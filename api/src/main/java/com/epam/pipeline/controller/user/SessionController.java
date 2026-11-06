/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.user;

import com.epam.pipeline.acl.session.PipelineSessionService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequiredArgsConstructor
@Tag(name = "session-controller", description = "Session API")
public class SessionController extends AbstractRestController {

    private final PipelineSessionService pipelineSessionService;

    @GetMapping(value = "/session")
    @Operation(summary = "Starts or prolongs an HTTP session for the current user.",
                description = "Starts or prolongs an HTTP session for the current user.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<String> startSession(final HttpSession session,
                                       final @RequestParam(required = false, defaultValue = "1800") Integer duration) {
        final SecurityContext context = SecurityContextHolder.getContext();
        final Authentication credentials = context.getAuthentication();
        if (credentials instanceof JwtAuthenticationToken) {
            ((JwtAuthenticationToken) credentials).prolong(duration);
        }
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return Result.success(session.getId());
    }

    @DeleteMapping(value = "/session")
    @Operation(
            summary = "Terminates active HTTP sessions for a user or group.",
            description = "Deletes Spring Session entries for the given identity. "
                    + "When principal is false, the name is treated as a group.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Void> deleteSession(@RequestBody final SidImpl sid) {
        pipelineSessionService.invalidateSession(sid);
        return Result.success();
    }
}
