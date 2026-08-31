/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.acl.auth.AccessApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.auth.AccessCode;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.access.UnsecuredAccessService;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "Methods to support applications (like pipe-cli) login via authorization code.")
@RequestMapping(value = "/access")
public class AccessController extends AbstractRestController {

    private final AccessApiService accessService;
    private final UnsecuredAccessService unsecuredAccessService;
    private final String successLoginHtml;

    public AccessController(final AccessApiService accessService,
                            final UnsecuredAccessService unsecuredAccessService,
                            @Value("${access.cli.success.login.page:/success-cli-login.html}")
                            final String successLoginHtml) {
        this.accessService = accessService;
        this.unsecuredAccessService = unsecuredAccessService;
        this.successLoginHtml = successLoginHtml;
    }

    @GetMapping("/auth")
    @ApiOperation(value = "Initiates user authentication.", produces = MediaType.TEXT_HTML_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public String auth(@RequestParam("code_challenge") final String codeChallenge,
                       @RequestParam("code_challenge_method") final CodeChallengeMethod codeChallengeMethod) {
        accessService.start(codeChallenge, codeChallengeMethod);
        // to support such redirection we cannot use @RestController class annotation here
        return String.format("redirect:%s", successLoginHtml);
    }

    @GetMapping("/code")
    @ApiOperation(value = "Returns code if exists.", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    @ResponseBody
    public Result<AccessCode> code(@RequestParam("code_challenge") final String codeChallenge) {
        return Result.success(unsecuredAccessService.findCode(codeChallenge));
    }

    @GetMapping("/token")
    @ApiOperation(value = "Exchanges the code for a token.", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    @ResponseBody
    public Result<JwtRawToken> token(@RequestParam("code") final String code,
                                     @RequestParam("code_verifier") final String codeVerifier) {
        return Result.success(unsecuredAccessService.exchangeCodeForToken(code, codeVerifier));
    }
}
