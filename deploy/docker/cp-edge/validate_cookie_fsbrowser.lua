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

function is_int(n)
    return tonumber(n) ~= nil
end

function split_str(inputstr, sep)
    if sep == nil then
        sep = "%s"
    end
    local t={}
    local i=1
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        t[i] = str
        i = i + 1
    end
    return t
end

function arr_length(T)
    local count = 0
    for _ in pairs(T) do count = count + 1 end
    return count
end

function arr_to_string(T, sep)
    if sep == nil then
        sep = ''
    end
    local r = ''
    for _, i in pairs(T) do
        r = r .. sep .. i
    end
    return r
end

function dict_contains(dict, key)
    return dict[key] ~= nil
end

local function create_basic_auth(username, password)
	-- the header format is "Basic <base64 encoded username:password>"
	local header = "Basic "
	local credentials = ngx.encode_base64(username .. ":" .. password)
	header = header .. credentials
	return header
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
        ngx.log(ngx.ERR, "[SECURITY] Application: FSBrowser; User: NotAuthorized; Status: Authentication failed; Message: " .. jwt_obj.reason)
        ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    local username = jwt_obj["payload"]["sub"]

    -- --------------------------------------------
    -- token is ok - proceed with forwarding setup
    -- Parse the initial URI (/fsbrowser/<RUN_ID>/...) to get the run_id
    local uri_raw = ngx.var.request_uri
    local uri_parts =  split_str(uri_raw, '/')

    -- Fail if the initial URI does have "/fsbrowser/<RUN_ID>" parts
    local uri_parts_len = arr_length(uri_parts)
    if (uri_parts_len < 2) then
        ngx.log(ngx.ERR, 'FSBrowser URI length is less then 2: ' .. uri_raw)
        ngx.status = ngx.HTTP_NOT_FOUND
        ngx.exit(ngx.HTTP_NOT_FOUND)
        return
    end

    local run_id = uri_parts[2]
    -- Fail if the <RUN_ID> in the URI is not a number
    if not is_int(run_id) then
        ngx.log(ngx.ERR, 'Run ID in the FSBrowser URI is not a number. Run ID: ' .. run_id .. ', URI: ' .. uri_raw)
        ngx.status = ngx.HTTP_NOT_FOUND
        ngx.exit(ngx.HTTP_NOT_FOUND)
        return
    end

    -- Remove first two elements from the initial URI to get the trailing part (/fsbrowser/<RUN_ID>/view/...), if there is any
    table.remove(uri_parts, 1)
    table.remove(uri_parts, 1)
    uri_parts_len = arr_length(uri_parts)

    -- If the initial URI has a trailing part - keep it for further use in the nginx proxy_pass
    local trailing_req_uri = ''
    if (uri_parts_len > 0) then
        trailing_req_uri = arr_to_string(uri_parts, '/')
    end

    -- Now we need to get the target IP (i.e. Pod IP) and the access token 
    -- (i.e. defined by the SSH_PASS, which available only to ADMINs and OWNERs)
    -- First we'll try to get it from the cookie (fsbrowser_<RUN_ID>), to reduce number of the requests to API
    local pod_cookie_name = 'fsbrowser_' .. run_id
    local pod_cookie_val = ngx.var['cookie_' .. pod_cookie_name]
    local pod_ip = ''
    local ssh_pass = ''

    -- If the cookie is not set - proceed with the API request
    if not pod_cookie_val then
        local run_api_url = os.getenv("API")
        local run_api_token = os.getenv("API_TOKEN")
        -- Fail if the API connection parameters cannot be retrieved from the environment
        if not run_api_url or not run_api_token then
            ngx.log(ngx.ERR, "[SECURITY] Application: FSBrowser; User: NotAuthorized; Status: Authentication failed; Message: Cannot get API or API_TOKEN environment variables")
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
            return
        end
        
        -- Perform the request to <API>/run/<RUN_ID>
        run_api_url = run_api_url .. 'run/' .. run_id
        local http = require "resty.http"
        local httpc = http.new()
        local res, err = httpc:request_uri(run_api_url, {
            method = "GET",
            headers = {
                ["Authorization"] = "Bearer " .. run_api_token,
            },
            ssl_verify = false
        })

        -- Fail if the request was not successful
        if not res then
            ngx.log(ngx.ERR, "[SECURITY] Application: FSBrowser; User: NotAuthorized; Status: Authentication failed; Message: " ..
                    'Failed to request API for the Pod IP and SSH Pass. API - ' .. run_api_url .. ', Error - ' .. err)
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
            return
        end

        -- Parse the response and get the podIP and sshPassword attributes
        local cjson = require "cjson"
        local data = cjson.decode(res.body)
        if not dict_contains(data, 'payload') or 
           not dict_contains(data['payload'], 'sshPassword') or 
           not dict_contains(data['payload'], 'podIP') then
            ngx.log(ngx.ERR, "[SECURITY] Application: FSBrowser; User: NotAuthorized; Status: Authentication failed; Message: " ..
                    'Cannot get podIP and sshPassword from the API response')
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
            return
        end

        -- Set the cookie for further subsequent requests and initialize variables for the nginx proxy_pass
        pod_ip = data['payload']['podIP']
        ssh_pass = data['payload']['sshPassword']
        -- Cookie is set for 1 minute, if the ssh_pass or ip change (e.g. pause/resume) - old values will be deleted
        local pod_cookie_expire = 60 -- sec
        ngx.header["Set-Cookie"] = pod_cookie_name .. '=' .. pod_ip .. ':' .. ssh_pass .. '; Path=/; Expires=' .. ngx.cookie_time(ngx.time() + pod_cookie_expire)
    else
        -- If we still have Pod IP and SSH Pass cached - let's reuse it, before calling API
        -- Cookie is formatted as IP:PASS
        local pod_cookie_parts = split_str(pod_cookie_val, ':')
        local pod_cookie_parts_len = arr_length(pod_cookie_parts)
        if (pod_cookie_parts_len ~= 2) then
            ngx.log(ngx.ERR, 'Cannot parse cookie ' .. pod_cookie_name .. ' value ' .. pod_cookie_val .. 'into IP and Pass')
            ngx.header['Set-Cookie'] = pod_cookie_name .. '=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            ngx.status = ngx.HTTP_UNAUTHORIZED
            ngx.exit(ngx.HTTP_UNAUTHORIZED)
            return
        end
        -- If the cookie was correctly parsed - initialize variables for the nginx proxy_pass
        pod_ip = pod_cookie_parts[1]
        ssh_pass = pod_cookie_parts[2]    
    end

    -- Provide all the collected info to the nginx
    -- Pod IP (used as a target IP for proxy_pass)
    ngx.var.fsbrowser_target = pod_ip
    -- SSH Pass is packed into Basic auth header, as FSBrowser accepts
    ngx.var.fsbrowser_auth = create_basic_auth('root', ssh_pass)
    -- Trailing URI (if exists) will be appended to the target host's endpoint
    ngx.var.fsbrowser_req_uri = trailing_req_uri

    ngx.req.set_header('token', token)
    ngx.req.set_header('X-Auth-User', username)
    ngx.log(ngx.WARN,"[SECURITY] Application: FSBrowser; User: " .. username .. "; Status: Successfully autentificated.")
    return
    -- --------------------------------------------
end

-- If no "bearer" cookie is found - proceed with authentication using API

-- Construct full current URL to use for redirection
local req_host_port = os.getenv("EDGE_EXTERNAL")
local req_schema = os.getenv("EDGE_EXTERNAL_SCHEMA")
if not req_host_port then
    req_host_port = ngx.var.host .. ":" .. ngx.var.server_port
end
if not req_schema then
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
