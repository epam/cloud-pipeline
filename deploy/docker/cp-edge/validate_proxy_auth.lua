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

local function is_empty(str)
    return str == nil or str == ''
end

local function dict_contains(dict, key)
    return dict[key] ~= nil
end

local function check_run_permissions(username, token)
    local run_api_url = os.getenv("API")
    if not run_api_url or not token then
        ngx.log(ngx.ERR, "[SECURITY] Application: Request: " .. ngx.var.request .. "; User: " .. username ..
                "; Status: Authentication failed; Message: Cannot get API environment variable or api token was not provided.")
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
        return
    end

    local http = require "resty.http"
    local httpc = http.new()

    -- Perform the <API>/run/search request
    local run_id_api_url = run_api_url .. 'run/search'
    local connect_host = ngx.var.connect_host
    local body_content = [[{
                            "filterExpression": {
                                "filterExpressionType": "AND",
                                "expressions": [
                                    {
                                        "field": "pod.ip",
                                        "value": "']] .. connect_host .. [['",
                                        "operand": "=",
                                        "filterExpressionType": "LOGICAL"
                                    },
                                    {
                                        "field": "status",
                                        "value": "RUNNING",
                                        "operand": "=",
                                        "filterExpressionType": "LOGICAL"
                                    }
                                ]
                            },
                            "page": 1,
                            "pageSize": 1
                          }]]
    local res, err = httpc:request_uri(run_id_api_url, {
        method = "POST",
        body = body_content,
        headers = {
            ["Authorization"] = "Bearer " .. token,
            ["Content-Type"] = 'application/json'
        },
        ssl_verify = false
    })

    if not res then
        ngx.log(ngx.ERR, "[SECURITY] Request: " .. ngx.var.request .. "; User: " .. username ..
                "; Status: Authentication failed; Message: Failed to request API for the SSH Password. API: " ..
                run_id_api_url .. ', Error: ' .. err)
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
        return
    end

    local cjson = require "cjson"

    -- Parse the response and get run id
    local data = cjson.decode(res.body)
    local run_id = ''
    if not dict_contains(data, 'payload') or
       not dict_contains(data.payload, 'elements') or
       is_empty(data.payload.elements[1]) or
       is_empty(data.payload.elements[1].id) then
        ngx.log(ngx.ERR, "[SECURITY] Request: " .. ngx.var.request .. "; User: " .. username ..
                "; Status: Authentication failed; Message: Requested from pod IP run is not found.")
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
        return
    else
        run_id = data.payload.elements[1].id
    end

    -- Perform the <API>/run/<run_id> request
    local run_sshpassword_api_url = run_api_url .. 'run/' .. run_id
    local res, err = httpc:request_uri(run_sshpassword_api_url, {
        method = "GET",
        headers = {
            ["Authorization"] = "Bearer " .. token,
            ["Content-Type"] = 'application/json'
        },
        ssl_verify = false
    })

    if not res then
        ngx.log(ngx.ERR, "[SECURITY] Request: " .. ngx.var.request .. "; User: " .. username ..
                "; Status: Authentication failed; Message: Failed to request API for the ssh password. API: " ..
                run_sshpassword_api_url .. ', Error: ' .. err)
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
        return
    end

    -- Parse the response and get the sshPassword parameter
    local data = cjson.decode(res.body)
    if not dict_contains(data, 'payload') or is_empty(data.payload.sshPassword) then
        if is_empty(data[message]) then
            message = "Cannot get sshPassword from the API response"
        else
            message = data.message
        end
        ngx.log(ngx.ERR, "[SECURITY] Request: " .. ngx.var.request .. "; User: " .. username ..
                "; Status: Authentication failed; Message: " .. message)
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
        return
    end
end

local function get_basic_token()
    local authorization = ngx.var.http_proxy_authorization

    if is_empty(authorization) then
        return nil
    end

    -- Check that Authorization header starts with "Basic: ", other header schemas are not supported
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

    -- Remove any whitespace/newline from the token (some clients tend to add trailing newline)
    pass = string.gsub(pass, '%s+', '')

    if (is_empty(user) or is_empty(pass)) then
        ngx.log(ngx.WARN, "Basic HTTP Authorization header is set and decoded, but user or pass is missing: " .. authorization)
        return nil
    end
    return pass
end

local function is_allowed_connect_host(host, host_whitelist)
    if is_empty(host) then
        return false
    end
    if is_empty(host_whitelist) then
        return false
    end
    for host_whitelist_suffix in host_whitelist.gmatch(host_whitelist, '([^,]+)') do
        if host:sub(-#host_whitelist_suffix) == host_whitelist_suffix then
            return true
        end
    end
    return false
end

local token = get_basic_token()

if is_empty(token) then
    ngx.log(ngx.WARN, "[SECURITY] Request " .. ngx.var.request ..
            " is rejected; Status: Authentication failed; Message: Token is not provided")
    ngx.header["Proxy-Authenticate"] = "Basic realm=\"Cloud Pipeline EDGE\""
    ngx.exit(407)
end

local cert_path = os.getenv("JWT_PUB_KEY")
local cert_file = io.open(cert_path, 'r')
local cert = cert_file:read("*all")
cert_file:close()

local jwt = require "resty.jwt"
local validators = require "resty.jwt-validators"
local claim_spec = {
    exp = validators.is_not_expired()
}

local jwt_obj = jwt:verify(cert, token, claim_spec)

local username = "NotAuthorized"
if jwt_obj["payload"] ~= nil and jwt_obj["payload"]["sub"] ~= nil then
    username = jwt_obj["payload"]["sub"]
end

if not jwt_obj["verified"] then
    ngx.status = ngx.HTTP_UNAUTHORIZED
    ngx.log(ngx.WARN, "[SECURITY] Request: " .. ngx.var.request ..
            "; User: " .. username .. "; Status: Authentication failed; Message: " .. jwt_obj.reason)
    ngx.exit(ngx.HTTP_UNAUTHORIZED)
end

local connect_host = ngx.var.connect_host
local connect_host_whitelist = os.getenv("CP_EDGE_CONNECT_PROXY_AUTHENTICATION_WHITELIST")
if not is_allowed_connect_host(connect_host, connect_host_whitelist) then
    check_run_permissions(username, token)
end

ngx.log(ngx.WARN,"[SECURITY] Request: " .. ngx.var.request .. "; User: " .. username .. "; Status: Successfully authenticated.")
