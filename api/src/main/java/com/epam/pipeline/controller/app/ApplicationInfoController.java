/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.app;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.app.ApplicationInfo;
import com.epam.pipeline.manager.app.ApplicationInfoManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Application info methods")
@RequestMapping(value = "/app")
@RequiredArgsConstructor
public class ApplicationInfoController extends AbstractRestController {

    private final ApplicationInfoManager applicationInfoManager;

    @GetMapping(value = "/info")
    @Operation(
            summary = "Returns an application version info",
            description = "Returns an application version info"
        )
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<ApplicationInfo> getInfo() {
        return Result.success(applicationInfoManager.getInfo());
    }
}
