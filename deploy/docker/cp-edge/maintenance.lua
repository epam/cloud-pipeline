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

-- Common functions
local function arr_concat(t1,t2)
    if t2 == nil then
        return t1
    end
    for i=1,#t2 do
        t1[#t1+1] = t2[i]
    end
    return t1
end

local function arr_has_value (tab, val)
    for index, value in ipairs(tab) do
        if value:lower() == val:lower() then
            return true
        end
    end

    return false
end

local function arr_intersect(a, b)
	for index, value in ipairs(a) do
        if arr_has_value(b, value) then
            return true
        end
    end

    return false
end

local function split_str(inputstr, sep)
    if inputstr == nil then
        return {}
    end
    if sep == nil then
        sep = "%s"
    end
    local t={} ; local i=1
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        t[i] = str
        i = i + 1
    end
    return t
end

local P = {}
maintenance = P

-- check_user_is_admin function is used for parsing user token and checking if user is admin / privileged user.
-- "privileged" user means that user has ROLE_ADMIN role or role from $CP_MAINTENANCE_SKIP_ROLES list (comma separated string).
function P.check_user_is_admin(token)
    if token == nil then
        return false
    end

    local skip_maintenance_roles = { "ROLE_ADMIN" }
    local cp_maintenance_skip_roles = os.getenv("CP_MAINTENANCE_SKIP_ROLES")
    if cp_maintenance_skip_roles ~= nil then
        skip_maintenance_roles = arr_concat( { "ROLE_ADMIN" }, split_str(cp_maintenance_skip_roles))
    end

    local cert_path = os.getenv("JWT_PUB_KEY")
    local cert_file = io.open(cert_path, 'r')
    local cert = cert_file:read("*all")
    cert_file:close()

    local jwt = require "resty.jwt"
    local validators = require "resty.jwt-validators"
    local claim_spec = {
        exp = validators.is_not_expired(),
    }

    local jwt_obj = jwt:verify(cert, token, claim_spec)

    if not jwt_obj["verified"] then
        return true
    end

    local user_roles = arr_concat(jwt_obj["payload"]["roles"], jwt_obj["payload"]["groups"])

    if arr_intersect(user_roles, skip_maintenance_roles) then
        return true
    end

    return false
end

-- checks MAINTENANCE cookie; if MAINTENANCE cookie is missing - return nil,
-- otherwise returns true if MAINTENANCE == "admin"
function P.check_user_is_admin_cookie()
    if ngx.var.cookie_maintenance ~= nil then
        return ngx.var.cookie_maintenance == "admin"
    end
    return nil
end

-- sets MAINTENANCE cookie
function P.set_user_is_admin_cookie(admin)
    local cookie_expire = 300 -- 5 minutes
    local maintenance_mode = "user";
    if admin then
        maintenance_mode = "admin"
    end
    ngx.header['Set-Cookie'] = 'MAINTENANCE=' .. maintenance_mode ..'; path=/; Secure; Expires=' .. ngx.cookie_time(ngx.time() + cookie_expire)
end

-- Parses "restapi/route?url=..." fallback body.
-- If `bearer` form item is presented, checks if user is admin.
-- Returns true if user is admin / privileged
function P.parse_auth_api_response()
    local allow_user = false -- true if user is allowed to operate in maintenance mode
    if ngx.var.maintenance_user_allowed == "allow" then
        allow_user = true
    end
    local token = nil
    -- Get list of POST params, if a request from API is received
    ngx.req.read_body()
    local args, err = ngx.req.get_post_args()
    if args then
        -- Search for "bearer" value in POST params
        for key, val in pairs(args) do
            if key == "bearer" then
                token = val
                break
            end
        end
    end

    if token ~= nil then
        -- We have token - we need to check if user belongs to administrators / privileged groups
        if P.check_user_is_admin(token) then
            allow_user = true
        else
            allow_user = false
        end
    end

    return allow_user
end

return maintenance
