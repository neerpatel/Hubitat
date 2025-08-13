# bridge.py (replace the /devices route)
from fastapi import FastAPI, HTTPException
from aioafero import v1
import asyncio

app = FastAPI()
bridge = None
lock = asyncio.Lock()

# Track previously seen device IDs for discovery
known_ids = set()

def _index_all_devices():
    """Return a list of normalized device dicts from all controllers."""
    if bridge is None:
        return []
    ctl_names = [
        "devices", "lights", "switches", "fans", "locks",
        "thermostats", "valves", "portable_acs",
        "security_systems", "security_systems_sensors",
    ]
    seen = set()
    out = []
    for name in ctl_names:
        ctrl = getattr(bridge, name, None)
        if not ctrl:
            continue
        for dev in _models_from_controller(ctrl).values():
            did = getattr(dev, "id", None) or getattr(dev, "_id", None)
            if not did or did in seen:
                continue
            seen.add(did)
            dname = getattr(dev, "name", None) or getattr(dev, "label", f"HubSpace {did}")
            dtype = getattr(dev, "type", None) or getattr(dev, "category", "device")
            out.append({"id": did, "type": str(dtype), "name": str(dname)})
    return out

def _models_from_controller(ctrl):
    # Most controllers in aioafero expose a .models dict {id -> model}
    # Fall back to empty dict if attribute not present
    return getattr(ctrl, "models", {}) or {}

@app.post("/login")
async def login(body: dict):
    """
    body = {"username": "...", "password": "...", "polling_interval": 30}
    """
    global bridge
    async with lock:
        username = body["username"]
        password = body["password"]
        poll = int(body.get("polling_interval", 30))
        bridge = v1.AferoBridgeV1(username, password, polling_interval=poll, hide_secrets=False)
        await bridge.initialize()  # populates controllers/models
        # Prime the known_ids set on login
        global known_ids
        known_ids = {d["id"] for d in _index_all_devices()}
    return {"ok": True}

@app.get("/devices")
async def devices():
    if bridge is None:
        raise HTTPException(status_code=400, detail="Not logged in")
    return _index_all_devices()

@app.post("/discover")
async def discover():
    """Force a refresh of controllers/models and return only newly added devices since last scan."""
    if bridge is None:
        raise HTTPException(status_code=400, detail="Not logged in")
    async with lock:
        # Re-run initialize to repopulate controller models (safe if called again)
        await bridge.initialize()
        current = _index_all_devices()
        current_ids = {d["id"] for d in current}
        global known_ids
        new_ids = [d for d in current if d["id"] not in known_ids]
        # Update the baseline for next time
        known_ids = current_ids
        return {"added_count": len(new_ids), "new": new_ids, "all_count": len(current)}

@app.get("/discovery/status")
async def discovery_status():
    if bridge is None:
        raise HTTPException(status_code=400, detail="Not logged in")
    current = _index_all_devices()
    current_ids = {d["id"] for d in current}
    missing = [kid for kid in known_ids if kid not in current_ids]
    return {"known_count": len(known_ids), "current_count": len(current), "missing_known": list(missing)}

@app.get("/state/{device_id}")
async def state(device_id: str):
    if bridge is None:
        raise HTTPException(status_code=400, detail="Not logged in")
    # Try each controller's get_device until we find it
    for ctrl_name in ["lights","switches","fans","locks","thermostats","valves",
                      "portable_acs","security_systems","security_systems_sensors","devices"]:
        ctrl = getattr(bridge, ctrl_name, None)
        if not ctrl or not hasattr(ctrl, "get_device"):
            continue
        dev = ctrl.get_device(device_id)
        if dev:
            # Many models expose a .states or .state property; handle both
            states = getattr(dev, "states", None) or getattr(dev, "state", None) or {}
            return states
    raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

@app.post("/command/{device_id}")
async def command(device_id: str, body: dict):
    """
    body = {"cmd": "turn_on", "args": {...}}
    """
    if bridge is None:
        raise HTTPException(status_code=400, detail="Not logged in")
    cmd = body.get("cmd")
    args = body.get("args", {}) or {}
    # Route command by trying controllers that define the method name
    for ctrl_name in ["lights","switches","fans","locks","thermostats","valves","portable_acs","security_systems"]:
        ctrl = getattr(bridge, ctrl_name, None)
        if not ctrl:
            continue
        fn = getattr(ctrl, cmd, None)
        if callable(fn):
            # aioafero controller methods are async
            res = await fn(device_id, **args) if asyncio.iscoroutinefunction(fn) else fn(device_id, **args)
            return {"ok": True, "result": res}
    raise HTTPException(status_code=400, detail=f"Unsupported command '{cmd}' for device {device_id}")