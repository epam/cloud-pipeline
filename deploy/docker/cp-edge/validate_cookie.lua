-- Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

local function arr_length(T)
    local count = 0
    for _ in pairs(T) do count = count + 1 end
    return count
end

local function split_str(inputstr, sep)
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

-- Check if request alread contains a cookie "bearer"
local token = ngx.var.cookie_bearer
if token then

    -- If cookie present - validate it
    local cert_path = os.getenv("JWT_PUB_KEY")
    local cert_file = io.open(cert_path, 'r')
    local cert = cert_file:read("*all")
    cert_file:close()

    local jwt = require "resty.jwt"
    local validators = require "resty.jwt-validators"
    local claim_spec = {
        exp = validators.is_not_expired(),
        -- iat = validators.is_not_before(),
    }

    local jwt_obj = jwt:verify(cert, token, claim_spec)

    local username = "NotAuthorized"
    if jwt_obj["payload"] ~= nil and jwt_obj["payload"]["sub"] ~= nil then
        username = jwt_obj["payload"]["sub"]
    end

    -- If "bearer" token is not valid - return 401 and clear cookie
    if not jwt_obj["verified"] then
        ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.log(ngx.WARN, "[SECURITY] Application: " .. ngx.var.route_location_root ..
                "; User: " .. username .. "; Status: Authentication failed; Message: " .. jwt_obj.reason)
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end
    local user_roles = jwt_obj["payload"]["roles"]
    local shared_with_users = split_str(ngx.var.shared_with_users, ',')
    local shared_with_groups = split_str(ngx.var.shared_with_groups, ',')

    -- 401 if:
    -- this is not an owner
    -- username is not listed in the sharing list
    -- user's role is not listed in the sharing list
    if username ~= ngx.var.username and not arr_has_value(shared_with_users, username) and not arr_intersect(user_roles, shared_with_groups) then
        ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.log(ngx.WARN, "[SECURITY] Application: " .. ngx.var.route_location_root ..
                "; User: " .. username .. "; Status:  Authentication failed; Message: Not an owner and access isn't shared.")
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end
    
    -- 401 if:
    -- user is anonymous and such access is not enabled
    if os.getenv("CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER") ~= "true" and arr_has_value(user_roles, "ROLE_ANONYMOUS_USER") then
        ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.log(ngx.WARN, "[SECURITY] Application: " .. ngx.var.route_location_root ..
                "; User: " .. username .. "; Status:  Authentication failed; Message: Anonymous access is disabled.")
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    -- If "bearer" is fine - allow nginx to proceed
    -- Pass authenticated user to the proxied resource as a header
    if ngx.var.route_location_root == ngx.var.request_uri then
        ngx.log(ngx.WARN,"[SECURITY] Application: " .. ngx.var.route_location_root ..
                "; User: " .. username .. "; Status: Successfully authenticated.")
    end
    ngx.req.set_header('X-Auth-User', username)
    return
end

-- If no "bearer" cookie is found - proceed with authentication using API

-- Construct full current URL to use for redirection
local req_host_port = os.getenv("EDGE_EXTERNAL")
if ngx.var.http_host then
    req_host_port = ngx.var.http_host
end
local req_schema = os.getenv("EDGE_EXTERNAL_SCHEMA")
if ngx.var.scheme then
    req_schema = ngx.var.scheme
end
local req_uri = req_schema .."://" .. req_host_port .. ngx.var.request_uri

-- Construct API Auth call URL
local api_endpoint = os.getenv("API_EXTERNAL")
if not api_endpoint then
    api_endpoint = os.getenv("API")
end
local api_uri = api_endpoint .. "/route?url=" .. req_uri .. "&type=FORM"

-- Get list of POST params, if a request from API is received
ngx.req.read_body()
local args, err = ngx.req.get_post_args()
if not args then
    -- If no attributes found - consider it an initial request and send to API
    ngx.redirect(api_uri)
    return
end

-- Search for "bearer" value in POST params
for key, val in pairs(args) do
    if key == "bearer" then
        token = val
        break
    end
end

if token == nil then
    -- If no bearer param found - consider it an initial request and send to API
    ngx.redirect(api_uri)
    return
else
    -- If "bearer" param is found - set it as cookie and redirect to initial uri
        ngx.header['Set-Cookie'] = 'bearer=' .. token  .. '; path=/'
        ngx.say('<html><body><script>window.location.href = "' .. req_uri .. '"</script></body></html>')
        return
end
