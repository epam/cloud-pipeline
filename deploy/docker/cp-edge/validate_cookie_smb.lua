-- Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

-- Validates the Cloud Pipeline JWT for the SMB session credentials endpoints, which are
-- proxied to the cp-dav SMB server (see the SMB location in the nginx.conf).
--
-- The token is accepted as:
-- - "Authorization: Bearer <token>" header (this is the way the CLI/scripts call the endpoint)
-- - "Authorization: Basic <base64(user:token)>" header (only the "password" is used, as it is
--   expected to contain the token)
-- - "bearer" cookie (set by the Cloud Pipeline SSO)
--
-- Unlike the WebDAV locations, an unauthenticated request is NOT redirected to the SSO
-- authentication route: these endpoints are called programmatically (POST/DELETE) and such
-- clients do not follow the form-based redirect, so a plain 401 (with a json body) is returned.

local APPLICATION = "SMB-" .. ngx.var.request_uri

local function is_empty(str)
    return str == nil or str == ''
end

local function split_str(inputstr, sep)
    local t = {} ; local i = 1
    for str in string.gmatch(inputstr, "([^" .. sep .. "]+)") do
        t[i] = str
        i = i + 1
    end
    return t
end

local function unauthorized(user, message)
    ngx.log(ngx.WARN, "[SECURITY] Application: " .. APPLICATION .. "; User: " .. user ..
            "; Status: Authentication failed; Message: " .. message)
    ngx.status = ngx.HTTP_UNAUTHORIZED
    ngx.header['Content-Type'] = 'application/json'
    ngx.say('{"error": "' .. message:gsub('[\\"]', ' ') .. '"}')
    ngx.exit(ngx.HTTP_UNAUTHORIZED)
end

-- Returns the token and the way it was received ("header" or "cookie"), or nil if it is not set
local function get_token()
    local authorization = ngx.var.http_authorization
    if not is_empty(authorization) then
        if authorization:find("Bearer ") == 1 then
            local bearer = string.sub(authorization, 8)
            if not is_empty(bearer) then
                return bearer, "header"
            end
            ngx.log(ngx.WARN, "Bearer HTTP Authorization header is set, but it has no value")
        elseif authorization:find("Basic ") == 1 then
            local basic = ngx.decode_base64(string.sub(authorization, 7))
            if is_empty(basic) then
                ngx.log(ngx.WARN, "Basic HTTP Authorization header is set, but it cannot be decoded from base64")
            else
                -- Split the Authorization header value by colon, i.e. "user:password".
                -- We care only about the password, as it shall contain the access token
                local user_pass = split_str(basic, ':')
                if not is_empty(user_pass[2]) then
                    return user_pass[2], "header"
                end
                ngx.log(ngx.WARN, "Basic HTTP Authorization header is set and decoded, but the password is missing")
            end
        else
            ngx.log(ngx.WARN, "HTTP Authorization header is set, but neither Bearer nor Basic schema is used")
        end
    end

    local cookie = ngx.var.cookie_bearer
    if not is_empty(cookie) then
        return cookie, "cookie"
    end

    return nil, nil
end

local token, token_source = get_token()
if is_empty(token) then
    unauthorized("NotAuthorized", "Access token is not provided")
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

local username = "NotAuthorized"
if jwt_obj["payload"] ~= nil and jwt_obj["payload"]["sub"] ~= nil then
    username = jwt_obj["payload"]["sub"]
end

if not jwt_obj["verified"] then
    if token_source == "cookie" then
        ngx.header['Set-Cookie'] = 'bearer=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    end
    unauthorized(username, jwt_obj.reason)
end

-- The SMB server authenticates the requests by the "Bearer" token only, so the validated token
-- is always forwarded in that form (even if it was received as a Basic one or as a cookie)
ngx.req.set_header('Authorization', 'Bearer ' .. token)

ngx.log(ngx.WARN, "[SECURITY] Application: " .. APPLICATION .. "; User: " .. username ..
        "; Status: Successfully authenticated.")
