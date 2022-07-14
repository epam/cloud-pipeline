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

require"maintenance"

-- maintenance_authenticate_user script is used for parsing /restapi/route?url=... response;

-- Checking auth response body & parsing bearer token; `allow_user` will be set to:
--  * true - if user is administrator / privileged
--  * false otherwise
local allow_user = maintenance.parse_auth_api_response()
-- Setting MAINTENANCE response cookie; this cookie is used for preventing
-- authentication calls for each user's request while system is in the maintenance mode.
maintenance.set_user_is_admin_cookie(allow_user)

-- Generally, authentication fallback url is of "/maintenance/auth?from=<original request url>" format;
-- if `from` query parameter is presented - request will be redirected to the original request url.
if ngx.var.arg_from ~= nil then
    ngx.redirect(ngx.var.arg_from)
else
    ngx.redirect("/maintenance")
end
