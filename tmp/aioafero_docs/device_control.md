# Device Control Endpoints

This document outlines the device control endpoints used by the `aioafero` library.

## Update Device State

*   **Method:** `PUT`
*   **URL:** `https://api2.afero.net/v1/accounts/{account_id}/metadevices/{device_id}/state`
*   **Description:** This endpoint is used to update the state of a specific device. The `account_id` and `device_id` should be replaced with the appropriate values. The request body should contain a JSON payload with the desired state changes.