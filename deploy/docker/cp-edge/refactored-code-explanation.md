# Refactored sync-routes.py Logic Documentation

## Overview
The `sync-routes.py` script has been refactored from a monolithic script into a modular, object-oriented package (`sync_routes_lib`) compliant with Python 3.12+. It isolates responsibilities into distinct classes while strictly preserving the original business logic defined in `unrefactored-code-logic.md`.

## Architectural Changes

### 1. Modular Package Structure (`sync_routes_lib`)
The logic is now distributed across specialized modules, reducing the entry point `sync-routes.py` to a thin bootstrapper.

*   **`models.py`**: Defines data structures (`RouteSpec`).
*   **`service_spec_builder.py`**: Handles complex logic for parsing endpoints and building route specifications.
*   **`nginx.py`**: Encapsulates all Nginx file operations (reading templates, writing configs, managing custom domains).
*   **`synchronizer.py`**: Orchestrates the sync flow (the "Main Script Flow").
*   **`kube_client.py`** & **`cp_api.py`**: Abstract external system interactions.
*   **`config.py`**: Centralized configuration constants and environment variables.
*   **`logger.py`**: Standardized logging logic.

### 2. Data Model (`RouteSpec`)
Replaced unstructured dictionaries with a stricter `RouteSpec` dataclass (in `models.py`).
*   **Benefit**: Provides type safety and clear schema for route data passed between `ServiceSpecBuilder`, `RouteSynchronizer`, and `NginxManager`.
*   **Modern Types**: Uses Python 3.10+ union syntax (e.g., `str | None`).

## Python 3.12+ Modernization Changes
A detailed comparison of modernization improvements:

1.  **F-Strings Adoption (PEP 498)**
    *   **Original**: Used verbose `.format()` calls (e.g., `'Edge: {}'.format(ip)`) and C-style `%` formatting (e.g., `'nginx -c %s' % config`) throughout `sync-routes.py`, `kube_client.py`, and `nginx.py`.
    *   **Refactored**: Converted to f-strings (e.g., `f'Edge: {ip}'`). This is faster and more readable.
    *   **Regex**: Adopted raw f-strings (`fr'...'`) in `service_spec_builder.py` for regex patterns that include variables (e.g., `CP_CAP_CUSTOM_ENDPOINT_PREFIX`), avoiding the manual string concatenation seen in the original code (`r'{}(\d+).*'.format(...)`).

2.  **Modern Type Unions (PEP 604)**
    *   **Original**: Imported `typing.Optional` for nullable fields in `models.py` (e.g., `custom_domain: Optional[str]`).
    *   **Refactored**: Uses the cleaner `|` operator (e.g., `custom_domain: str | None`), which is the standard syntax since Python 3.10 and preferred in 3.12.
    *   **Impact**: Removed the import of `Optional` from `typing`, simplifying imports.

3.  **Indentation Standardization**
    *   **Original**: Inconsistent mixing of 4-space and 12-space indentation (especially in `sync-routes.py`), which is error-prone.
    *   **Refactored**: Strictly enforced 4-space indentation (PEP 8 standard), eliminating potential `IndentationError` risks in strict Python 3 environments.

4.  **Security & Deprecation Compliance**
    *   **DateTime**: Verified usage of `datetime.fromtimestamp(..., timezone.utc)` in `logger.py`, ensuring compliance with Python 3.12's deprecation of `datetime.utcnow()`.
    *   **Escape Sequences**: Maintained and enforced usage of raw strings (`r'...'`) for regex backslashes to prevent Python 3.12 syntax warnings about invalid escape sequences.

## Logic Preservation
The refactored code maintains exact parity with the original logic:
*   **`ServiceSpecBuilder.get_service_list`** mirrors the original `get_service_list` flow: selecting runs, parsing system/custom endpoints, and resolving edge locations.
*   **`RouteSynchronizer.sync`** implements the same 20-step process: resolving expected vs. actual routes, calculating diffs, handling shared users/groups, and managing DNS threading.
*   **Configuration**: All constants (`CP_EDGE_*`, `API_TOKEN`, etc.) remain in `config.py`, ensuring environment variable compatibility.
