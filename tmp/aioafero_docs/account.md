# Account Information Endpoints

This document outlines the account information endpoints used by the `aioafero` library.

## Get Account ID

*   **Method:** `GET`
*   **URL:** `https://api2.afero.net/v1/users/me`
*   **Description:** This endpoint retrieves the user's account ID, which is required for other API calls.

## Get All Device Data

*   **Method:** `GET`
*   **URL:** `https://api2.afero.net/v1/accounts/{account_id}/metadevices`
*   **Description:** This endpoint retrieves all data for all devices associated with the account. The `account_id` should be replaced with the user's account ID.