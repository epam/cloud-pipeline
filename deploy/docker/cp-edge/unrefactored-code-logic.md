# sync-routes.py Logic Documentation

## Logic
`sync-routes.py` synchronizes Cloud Pipeline run/service endpoints into the Edge Nginx configuration. It:
*   Queries Kubernetes (via `pykube`) for pods that expose endpoints.
*   Calls Cloud Pipeline API to get run details (endpoints, owners, sharing, pretty URLs, instance info).
*   Generates nginx location config files from templates for each endpoint.
*   Optionally creates DNS records via API for custom domains.
*   Reloads nginx when configs changed and updates the run's `serviceUrl` back to the API.
*   Handles retries, logging, and threaded DNS creation.

## Key Constants & Env. Variables
*   **API, API_TOKEN:** Cloud Pipeline API URL and token (required).
*   **CP_KUBE_NAMESPACE:** Kubernetes namespace to inspect.
*   **CP_CAP_CUSTOM_TOOL_ENDPOINT_...:** Run-parameter prefixes for custom endpoints.
*   **CP_EDGE_* flags:** Flags in "additional" strings:
    *   `CP_EDGE_NO_PATH_CROP`
    *   `CP_EDGE_COOKIE_NO_REPLACE`
    *   `CP_EDGE_JWT_NO_AUTH`
    *   `CP_EDGE_PASS_BEARER`
    *   `CP_EDGE_EXTERNAL_APP`
    *   `CP_EDGE_INSTANCE_IP`
*   **Nginx paths / templates:**
    *   `nginx_loc_module_template`
    *   `nginx_sensitive_loc_module_template`
    *   `nginx_loc_module_stub_template`
    *   `nginx_sites_path`
    *   `nginx_domains_path`
*   **DNS settings & prefs:**
    *   `EDGE_DNS_RECORD_FORMAT`
    *   `CP_EDGE_SKIP_CUSTOM_DNS`
    *   `CP_EDGE_CUSTOM_DOMAIN`

## Classes
*   **`ServiceEndpoint`**
    *   **Fields:** `num`, `port`, `path`, `additional` (simple container for endpoint parts).
*   **`RunLogger`**
    *   **Methods:** `info`, `warning`, `success`, `_log` (calls `call_api` to post logs to API).

## Important Functions (Behavior & Responsibilities)
*   **`do_log(msg)`**: Stdout timestamped logging helper.
*   **`call_api(method_url, data=None)`**: HTTP GET/POST to API with retry loop (`NUMBER_OF_RETRIES`, `SECS_TO_WAIT_BEFORE_RETRY`); parses JSON and expects `status == 'OK'` to return payload.
*   **`parse_pretty_url(pretty)`**: Safely normalizes pretty-url JSON into `{domain, path}` or `None`.
*   **`construct_additional_endpoints_from_run_parameters(run_details)`**: Reads run parameters named `CP_CAP_CUSTOM_TOOL_ENDPOINT_<n>_*` and groups them into additional endpoints.
*   **`match_sys_endpoint_value(param_value, endpoint_value)`**: Compares parameter values with system endpoint value (supports boolean/expr).
*   **`append_additional_endpoints(tool_endpoints, run_details)`**: Merges system endpoints and run-parameter custom endpoints into tool endpoints, taking care of overrides and defaults.
*   **`get_active_runs(pods)`**: Calls API to fetch runs details for run IDs derived from pod labels.
*   **`get_service_list(active_runs_list, pod_id, pod_run_id, pod_ip)`**: Core function that:
    1.  Selects run info for pod.
    2.  Reads tool endpoints config and appends additional endpoints.
    3.  For each endpoint, computes `edge_location` (id or pretty URL path), `edge_target` (pod IP + port + optional path), flags (cookie location, jwt auth, pass bearer, external app), and builds a `service_list` dict entry keyed by `edge_location_id` with full metadata used later to render nginx templates and update API.
*   **`load_pods_for_runs_with_endpoints(selector_key, selector_value)`**: Lists running pods matching selector and picks those either labeled as Service or with env vars that match system endpoints.
*   **`create_dns_record(service_spec, edge_region_id, edge_region_name)`**: Posts DNS create request to API and on success sets `service_spec["custom_domain"]`.
*   **`create_service_dns_record(...)`**: Wrapper to call `create_dns_record`, returning route on success or `None`.
*   **`create_service_location(service_spec, service_url_dict, edge_region_id)`**: Renders `nginx_loc_module_template` content, writes a config file at `nginx_sites_path/<edge_location_path>.conf`, possibly writes sensitive subroutes, calls `add_custom_domain` when needed, then `check_route`, and finally composes `SVC_URL_TMPL` entry into `service_url_dict`.
*   **`write_stub_location_configuration(...)`, `check_nginx_config()`, `reload_nginx_config()`**: Handle config validation and fallback to stub files if raw config fails.
*   **`remove_custom_domain`, `add_custom_domain`**: Manage domain-specific server block includes (adds/removes include lines in domain configs).
*   **Utility functions:** `get_pods`, `get_pod`, `get_affected_routes`, `is_true`, file helpers.

## Main Script Flow for unrefactored sync-routes.py 
1.  **Validate API and API_TOKEN.** Abort if missing.
2.  **Initialize kube_api** (`pykube` HTTPClient using service account).
3.  **Determine edge_region_name & edge_region_id** via env or API preference (`find_preference` uses `call_api`).
4.  **Determine** whether to `skip_custom_dns` and `dns_domain`.
5.  **Locate the Edge Kubernetes Service** by labels `cloud-pipeline/role=EDGE` and region label; get `edge_service_external_ip` & `edge_service_port` either from labels or spec with retries.
6.  **`load_pods_for_runs_with_endpoints`** — gather pods that are a Service or have environment vars indicating system endpoints.
7.  **`get_active_runs`** — query API for runs metadata of those pods.
8.  **For each pod, call `get_service_list`** to build `services_list` (dict keyed by route id, values are `service_spec` metadata).
9.  **Read current nginx site files** in `nginx_sites_path`; normalize to sets `routes_actual` and compute:
    *   `routes_expected` (from `services_list`)
    *   `routes_to_check` = intersect
    *   `routes_to_add`, `routes_to_delete`
10. **For `routes_to_check`**, compare shared users/groups inside current route file to expected; mark `routes_to_update`.
11. **Compute `routes_to_replace`** and then union add/delete accordingly to ensure pod-level atomicity.
12. **Delete `routes_to_delete` files** and corresponding custom domain includes.
13. **Read templates & sensitive routes configuration** into memory.
14. **Partition `routes_to_add`** into `regular_routes_to_add` (no DNS creation) and `dns_routes_to_configure` (require DNS creation).
15. **For each regular route:** call `create_service_location` (writes file, updates `service_url_dict`).
16. **For dns routes:** dispatch `create_service_dns_record` into a thread pool (`dns_services_pool`) and collect results.
17. **Reload nginx** if regular adds or deletes occurred.
18. **For runs with `service_url_dict` entries** that were not DNS-threaded, call `update_svc_url_for_run` to POST service URL to API.
19. **Wait for DNS threads**, collect succeeded DNS routes, create their nginx configs, reload nginx if needed, and update API for DNS-handled runs.
20. **Exit.**

## Key Runtime Structures
**services_list**:  
A dict keyed by `edge_location_id` (`{pod_id}-{endpoint_port}-{endpoint_num}`) → `service_spec` dict with fields:

------------------------------------------------------------
| Field                | Description                       |
|----------------------|-----------------------------------|
| edge_location_path   | Nginx config path                 |
| pod_id               | Pod identifier                    |
| pod_ip               | Pod IP address                    |
| pod_owner            | Owner of the run                  |
| shared_users_sids    | Shared user SIDs                  |
| shared_groups_sids   | Shared group SIDs                 |
| service_name         | Name of the service               |
| is_default_endpoint  | Is default endpoint               |
| is_ssl_backend       | SSL backend flag                  |
| is_same_tab          | Same tab flag                     |
| edge_num             | Endpoint number                   |
| edge_location        | Location ID or pretty URL path    |
| custom_domain        | Custom domain (if any)            |
| edge_target          | Target IP/port/path               |
| run_id               | Run identifier                    |
| additional           | Additional endpoint info          |
| sensitive            | Sensitive route flag              |
| create_dns_record    | DNS record creation flag          |
| cloudRegionId        | Cloud region ID                   |
| external_app         | External app flag                 |
| cookie_location      | Cookie location flag              |
| edge_jwt_auth        | JWT auth flag                     |
| edge_pass_bearer     | Pass bearer flag                  |
------------------------------------------------------------
## Where Templates and Files are Used
*   **Read templates:**
    *   `nginx_loc_module_template`
    *   `nginx_sensitive_loc_module_template`
    *   `nginx_loc_module_stub_template`
*   **Write location config:** `nginx_sites_path/<edge_location_path>.conf` (or `.loc.conf`/`.inc`).
*   **If custom domain:** Edits server block file at `nginx_domains_path/<domain>.srv.conf` (via `add_custom_domain`).