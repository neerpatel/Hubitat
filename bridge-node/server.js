const express = require("express");
const fetch = require("node-fetch");
const { load: loadHtml } = require("cheerio");
const qs = require("qs");
const { v4: uuidv4 } = require("uuid");
const { randomBytes, createHash } = require("crypto");
const fs = require("fs");
const path = require("path");
const logger = require("./winston");

// Get package version
const packageJson = require("./package.json");
const appVersion = packageJson.version;

// Configuration constants
const CONFIG = {
  PORT: process.env.PORT || 3000,
  AUTH_HOST: "accounts.hubspaceconnect.com",
  AUTH_REALM: "thd",
  CLIENT_ID: "hubspace_android",
  REDIRECT_URI: "hubspace-app://loginredirect",
  USER_AGENT: "Dart/3.1 (dart:io)",
  API_HOST: "api2.afero.net",
  DATA_HOST: "semantics2.afero.net",
  SESSION_CLEANUP_INTERVAL: 60000, // 1 minute
  TOKEN_REFRESH_BUFFER: 5000, // 5 seconds
};

// In-memory session store: sessionId -> { refresh_token, access_token, expiration, accountId, lastAccess }
const sessions = new Map();

// Initialize Express app
const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ===== UTILITY FUNCTIONS =====

/**
 * Base64 URL encode a buffer
 */
function base64UrlEncode(buf) {
  return Buffer.from(buf)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

/**
 * Generate PKCE verifier and challenge
 */
function genPkce() {
  const verifier = base64UrlEncode(randomBytes(40)).replace(
    /[^a-zA-Z0-9_-]/g,
    ""
  );
  const challenge = base64UrlEncode(
    createHash("sha256").update(verifier).digest()
  );
  return { verifier, challenge };
}

/**
 * HTTP client wrapper
 */
async function http(
  method,
  url,
  { headers = {}, body, redirect = "follow" } = {}
) {
  const resp = await fetch(url, { method, headers, body, redirect });
  return resp;
}

/**
 * Parse cookies from response headers
 */
function parseSetCookie(resp) {
  const cookies = [];
  const setCookie = resp.headers.raw()["set-cookie"] || [];
  for (const c of setCookie) {
    const nv = c.split(";")[0];
    if (nv) cookies.push(nv);
  }
  return cookies.join("; ");
}

/**
 * Build auth URL
 */
function getAuthUrl(path) {
  const endpoint = path.startsWith("/") ? path.slice(1) : path;
  return `https://${CONFIG.AUTH_HOST}/auth/realms/${CONFIG.AUTH_REALM}/${endpoint}`;
}

/**
 * Build API URL
 */
function getApiUrl(endpoint) {
  const p = endpoint.startsWith("/") ? endpoint.slice(1) : endpoint;
  return `https://${CONFIG.API_HOST}/${p}`;
}

/**
 * Clean up expired sessions
 */
function cleanupSessions() {
  const now = Date.now();
  const oneHour = 60 * 60 * 1000;

  for (const [sessionId, session] of sessions.entries()) {
    if (!session.lastAccess || now - session.lastAccess > oneHour) {
      sessions.delete(sessionId);
      logger.info(`[cleanup] Removed expired session: ${sessionId}`);
    }
  }
}

// ===== REQUEST LOGGING MIDDLEWARE =====

app.use((req, _res, next) => {
  try {
    const safeBody =
      req.body && typeof req.body === "object"
        ? Object.keys(req.body).reduce((acc, k) => {
            acc[k] = k.toLowerCase().includes("password") ? "***" : req.body[k];
            return acc;
          }, {})
        : undefined;

    // Log only safe request properties to avoid circular references
    logger.info(
      JSON.stringify({
        method: req.method,
        url: req.url,
        ip: req.ip,
        userAgent: req.get("User-Agent"),
        body: safeBody,
        timestamp: new Date().toISOString(),
      })
    );
  } catch (_) {
    // ignore logging errors
    logger.error("Logging error:", _);
  }
  next();
});

// ===== AUTHENTICATION FUNCTIONS =====

/**
 * Perform complete OAuth login flow
 */
async function performLogin(username, password) {
  const { verifier, challenge } = genPkce();

  // Step 1: GET login page
  const authParams = {
    response_type: "code",
    client_id: CONFIG.CLIENT_ID,
    redirect_uri: CONFIG.REDIRECT_URI,
    scope: "openid offline_access",
    code_challenge: challenge,
    code_challenge_method: "S256",
  };

  const url =
    getAuthUrl("protocol/openid-connect/auth") + `?${qs.stringify(authParams)}`;
  const getResp = await http("GET", url, {
    headers: {
      "User-Agent": CONFIG.USER_AGENT,
      Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    },
    redirect: "manual",
  });

  const cookies = parseSetCookie(getResp);
  const html = await getResp.text();
  const $ = loadHtml(html);
  const form = $("form#kc-form-login");

  if (!form || form.length === 0) {
    throw new Error("Login form not found");
  }

  let action = form.attr("action") || "";
  action = action.replace(/&amp;/g, "&");

  const session_code =
    action.match(/session_code=([^&]+)/)?.[1] ||
    $('input[name="session_code"]').attr("value");
  const execution =
    action.match(/execution=([^&]+)/)?.[1] ||
    $('input[name="execution"]').attr("value");
  const tab_id =
    action.match(/tab_id=([^&]+)/)?.[1] ||
    $('input[name="tab_id"]').attr("value");

  if (!session_code || !execution || !tab_id) {
    throw new Error("Missing session parameters");
  }

  // Step 2: POST credentials
  const loginUrl = getAuthUrl("login-actions/authenticate");
  const loginQs = {
    session_code,
    execution,
    client_id: CONFIG.CLIENT_ID,
    tab_id,
  };
  const loginBody = qs.stringify({ username, password, credentialId: "" });

  const postResp = await http("POST", `${loginUrl}?${qs.stringify(loginQs)}`, {
    headers: {
      "User-Agent": CONFIG.USER_AGENT,
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
    client_id: CONFIG.CLIENT_ID,
    redirect_uri: CONFIG.REDIRECT_URI,
    code,
    code_verifier: verifier,
  });

  const tokenResp = await http("POST", tokenUrl, {
    headers: {
      "User-Agent": CONFIG.USER_AGENT,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
      Host: CONFIG.AUTH_HOST,
    },
    body: tokenBody,
  });

  const tokenJson = await tokenResp.json();
  if (!tokenResp.ok) {
    throw new Error(
      `Token exchange failed: ${tokenResp.status} ${JSON.stringify(tokenJson)}`
    );
  }

  const { access_token, refresh_token, expires_in } = tokenJson;
  const expiration = Date.now() + (expires_in - 5) * 1000;

  // Step 4: Get account ID
  const meResp = await http("GET", getApiUrl("/v1/users/me"), {
    headers: {
      Authorization: `Bearer ${access_token}`,
      Host: CONFIG.API_HOST,
    },
  });

  const meJson = await meResp.json();
  const accountId = meJson?.accountAccess?.[0]?.account?.accountId;

  if (!accountId) {
    throw new Error("Unable to resolve accountId");
  }

  return {
    access_token,
    refresh_token,
    expiration,
    accountId,
    lastAccess: Date.now(),
  };
}

/**
 * Refresh access token if needed
 */
async function refreshIfNeeded(sess) {
  if (!sess || !sess.refresh_token) {
    throw new Error("Invalid session");
  }

  // Update last access time
  sess.lastAccess = Date.now();

  if (Date.now() < sess.expiration - CONFIG.TOKEN_REFRESH_BUFFER) {
    return sess;
  }

  const tokenUrl = getAuthUrl("protocol/openid-connect/token");
  const body = qs.stringify({
    grant_type: "refresh_token",
    client_id: CONFIG.CLIENT_ID,
    refresh_token: sess.refresh_token,
    scope: "openid email offline_access profile",
  });

  const resp = await http("POST", tokenUrl, {
    headers: {
      "User-Agent": CONFIG.USER_AGENT,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
      Host: CONFIG.AUTH_HOST,
    },
    body,
  });

  const json = await resp.json();
  if (!resp.ok) {
    throw new Error(`Refresh failed: ${resp.status} ${JSON.stringify(json)}`);
  }

  sess.access_token = json.access_token;
  sess.refresh_token = json.refresh_token || sess.refresh_token;
  sess.expiration = Date.now() + (json.expires_in - 5) * 1000;

  return sess;
}

// ===== API ENDPOINTS =====

/**
 * POST /login - Authenticate with HubSpace credentials
 */
app.post("/login", async (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return res.status(400).json({ error: "username and password required" });
  }

  try {
    logger.info(`[auth] Starting login for ${username}`);
    const tok = await performLogin(username, password);
    const sessionId = uuidv4();
    sessions.set(sessionId, tok);
    logger.info(`[auth] Login success for ${username}, session=${sessionId}`);
    res.json({ sessionId, accountId: tok.accountId });
  } catch (e) {
    console.error("[auth] Login failed", e);
    res.status(500).json({ error: e.message });
  }
});

/**
 * GET /devices - List all devices for authenticated session
 */
app.get("/devices", async (req, res) => {
  try {
    const sessionId = req.query.session;
    const sess = sessions.get(sessionId);

    if (!sess) {
      return res.status(401).json({ error: "invalid session" });
    }

    await refreshIfNeeded(sess);

    const url = getApiUrl(
      `/v1/accounts/${sess.accountId}/metadevices?expansions=state`
    );
    const resp = await http("GET", url, {
      headers: {
        Authorization: `Bearer ${sess.access_token}`,
        Host: CONFIG.DATA_HOST,
      },
    });

    const data = await resp.json();
    logger.info(
      `[devices] account=${sess.accountId} status=${resp.status} count=${
        Array.isArray(data) ? data.length : "n/a"
      }`
    );

    const minimal = (Array.isArray(data) ? data : []).map((d) => ({
      id: d.id || d.deviceId || d.metadeviceId || d.device_id,
      deviceId: d.deviceId || d.device_id,
      typeId: d.typeId || d.type,
      device_class:
        d.device_class ||
        d?.description?.device?.deviceClass ||
        d?.description?.deviceClass,
      friendlyName:
        d.friendlyName ||
        d.friendly_name ||
        d?.description?.device?.friendlyName ||
        d.default_name,
      children: d.children || [],
      states: d.state || d.states || undefined,
    }));

    res.json(minimal);
  } catch (e) {
    console.error("[devices] error", e);
    res.status(500).json({ error: e.message });
  }
});

/**
 * GET /state/:id - Get device state
 */
app.get("/state/:id", async (req, res) => {
  try {
    const sessionId = req.query.session;
    const deviceId = req.params.id;
    const sess = sessions.get(sessionId);

    if (!sess) {
      return res.status(401).json({ error: "invalid session" });
    }

    await refreshIfNeeded(sess);

    const url = getApiUrl(
      `/v1/accounts/${sess.accountId}/metadevices/${deviceId}/state`
    );
    const resp = await http("GET", url, {
      headers: {
        Authorization: `Bearer ${sess.access_token}`,
        Host: CONFIG.DATA_HOST,
      },
    });

    logger.info(`[state] device=${deviceId} status=${resp.status}`);
    const json = await resp.json();
    res.json(json);
  } catch (e) {
    logger.error("[state] error", e);
    res.status(500).json({ error: e.message });
  }
});

/**
 * POST /command/:id - Send command to device
 */
app.post("/command/:id", async (req, res) => {
  try {
    const sessionId = req.query.session;
    const deviceId = req.params.id;
    const { values } = req.body || {};

    if (!Array.isArray(values)) {
      return res.status(400).json({ error: "values array required" });
    }

    const sess = sessions.get(sessionId);
    if (!sess) {
      return res.status(401).json({ error: "invalid session" });
    }

    await refreshIfNeeded(sess);

    const url = getApiUrl(
      `/v1/accounts/${sess.accountId}/metadevices/${deviceId}/state`
    );
    const payload = { metadeviceId: String(deviceId), values };

    const resp = await http("PUT", url, {
      headers: {
        Authorization: `Bearer ${sess.access_token}`,
        Host: CONFIG.DATA_HOST,
        "Content-Type": "application/json; charset=utf-8",
      },
      body: JSON.stringify(payload),
    });

    logger.info(
      `[command] device=${deviceId} status=${resp.status} body=${JSON.stringify(
        values
      )}`
    );

    const text = await resp.text();
    if (!resp.ok) {
      return res.status(resp.status).json({ error: text });
    }

    res.json({ ok: true });
  } catch (e) {
    console.error("[command] error", e);
    res.status(500).json({ error: e.message });
  }
});

/**
 * GET /health - Health check endpoint
 */
app.get("/health", (req, res) => {
  try {
    res.json({
      status: "ok",
      uptime: process.uptime(),
      sessions: sessions.size,
      version: appVersion,
    });
  } catch (e) {
    res.status(500).json({ status: "error", error: e.message });
  }
});

// ===== SERVER STARTUP =====

// Set up session cleanup interval
setInterval(cleanupSessions, CONFIG.SESSION_CLEANUP_INTERVAL);

// Start the server
app.listen(CONFIG.PORT, () => {
  logger.info(`HubSpace bridge v${appVersion} listening on :${CONFIG.PORT}`);
  logger.info(`Session cleanup interval: ${CONFIG.SESSION_CLEANUP_INTERVAL}ms`);
});

process.on("SIGINT", () => {
  // was SIGTERM
  logger.info("SIGINT signal received.");
});
