# Base Controller

The `base` module provides the `BaseResourcesController` class, which is the foundation for all device-specific controllers. It handles event subscription, device state updates, and communication with the Afero API.

## `update(device_id, obj_in)`

This method is responsible for updating the state of a device. It takes the `device_id` and a data object `obj_in` containing the new values.

```mermaid
sequenceDiagram
    participant client as Client
    participant controller as Controller.update()
    participant get_device as controller.get_device()
    participant dataclass_to_afero as dataclass_to_afero()
    participant update_dataclass as update_dataclass()
    participant update_afero_api as controller.update_afero_api()
    participant AferoAPI as Afero API

    client->>controller: calls update with device_id and new data
    controller->>get_device: get device object
    get_device-->>controller: returns device object
    controller->>dataclass_to_afero: convert data to Afero states
    dataclass_to_afero-->>controller: returns list of states
    controller->>update_dataclass: update local device state
    update_dataclass-->>controller: 
    controller->>update_afero_api: send states to Afero API
    update_afero_api->>AferoAPI: PUT /v1/accounts/{account_id}/metadevices/{device_id}/state
    AferoAPI-->>update_afero_api: returns success or failure
    alt Update Fails
        update_afero_api-->>controller: returns failure
        controller->>controller: restore device state from fallback
    else Update Succeeds
        update_afero_api-->>controller: returns success
    end
```
