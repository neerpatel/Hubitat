# Device Module

The `device` module provides the core data structures for representing Afero devices and their states.

## `get_afero_device(afero_device)`

This function takes a dictionary representing an Afero device and converts it into an `AferoDevice` object.

```mermaid
sequenceDiagram
    participant client as Client
    participant get_afero_device as get_afero_device(afero_device)
    participant AferoDevice as AferoDevice(**dev_dict)
    participant AferoState as AferoState(...)

    client->>get_afero_device: calls with afero_device dictionary
    get_afero_device->>get_afero_device: processes description and device info
    loop for each state in afero_device
        get_afero_device->>AferoState: creates AferoState object
        AferoState-->>get_afero_device: returns AferoState object
    end
    get_afero_device->>AferoDevice: creates AferoDevice object
    AferoDevice-->>get_afero_device: returns AferoDevice object
    get_afero_device-->>client: returns AferoDevice object
```
