# bridge.py
from fastapi import FastAPI, HTTPException
from aioafero import v1
import asyncio

app = FastAPI()
bridge = None   # v1.AferoBridgeV1(...)
lock = asyncio.Lock()

@app.post("/login")
async def login(body: dict):
    global bridge
    async with lock:
        bridge = v1.AferoBridgeV1(body["username"], body["password"], polling_interval=30)
        await bridge.initialize()
    return {"ok": True}

@app.get("/devices")
async def devices():
    # return minimal normalized list with id, type, name, supported features
    return [{"id": d.id, "type": d.type, "name": d.name} for d in bridge.devices.get_devices()]

@app.get("/state/{device_id}")
async def state(device_id: str):
    dev = bridge.devices.get_device(device_id)
    return dev.states

@app.post("/command/{device_id}")
async def command(device_id: str, body: dict):
    # body = {"cmd": "...", "args": {...}}
    # route to correct controller based on device type
    # e.g. lights.turn_on/turn_off/set_brightness/set_color_temperature/set_rgb, etc.
    # fans.turn_on/turn_off/set_speed, locks.lock/unlock, valves.turn_on/turn_off...
    return {"ok": True}