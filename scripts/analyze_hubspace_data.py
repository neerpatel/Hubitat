#!/usr/bin/env python3
"""
Analyze collected HubSpace/Afero metadevice data and generate a Markdown reference.

Inputs (defaults):
  - research/05_metadevices_with_state.json
  - research/05b_metadevices_compact.txt (optional)

Output:
  - docs/hubspace_data_reference.md

Usage:
  python3 scripts/analyze_hubspace_data.py \
    --input research/05_metadevices_with_state.json \
    --compact research/05b_metadevices_compact.txt \
    --out docs/hubspace_data_reference.md
"""
from __future__ import annotations

import argparse
import json
import os
from collections import Counter, defaultdict
from typing import Any, Dict, List, Optional, Set, Tuple


def load_json(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_compact(path: str) -> List[Tuple[str, str, str]]:
    rows: List[Tuple[str, str, str]] = []
    if not path or not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = [p.strip() for p in line.split("|")]
            if len(parts) >= 3:
                rows.append((parts[0], parts[1], parts[2]))
    return rows


def type_name(v: Any) -> str:
    if isinstance(v, bool):
        return "boolean"
    if isinstance(v, int) or isinstance(v, float):
        return "number"
    if isinstance(v, str):
        return "string"
    if isinstance(v, dict):
        return "object"
    if isinstance(v, list):
        return "array"
    if v is None:
        return "null"
    return type(v).__name__


def safe_sample_val(v: Any) -> Any:
    # reduce size for objects
    if isinstance(v, dict):
        return {k: v[k] for k in list(v.keys())[:5]}
    if isinstance(v, list):
        return v[:5]
    return v


def analyze(data: List[dict]) -> Dict[str, Any]:
    # Filter metadevices
    devices = [
        d for d in data if d.get("typeId") == "metadevice.device"
    ]
    # Helpers to extract
    def dev_id(d):
        return d.get("deviceId") or d.get("id") or d.get("metadeviceId")

    def dev_class(d):
        return (
            (d.get("description") or {})
            .get("device", {})
            .get("deviceClass")
            or d.get("device_class")
            or d.get("typeId")
            or "unknown"
        )

    def dev_name(d):
        return (
            (d.get("description") or {})
            .get("device", {})
            .get("friendlyName")
            or d.get("friendlyName")
            or d.get("name")
            or ""
        )

    # Aggregate basics
    class_counts = Counter([dev_class(d) for d in devices])

    # function class stats per device class
    # { device_class: { functionClass: {device_count, state_count, instances:set, value_types:Counter, samples:set} } }
    func_stats: Dict[str, Dict[str, Any]] = defaultdict(lambda: defaultdict(lambda: {
        "device_count": 0,
        "state_count": 0,
        "instances": set(),
        "value_types": Counter(),
        "samples": set(),
    }))

    # also track per-device whether a functionClass was seen to increment device_count once
    # { device_class: { device_id: set(functionClass) } }
    seen_per_device: Dict[str, Dict[str, Set[str]]] = defaultdict(lambda: defaultdict(set))

    # diagnostics for malformed entries
    diagnostics = {
        "malformed_state_containers": 0,
        "malformed_state_entries": 0,
    }

    for d in devices:
        dc = dev_class(d)
        did = str(dev_id(d))
        states = d.get("state") or d.get("states") or []
        # Normalize states container
        if isinstance(states, dict):
            # sometimes payloads can show { values: [...] }
            if isinstance(states.get("values"), list):
                states = states.get("values", [])
            else:
                diagnostics["malformed_state_containers"] += 1
                continue
        if not isinstance(states, list):
            diagnostics["malformed_state_containers"] += 1
            continue
        for st in states:
            if not isinstance(st, dict):
                diagnostics["malformed_state_entries"] += 1
                continue
            fc = st.get("functionClass") or ""
            if not fc:
                continue
            fi = st.get("functionInstance")
            val = st.get("value")
            tname = type_name(val)
            bucket = func_stats[dc][fc]
            bucket["state_count"] += 1
            bucket["instances"].add(fi)
            bucket["value_types"][tname] += 1
            if len(bucket["samples"]) < 5:
                bucket["samples"].add(json.dumps(safe_sample_val(val), sort_keys=True))
            # device-count tally once per device per fc
            if fc not in seen_per_device[dc][did]:
                seen_per_device[dc][did].add(fc)
                bucket["device_count"] += 1

    # Convert sets to lists and sample strings back to JSON
    def normalize(stats):
        out = {}
        for fc, s in stats.items():
            inst = sorted([i for i in s["instances"] if i is not None])
            samples = []
            for sv in s["samples"]:
                try:
                    samples.append(json.loads(sv))
                except Exception:
                    samples.append(sv)
            out[fc] = {
                "device_count": s["device_count"],
                "state_count": s["state_count"],
                "instances": inst,
                "value_types": dict(s["value_types"]),
                "samples": samples,
            }
        return out

    func_stats_norm = {dc: normalize(stats) for dc, stats in func_stats.items()}

    # Cross-class function usage
    cross_fc = Counter()
    for dc, stats in func_stats_norm.items():
        for fc in stats.keys():
            cross_fc[fc] += 1

    devices_index = [
        {"id": str(dev_id(d)), "class": dev_class(d), "name": dev_name(d)}
        for d in devices
    ]

    return {
        "device_count": len(devices),
        "class_counts": dict(class_counts),
        "devices_index": devices_index,
        "function_stats": func_stats_norm,
        "function_usage_across_classes": dict(cross_fc),
        "diagnostics": diagnostics,
    }


HUBITAT_MAPPINGS = {
    "power": {"capability": "Switch", "attributes": ["switch"], "commands": ["on","off"]},
    "brightness": {"capability": "SwitchLevel", "attributes": ["level"], "commands": ["setLevel"]},
    "color-temperature": {"capability": "ColorTemperature", "attributes": ["colorTemperature"], "commands": ["setColorTemperature"]},
    "color-rgb": {"capability": "ColorControl", "attributes": ["color","hue","saturation"], "commands": ["setColor"]},
    "fan-speed": {"capability": "FanControl", "attributes": ["speed"], "commands": ["setSpeed"]},
    "fan-direction": {"capability": "FanControl", "attributes": ["direction"], "commands": ["setDirection"]},
    "lock": {"capability": "Lock", "attributes": ["lock"], "commands": ["lock","unlock"]},
    "mode": {"capability": "Thermostat", "attributes": ["thermostatMode"], "commands": ["setThermostatMode"]},
    "fan-mode": {"capability": "ThermostatFanMode", "attributes": ["thermostatFanMode"], "commands": ["setThermostatFanMode"]},
}


def write_markdown(out_path: str, summary: Dict[str, Any]) -> None:
    lines: List[str] = []
    add = lines.append

    add("# HubSpace Metadevices Data Reference")
    add("")
    add("This document summarizes your collected HubSpace/Afero metadevices payload, mapping device classes to their observed function classes, value types, and example values. It also suggests Hubitat capability mappings and PUT payload shapes.")
    add("")

    add("## Inventory")
    add(f"- Devices analyzed: {summary['device_count']}")
    add("- Classes observed:")
    for cls, cnt in sorted(summary["class_counts"].items(), key=lambda x: -x[1]):
        add(f"  - {cls}: {cnt}")
    add("")

    add("## Common Function Classes (across device classes)")
    for fc, occ in sorted(summary["function_usage_across_classes"].items(), key=lambda x: -x[1])[:25]:
        hint = HUBITAT_MAPPINGS.get(fc)
        hint_str = f" → Hubitat: {hint['capability']}" if hint else ""
        add(f"- `{fc}` in {occ} class(es){hint_str}")
    add("")

    add("## Per-Class Details")
    for cls in sorted(summary["function_stats"].keys()):
        add(f"### {cls}")
        stats = summary["function_stats"][cls]
        if not stats:
            add("(no function classes observed)")
            add("")
            continue
        for fc, s in sorted(stats.items(), key=lambda x: -x[1]["device_count"]):
            add(f"- `{fc}`: on {s['device_count']} device(s), {s['state_count']} state entr(y/ies)")
            vt = ", ".join(f"{k}:{v}" for k, v in sorted(s["value_types"].items(), key=lambda x: -x[1]))
            add(f"  - Value types: {vt}")
            inst = s.get("instances") or []
            if inst:
                add(f"  - Instances: {', '.join(str(i) for i in inst)}")
            samples = s.get("samples") or []
            if samples:
                try:
                    sample_pretty = json.dumps(samples[0], ensure_ascii=False)
                except Exception:
                    sample_pretty = str(samples[0])
                add(f"  - Sample: {sample_pretty}")
            if fc in HUBITAT_MAPPINGS:
                h = HUBITAT_MAPPINGS[fc]
                add(f"  - Hubitat: {h['capability']} ({', '.join(h['attributes'])})")
            add("")

    add("## PUT Payload Shape")
    add("All updates flow through PUT `/v1/accounts/{accountId}/metadevices/{deviceId}/state` with body:")
    add("```")
    add('{ "metadeviceId": "<deviceId>", "values": [ { "functionClass": "<class>", "functionInstance": null, "value": <scalar-or-object> } ] }')
    add("```")
    add("Notes:")
    add("- Timestamps are optional; HubSpace accepts server-side TS.")
    add("- For boolean-like toggles, some devices use strings `on|off` rather than true/false.")
    add("- Color RGB expects an object `{r,g,b}`; thermostat targets use distinct functionInstances.")
    add("")

    add("## Next Data Gaps to Explore")
    add("- If some classes above show only `unknown` or few function classes, collect additional samples with the latest script.")
    add("- For devices with object-valued states (e.g., `color-rgb`), capture a couple of values to confirm full shape.")
    add("- Consider fetching versions for all deviceIds to correlate firmware with capabilities.")

    diag = summary.get("diagnostics") or {}
    if diag:
        add("")
        add("### Diagnostics")
        add(f"- Malformed state containers: {diag.get('malformed_state_containers', 0)}")
        add(f"- Malformed state entries: {diag.get('malformed_state_entries', 0)}")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default="research/05_metadevices_with_state.json")
    ap.add_argument("--compact", default="research/05b_metadevices_compact.txt")
    ap.add_argument("--out", default="docs/hubspace_data_reference.md")
    args = ap.parse_args()

    if not os.path.exists(args.input):
        raise SystemExit(f"Input file not found: {args.input}")

    data = load_json(args.input)
    if not isinstance(data, list):
        raise SystemExit("Expected input JSON array of metadevices")

    # optional load/validate compact rows – not strictly needed for analysis
    _ = load_compact(args.compact)

    summary = analyze(data)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    write_markdown(args.out, summary)
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
