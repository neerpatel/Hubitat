# Afero API Data Models

This document details the key data models used by the `aioafero` library to represent devices and their states when interacting with the Afero API. Understanding these models is crucial for correctly parsing API responses and constructing API requests.

## 1. `AferoState`

Represents the state of a single function or attribute of a device. This is the fundamental unit of data for device states.

```python
@dataclass
class AferoState:
    functionClass: str
    value: Any
    lastUpdateTime: int | None = None
    functionInstance: str | None = None
```

**Explanation:**
*   `functionClass`: A string identifying the type of function (e.g., "power", "brightness", "fan-speed", "color-rgb").
*   `value`: The current value of the function. This can be a boolean, integer, string, or a nested dictionary/list depending on the `functionClass`.
*   `lastUpdateTime`: (Optional) Timestamp (in epoch milliseconds) when the state was last updated.
*   `functionInstance`: (Optional) An additional identifier for specific instances of a function, especially when a device has multiple similar functions (e.g., multiple light channels, or specific presets).

## 2. `AferoDevice`

Represents a complete device as received from the Afero API. It aggregates device information, capabilities, and its current states.

```python
@dataclass
class AferoDevice:
    id: str
    device_id: str
    model: str
    device_class: str
    default_name: str
    default_image: str
    friendly_name: str
    functions: list[dict] = field(default=list)
    states: list[AferoState] = field(default=list)
    children: list[str] = field(default=list)
    manufacturerName: str | None = field(default=None)
    split_identifier: str | None = field(default=None, repr=False)
```

**Explanation:**
*   `id`: The unique identifier for this specific device instance.
*   `device_id`: Often the parent device ID, especially for devices that are logically split into multiple entities.
*   `model`, `device_class`, `default_name`, `default_image`, `friendly_name`, `manufacturerName`: Descriptive metadata about the device.
*   `functions`: A list of dictionaries describing the device's capabilities. Each dictionary defines a `functionClass`, its `type` (e.g., "numeric", "category"), and `values` (e.g., ranges for numeric functions, or lists of names for categorical functions). This is crucial for understanding what actions can be performed on the device and what values are supported.
*   `states`: A list of `AferoState` objects, representing the current values of all functions on the device.
*   `children`: A list of IDs of any child devices associated with this device.
*   `split_identifier`: An internal identifier used for managing split devices.

## 3. `ResourceTypes`

An enumeration defining the various types of resources (devices) supported by the Afero API. These values are often found in the `device_class` field of `AferoDevice`.

```python
class ResourceTypes(Enum):
    DEVICE = "metadevice.device"
    HOME = "metadata.home"
    ROOM = "metadata.room"
    EXHAUST_FAN = "exhaust-fan"
    FAN = "fan"
    # ... (and many more)
    UNKNOWN = "unknown"
```

**Explanation:**
*   Provides a mapping between human-readable device types and their string representations used in the API.

## 4. `DeviceInformation`

A dataclass containing common descriptive information about a device, often nested within more specific device models.

```python
@dataclass
class DeviceInformation:
    device_class: str | None = None
    default_image: str | None = None
    default_name: str | None = None
    manufacturer: str | None = None
    model: str | None = None
    name: str | None = None
    parent_id: str | None = None
    wifi_mac: str | None = None
    ble_mac: str | None = None
```

**Explanation:**
*   Contains general metadata about a device, such as its class, model, manufacturer, and network identifiers.

## 5. Feature Dataclasses (e.g., `OnFeature`, `ColorFeature`, `DimmingFeature`)

These dataclasses represent specific capabilities or attributes of a device. They are used to structure the data both when reading device states and when preparing updates to send to the API. A key aspect of these features is the `api_value` property.

**General Structure:**

```python
@dataclass
class SomeFeature:
    # ... feature-specific attributes ...

    @property
    def api_value(self):
        """Value to send to Afero API."""
        # This method transforms the feature's attributes into the
        # 'functionClass', 'functionInstance', and 'value' format
        # expected by the Afero API for state updates.
        return { ... }
```

**Examples:**

*   **`OnFeature`**: Represents the on/off state of a device.
    ```python
    @dataclass
    class OnFeature:
        on: bool
        func_class: str | None = field(default="power")
        func_instance: str | None = field(default=None)

        @property
        def api_value(self):
            return {
                "value": "on" if self.on else "off",
                "functionClass": self.func_class,
            }
    ```

*   **`DimmingFeature`**: Represents the brightness level.
    ```python
    @dataclass
    class DimmingFeature:
        brightness: int
        supported: list[int]

        @property
        def api_value(self):
            return self.brightness
    ```

*   **`ColorFeature`**: Represents RGB color values.
    ```python
    @dataclass
    class ColorFeature:
        red: int
        green: int
        blue: int

        @property
        def api_value(self):
            return {
                "value": {"color-rgb": {"r": self.red, "g": self.green, "b": self.blue}}
            }
    ```

**Importance for Reverse Engineering:**

When constructing API requests to update device states, you will need to create payloads that conform to the `AferoState` structure. The `api_value` properties within these `Feature` dataclasses provide the exact mapping from a high-level attribute (like `brightness=50`) to the `functionClass`, `functionInstance`, and `value` format required by the Afero API. By examining these `api_value` implementations, you can accurately replicate the outgoing data structure for any device command.
