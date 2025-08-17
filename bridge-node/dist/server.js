import express from "express";
import fetch from "node-fetch";
import * as cheerio from "cheerio";
import qs from "qs";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
// Config
const PORT = process.env.PORT || 3000;
const AUTH_HOST = "accounts.hubspaceconnect.com";
const AUTH_REALM = "thd";
const CLIENT_ID = "hubspace_android";
const REDIRECT_URI = "hubspace-app://loginredirect";
const USER_AGENT = "Dart/3.1 (dart:io)";
const API_HOST = "api2.afero.net";
const DATA_HOST = "semantics2.afero.net";
const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
// In-memory session store: sessionId -> SessionData
const sessions = new Map();
function base64UrlEncode(buffer) {
    return buffer
        .toString("base64")
        .replace(/=/g, "")
        .replace(/\+/g, "-")
        .replace(/\//g, "_");
}
function genPkce() {
    const verifier = base64UrlEncode(crypto.randomBytes(40)).replace(/[^a-zA-Z0-9_-]/g, "");
    const challenge = base64UrlEncode(crypto.createHash("sha256").update(verifier).digest());
    return { verifier, challenge };
}
async function http(method, url, options = {}) {
    const { headers = {}, body, redirect = "follow" } = options;
    const fetchOptions = { method, headers, redirect };
    if (body !== undefined) {
        fetchOptions.body = body;
    }
    const resp = await fetch(url, fetchOptions);
    return resp;
}
function parseSetCookie(resp) {
    const cookies = [];
    const setCookie = resp.headers.raw()["set-cookie"] || [];
    for (const c of setCookie) {
        const nv = c.split(";")[0];
        if (nv)
            cookies.push(nv);
    }
    return cookies.join("; ");
}
function getAuthUrl(path) {
    const endpoint = path.startsWith("/") ? path.slice(1) : path;
    return `https://${AUTH_HOST}/auth/realms/${AUTH_REALM}/${endpoint}`;
}
function getApiUrl(endpoint) {
    const p = endpoint.startsWith("/") ? endpoint.slice(1) : endpoint;
    return `https://${API_HOST}/${p}`;
}
async function performLogin(username, password) {
    const { verifier, challenge } = genPkce();
    // Step 1: GET login page
    const authParams = {
        response_type: "code",
        client_id: CLIENT_ID,
        redirect_uri: REDIRECT_URI,
        scope: "openid offline_access",
        code_challenge: challenge,
        code_challenge_method: "S256",
    };
    const url = getAuthUrl("protocol/openid-connect/auth") + `?${qs.stringify(authParams)}`;
    const getResp = await http("GET", url, {
        headers: {
            "User-Agent": USER_AGENT,
            Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        },
        redirect: "manual",
    });
    const cookies = parseSetCookie(getResp);
    const html = await getResp.text();
    const $ = cheerio.load(html);
    const form = $("form#kc-form-login");
    if (!form || form.length === 0) {
        throw new Error("Login form not found");
    }
    let action = form.attr("action") || "";
    action = action.replace(/&amp;/g, "&");
    const session_code = action.match(/session_code=([^&]+)/)?.[1] ||
        $('input[name="session_code"]').attr("value");
    const execution = action.match(/execution=([^&]+)/)?.[1] ||
        $('input[name="execution"]').attr("value");
    const tab_id = action.match(/tab_id=([^&]+)/)?.[1] ||
        $('input[name="tab_id"]').attr("value");
    if (!session_code || !execution || !tab_id) {
        throw new Error("Missing session parameters");
    }
    // Step 2: POST credentials
    const loginUrl = getAuthUrl("login-actions/authenticate");
    const loginQs = { session_code, execution, client_id: CLIENT_ID, tab_id };
    const loginBody = qs.stringify({ username, password, credentialId: "" });
    const postResp = await http("POST", `${loginUrl}?${qs.stringify(loginQs)}`, {
        headers: {
            "User-Agent": USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded",
            Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            Cookie: cookies,
            Referer: getAuthUrl("protocol/openid-connect/auth"),
        },
        body: loginBody,
        redirect: "manual",
    });
    const location = postResp.headers.get("location") || "";
    const code = location.match(/[?&]code=([^&]+)/)?.[1];
    if (!code) {
        throw new Error(`No auth code in response; status ${postResp.status}`);
    }
    // Step 3: Exchange code for tokens
    const tokenUrl = getAuthUrl("protocol/openid-connect/token");
    const tokenBody = qs.stringify({
        grant_type: "authorization_code",
        client_id: CLIENT_ID,
        redirect_uri: REDIRECT_URI,
        code,
        code_verifier: verifier,
    });
    const tokenResp = await http("POST", tokenUrl, {
        headers: {
            "User-Agent": USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded",
            Accept: "application/json",
            Host: AUTH_HOST,
        },
        body: tokenBody,
    });
    const tokenJson = (await tokenResp.json());
    if (!tokenResp.ok) {
        throw new Error(`Token exchange failed: ${tokenResp.status} ${JSON.stringify(tokenJson)}`);
    }
    const { access_token, refresh_token, expires_in } = tokenJson;
    const expiration = Date.now() + (expires_in - 5) * 1000;
    // Step 4: Get Account ID
    const meResp = await http("GET", getApiUrl("/v1/users/me"), {
        headers: {
            Authorization: `Bearer ${access_token}`,
            Host: API_HOST,
        },
    });
    const meJson = (await meResp.json());
    const accountId = meJson?.accountAccess?.[0]?.account?.accountId;
    if (!accountId) {
        throw new Error("Unable to resolve accountId");
    }
    return { access_token, refresh_token, expiration, accountId };
}
async function refreshIfNeeded(sess) {
    if (!sess || !sess.refresh_token) {
        throw new Error("Invalid session");
    }
    if (Date.now() < sess.expiration - 5000) {
        return sess;
    }
    const tokenUrl = getAuthUrl("protocol/openid-connect/token");
    const body = qs.stringify({
        grant_type: "refresh_token",
        client_id: CLIENT_ID,
        refresh_token: sess.refresh_token,
        scope: "openid email offline_access profile",
    });
    const resp = await http("POST", tokenUrl, {
        headers: {
            "User-Agent": USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded",
            Accept: "application/json",
            Host: AUTH_HOST,
        },
        body,
    });
    const json = (await resp.json());
    if (!resp.ok) {
        throw new Error(`Refresh failed: ${resp.status} ${JSON.stringify(json)}`);
    }
    sess.access_token = json.access_token;
    sess.refresh_token = json.refresh_token || sess.refresh_token;
    sess.expiration = Date.now() + (json.expires_in - 5) * 1000;
    return sess;
}
// POST /login { username, password }
app.post("/login", async (req, res) => {
    const { username, password } = req.body || {};
    if (!username || !password) {
        res.status(400).json({ error: "username and password required" });
        return;
    }
    try {
        const tok = await performLogin(username, password);
        const sessionId = uuidv4();
        sessions.set(sessionId, tok);
        res.json({ sessionId, accountId: tok.accountId });
    }
    catch (e) {
        const error = e instanceof Error ? e.message : "Unknown error";
        res.status(500).json({ error });
    }
});
// GET /devices?session=...
app.get("/devices", async (req, res) => {
    try {
        const sessionId = req.query.session;
        const sess = sessions.get(sessionId);
        if (!sess) {
            res.status(401).json({ error: "invalid session" });
            return;
        }
        await refreshIfNeeded(sess);
        const url = getApiUrl(`/v1/accounts/${sess.accountId}/metadevices?expansions=state`);
        const resp = await http("GET", url, {
            headers: {
                Authorization: `Bearer ${sess.access_token}`,
                Host: DATA_HOST,
            },
        });
        const data = (await resp.json());
        const devices = Array.isArray(data) ? data : [];
        const minimal = devices.map((d) => ({
            id: d.id || d.deviceId || d.metadeviceId || d.device_id,
            typeId: d.typeId || d.type,
            device_class: d.device_class ||
                d?.description?.device?.deviceClass ||
                d?.description?.deviceClass,
            friendlyName: d.friendlyName ||
                d.friendly_name ||
                d?.description?.device?.friendlyName ||
                d.default_name,
            states: d.state || d.states || undefined,
        }));
        res.json(minimal);
    }
    catch (e) {
        const error = e instanceof Error ? e.message : "Unknown error";
        res.status(500).json({ error });
    }
});
// GET /state/:id?session=...
app.get("/state/:id", async (req, res) => {
    try {
        const sessionId = req.query.session;
        const deviceId = req.params.id;
        const sess = sessions.get(sessionId);
        if (!sess) {
            res.status(401).json({ error: "invalid session" });
            return;
        }
        await refreshIfNeeded(sess);
        const url = getApiUrl(`/v1/accounts/${sess.accountId}/metadevices/${deviceId}/state`);
        const resp = await http("GET", url, {
            headers: {
                Authorization: `Bearer ${sess.access_token}`,
                Host: DATA_HOST,
            },
        });
        const json = await resp.json();
        res.json(json);
    }
    catch (e) {
        const error = e instanceof Error ? e.message : "Unknown error";
        res.status(500).json({ error });
    }
});
// POST /command/:id?session=... { values: [ { functionClass, functionInstance?, value } ] }
app.post("/command/:id", async (req, res) => {
    try {
        const sessionId = req.query.session;
        const deviceId = req.params.id;
        const { values } = req.body || {};
        if (!Array.isArray(values)) {
            res.status(400).json({ error: "values array required" });
            return;
        }
        const sess = sessions.get(sessionId);
        if (!sess) {
            res.status(401).json({ error: "invalid session" });
            return;
        }
        await refreshIfNeeded(sess);
        const url = getApiUrl(`/v1/accounts/${sess.accountId}/metadevices/${deviceId}/state`);
        const payload = { metadeviceId: String(deviceId), values };
        const resp = await http("PUT", url, {
            headers: {
                Authorization: `Bearer ${sess.access_token}`,
                Host: DATA_HOST,
                "Content-Type": "application/json; charset=utf-8",
            },
            body: JSON.stringify(payload),
        });
        const text = await resp.text();
        if (!resp.ok) {
            res.status(resp.status).json({ error: text });
            return;
        }
        res.json({ ok: true });
    }
    catch (e) {
        const error = e instanceof Error ? e.message : "Unknown error";
        res.status(500).json({ error });
    }
});
app.listen(PORT, () => {
    console.log(`HubSpace bridge listening on :${PORT}`);
});
//# sourceMappingURL=server.js.map