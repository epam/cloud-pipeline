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

-- maintenance_check_user script is used for:
--  * switching off the maintenance mode for current request
--  * setting $maintenance_user_allowed variable (via set_by_lua_file),
--    which is used for auth redirection decision (maintenance_redirect_user.lua script)

if ngx.var.maintenance == "off" then
    -- System is not in maintenance mode, allow user request
    return "allow"
end

-- We're in the maintenance mode.

-- Checking MAINTENANCE cookie (set by maintenance_authenticate_user.lua script)
local user_is_admin_cookie = maintenance.check_user_is_admin_cookie()
if user_is_admin_cookie ~= nil then
    -- We have MAINTENANCE cookie:
    --  * "user" (false)- deny current request, display /maintenance disclaimer page
    --  * "admin" (true) - allow current request
    if user_is_admin_cookie then
        -- Switching off maintenance mode
        ngx.var.maintenance = "off"
        return "allow"
    end
    return "deny"
end

-- Check if request already contains a cookie or a header named "bearer"
local token = ngx.var.cookie_bearer or ngx.var.http_bearer

-- Check if 'Authorization: Bearer' header is presented (if bearer cookie / bearer header is missing)
if token == nil and ngx.var.http_authorization ~= nil then
    local authorization_token_lower = string.lower(ngx.var.http_authorization)
    local index = string.find(authorization_token_lower, "bearer ", 1, true)
    if index ~= nil and index > 0 then
        token = string.sub(ngx.var.http_authorization, index + 7)
        ngx.log(ngx.WARN, "maintenance authorization header " .. token)
    end
end

if token then
    -- Check if user belongs to administrators / privileged groups
    if maintenance.check_user_is_admin(token) then
        -- switching off maintenance mode
        ngx.var.maintenance = "off"
        return "allow"
    end
    return "deny"
else
    -- Set special flag to redirect user to the authentication endpoint
    -- (will be redirected at maintenance_redirect_user script)
    -- and switch off maintenance mode for current request only
    ngx.var.maintenance = "off"
    return "redirect"
end
