-- Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
        if value == val then
            return true
        end
    end

    return false
end


function arr_length(T)
    local count = 0
    for _ in pairs(T) do count = count + 1 end
    return count
end

function split_str(inputstr, sep)
    if sep == nil then
        sep = "%s"
    end
    local t={} ; i=1
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

    -- If "bearer" token is not valid - return 401 and clear cookie
        if not jwt_obj["verified"] then
            ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.log(ngx.WARN, jwt_obj.reason)
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- Parse request uri and split it into parts
    -- E.g. /webdav/UserName1/ will provide { 'webdav', 'UserName1' }
        local uri_raw = ngx.var.request_uri
        local uri_parts =  split_str(uri_raw,'/')
        local uri_parts_len = arr_length(uri_parts)
        local uri_username = nil
        local request_method = ngx.req.get_method()
        
    -- Restrict writing to the root dirs/files as they are not mounted anywhere and serve as "containers":
    -- First level directory: "/webdav"
    -- User-level directory: "/webdav/UserName1"
    -- Objects in the user-level directory: "/webdav/UserName1/file.txt"
        local restricted_root_methods = { "PUT", "POST", "DELETE", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK", "PATCH" }
        if (uri_parts_len <= 3 and arr_has_value(restricted_root_methods, request_method)) then
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- Check whether this is an admin token
    -- If so - allow nginx to proceed with whatever is requested
        if arr_has_value(jwt_obj["payload"]["roles"], "ROLE_ADMIN") then
            return
        end

    -- We always treat uri to contain username as a second item
        if uri_parts_len > 1  then
            uri_username = uri_parts[2]
        else
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- If JWT Subject is not equal to the uri_username - restrict connection
        local jwt_username = jwt_obj["payload"]["sub"]
        if jwt_username ~= uri_username then
            ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.log(ngx.WARN, jwt_obj.reason)
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- If "bearer" is fine - allow nginx to proceed
    return
end

-- If no "bearer" cookie is found - proceed with authentication using API

-- Construct full current URL to use for redirection
local req_host_port = os.getenv("EDGE_EXTERNAL")
if not req_host_port then
    req_host_port = ngx.var.host .. ":" .. ngx.var.server_port
end
local req_uri = ngx.var.scheme .."://" .. req_host_port .. ngx.var.request_uri

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
        ngx.header['Set-Cookie'] = 'bearer=' .. token  .. '; path=/; expires=Thu, 01 Jan 2222 00:00:00 GMT'
        ngx.say('<html><body><script>window.location.href = "' .. req_uri .. '"</script></body></html>')
        return
end

-- No cookie, no POST param - 401
ngx.status = ngx.HTTP_UNAUTHORIZED
ngx.exit(ngx.HTTP_UNAUTHORIZED)
