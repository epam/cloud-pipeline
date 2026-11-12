/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.auth;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.dto.auth.TokenExchangeError;
import com.epam.pipeline.dto.auth.TokenExchangeResponse;
import com.epam.pipeline.manager.access.oidc.OidcClientNotAuthorizedException;
import com.epam.pipeline.manager.access.oidc.OidcExchangedToken;
import com.epam.pipeline.manager.access.oidc.OidcTokenExchangeManager;
import com.epam.pipeline.manager.access.oidc.OidcTokenVerificationException;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.token.TokenTypeURI;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Exchanges an identity provider's access token for a platform token, as RFC 8693 describes.
 * <p>
 * Reachable without a platform token, which is the point of it, and which is also why every
 * refusal looks the same from the outside. The reason is logged here and the caller is told only
 * the error code RFC 6749 defines.
 */
@RestController
@Api(value = "Methods to exchange an identity provider's token for a platform token.")
@RequestMapping(value = "/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenController extends AbstractRestController {

    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final String NO_STORE = "no-store";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    private static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    private static final String SERVER_ERROR = "server_error";

    /**
     * A JWT access token can be presented as either, and providers differ over which they call it.
     */
    private static final Set<String> ACCEPTED_SUBJECT_TOKEN_TYPES = new HashSet<>(Arrays.asList(
            TokenTypeURI.ACCESS_TOKEN.getURI().toString(),
            TokenTypeURI.JWT.getURI().toString()));

    private final OidcTokenExchangeManager tokenExchangeManager;

    @PostMapping(value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(
            value = "Exchanges an identity provider's access token for a platform token.",
            notes = "Form encoded, as RFC 8693 defines. Returns the token exchange response "
                    + "directly rather than wrapped, so ordinary OAuth clients can read it.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = "A platform token for the caller")})
    public ResponseEntity<Object> token(
            @RequestParam(value = "grant_type", required = false) final String grantType,
            @RequestParam(value = "subject_token", required = false) final String subjectToken,
            @RequestParam(value = "subject_token_type", required = false) final String subjectTokenType,
            @RequestParam(value = "requested_token_type", required = false) final String requestedTokenType) {
        if (!GrantType.TOKEN_EXCHANGE.getValue().equals(grantType)) {
            log.debug("Token exchange refused: grant type {} is not supported", grantType);
            return error(UNSUPPORTED_GRANT_TYPE);
        }
        if (StringUtils.isBlank(subjectToken)) {
            log.debug("Token exchange refused: no subject token");
            return error(INVALID_REQUEST);
        }
        if (!ACCEPTED_SUBJECT_TOKEN_TYPES.contains(subjectTokenType)) {
            log.debug("Token exchange refused: subject token type {} is not supported", subjectTokenType);
            return error(INVALID_REQUEST);
        }
        if (Objects.nonNull(requestedTokenType)
                && !TokenTypeURI.JWT.getURI().toString().equals(requestedTokenType)) {
            log.debug("Token exchange refused: requested token type {} is not supported", requestedTokenType);
            return error(INVALID_REQUEST);
        }
        final OidcExchangedToken exchanged = tokenExchangeManager.exchange(subjectToken);
        return success(new TokenExchangeResponse(exchanged.getToken(),
                TokenTypeURI.JWT.getURI().toString(), BEARER_TOKEN_TYPE, exchanged.getExpiresIn()));
    }

    /**
     * The handlers below belong to this controller rather than to the shared advice, which answers
     * with the exception's own message. Those messages name users, claims and clients, and are
     * written for the log. A handler declared here is chosen before any advice, so nothing escapes
     * to it.
     */
    @ExceptionHandler(OidcClientNotAuthorizedException.class)
    public ResponseEntity<Object> onClientNotAuthorized(final OidcClientNotAuthorizedException e) {
        log.warn("Token exchange refused: {}", e.getMessage());
        return error(UNAUTHORIZED_CLIENT);
    }

    @ExceptionHandler(OidcTokenVerificationException.class)
    public ResponseEntity<Object> onSubjectTokenRejected(final OidcTokenVerificationException e) {
        log.warn("Token exchange refused: {}", e.getMessage());
        return error(INVALID_GRANT);
    }

    /**
     * A fault of ours rather than of the request, so it is not reported as a bad grant — that
     * would send an administrator looking for a problem at the identity provider.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> onUnexpectedFailure(final Exception e) {
        log.error("Token exchange failed unexpectedly", e);
        return noStore(HttpStatus.INTERNAL_SERVER_ERROR).body(new TokenExchangeError(SERVER_ERROR));
    }

    private ResponseEntity<Object> success(final TokenExchangeResponse body) {
        return noStore(HttpStatus.OK).body(body);
    }

    private ResponseEntity<Object> error(final String error) {
        return noStore(HttpStatus.BAD_REQUEST).body(new TokenExchangeError(error));
    }

    /**
     * A token must not be kept by a cache on the way back, as RFC 6749 section 5.1 requires.
     */
    private ResponseEntity.BodyBuilder noStore(final HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
