# Switch Controller

The `switch` module provides the `SwitchController` class, which is responsible for managing Afero switch devices.

## `turn_on(device_id, instance=None)`

This method turns on a specific switch or a specific instance of a switch.

```mermaid
sequenceDiagram
    participant client as Client
    participant controller as SwitchController.turn_on()
    participant set_state as SwitchController.set_state()
    participant update as BaseResourcesController.update()
    participant AferoAPI as Afero API

    client->>controller: calls turn_on with device_id
    controller->>set_state: calls set_state with on=True
    set_state->>update: calls update with SwitchPut object
    update->>AferoAPI: PUT /v1/accounts/{account_id}/metadevices/{device_id}/state
    AferoAPI-->>update: returns success or failure
    update-->>set_state: 
    set_state-->>controller: 
    controller-->>client: 
```
