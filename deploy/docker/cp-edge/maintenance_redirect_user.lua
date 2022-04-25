-- Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- We're processing "general" request (GUI, restapi, docker, git etc.).
-- At this point we have $maintenance and $maintenance_user_allowed variables set (by maintenance_check_user.lua):
--  * If $maintenance_user_allowed == "deny" (=> $maintenance is ON),
--      we'll proxy pass this request to the /maintenance
--  * If $maintenance_user_allowed == "allow" (=> $maintenance is OFF for current request)
--      we'll proxy pass this request to the corresponding handler
--  * If $maintenance_user_allowed == "redirect",
--      we should redirect current request to the authentication endpoint
if ngx.var.maintenance_user_allowed == "redirect" then
    -- We're in maintenance mode and we don't know user roles.
    -- In this case, we should proceed with authenticating user

    -- Construct full current URL to use for redirection
    local req_host = ngx.var.host
    local req_uri = ngx.var.scheme .."://" .. req_host .. ngx.var.request_uri

    -- Construct authentication endpoint' fallback url:
    local maintenance_auth_uri = ngx.var.scheme .. "://" .. req_host .. "/maintenance/auth?from=" .. req_uri

    -- Construct API Auth call URL
    local api_endpoint = os.getenv("API_EXTERNAL")
    if not api_endpoint then
        api_endpoint = os.getenv("API")
    end
    local api_uri = api_endpoint .. "/route?url=" .. ngx.escape_uri(maintenance_auth_uri) .. "&type=FORM"
    ngx.redirect(api_uri)
end
