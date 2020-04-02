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
        if value == val then
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

local function is_empty(str)
    return str == nil or str == ''
end

local function get_basic_token()
    local authorization = ngx.var.http_authorization
    if is_empty(authorization) then
        return nil
    end

    -- Check that Authorization header starts with "Basic: ", other header schemas are not supported
    -- FIXME: shall we add "Bearer: " as well?
    if authorization:find("Basic ") ~= 1 then
        ngx.log(ngx.WARN, "HTTP Authorization header is set, but it is not Basic: " .. authorization)
        return nil
    end

    -- Crop the "Basic: " from the Authorization header - base64 encoded value will be retrieved
    local basicb64 = string.sub(authorization, 7)
    if is_empty(basicb64) then
        ngx.log(ngx.WARN, "Basic HTTP Authorization header is set, it has no value: " .. authorization)
        return nil
    end

    -- Decode the base64 representation of the Authorization header value
    local basic = ngx.decode_base64(basicb64)  
    if is_empty(basic) then
        ngx.log(ngx.WARN, "Basic HTTP Authorization header is set, but can not decode from base64: " .. authorization)
        return nil
    end

    -- Split the Authorization header value by colon, i.e. "user:password"
    local user_pass = split_str(basic, ':')
    local user = user_pass[1]
    local pass = user_pass[2]

    if (is_empty(user) or is_empty(pass)) then
        ngx.log(ngx.WARN, "Basic HTTP Authorization header is set and decoded, but user or pass is missing: " .. authorization)
        return nil
    end

    -- We care only about password, as it shall contain the access token
    return pass
end

-- Here we replace the host and port of the Destination header to the cluster internal host:port
-- Reasons for this are descibed in nginx.conf
-- Previously this was hapenning in the nginx.conf itself, but caused "double url encoding" for urls with whitespaces (and other special symbols), e.g.:
-- - This URL was specific in Destination by a client /webdav/folder%201
-- - Then it was processed and passed to the underlying DAV server as /webdav/folder%25201
-- - I.e. "%" was encoded into %25
local move_dest = ngx.var.http_destination
if move_dest ~= nil then
  local resty_url = require 'resty.url'
  local move_dest_parsed = resty_url.parse(move_dest)
  local move_dest_internal = resty_url.join(ngx.var.cp_dav_backend, move_dest_parsed.path)
  ngx.var.dav_dest_path = move_dest_internal
end


-- First try to get the HTTP Basic auth from the request
local token = get_basic_token()
if is_empty(token) then
    -- HTTP Basic auth is not set - check if request alread contains a cookie "bearer"
    token = ngx.var.cookie_bearer
end

if token then
    -- If token is present - validate it
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

        local jwt_username = "NotAuthorized"
        if jwt_obj["payload"] ~= nil and jwt_obj["payload"]["sub"] ~= nil then
            jwt_username = jwt_obj["payload"]["sub"]
        end


    -- If "bearer" token is not valid - return 401 and clear cookie
        if not jwt_obj["verified"] then
            ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.log(ngx.WARN, "[SECURITY] Application: DAV-" .. ngx.var.request_uri .. "; User: " .. jwt_username ..
                    "; Status: Authentication failed; Message: " .. jwt_obj.reason)
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
            ngx.log(ngx.WARN, "[SECURITY] Application: DAV-" .. ngx.var.request_uri .. "; User: " .. jwt_username ..
                    "; Status: Authentication failed; Message: Restrict writing to the root")
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
            ngx.log(ngx.WARN, "[SECURITY] Application: DAV-" .. ngx.var.request_uri ..
                    "; User: " .. jwt_username .. "; Status: Authentication failed; Message: username is not provided")
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- If JWT Subject is not equal to the uri_username - restrict connection
        local jwt_username = jwt_obj["payload"]["sub"]
        if jwt_username ~= uri_username then
            ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.log(ngx.WARN, "[SECURITY] Application: DAV-" .. ngx.var.request_uri ..
                    "; User: " .. jwt_username .. "; Status: Authentication failed; "
                    .. "Message: JWT Subject is not equal to the uri_username - restrict connection")
            ngx.log(ngx.WARN, jwt_obj.reason)
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
        end

    -- If "bearer" is fine - allow nginx to proceed
    if string.gmatch(ngx.var.request_uri, "^/webdav/[%w_-]+/$") then
        ngx.log(ngx.WARN,"[SECURITY] Application: DAV-" .. ngx.var.request_uri ..
                "; User: " .. jwt_username .. "; Status: Successfully autentificated.")
    end
    return
end

-- If no HTTP Basic auth and "bearer" cookie are found - proceed with authentication using API

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
