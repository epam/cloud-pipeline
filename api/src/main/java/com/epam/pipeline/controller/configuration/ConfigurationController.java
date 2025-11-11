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

package com.epam.pipeline.controller.configuration;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.acl.configuration.RunConfigurationApiService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Tag(name = "Run Configuration methods")
public class ConfigurationController extends AbstractRestController {

    @Autowired
    private RunConfigurationApiService configurationApiService;

    @PostMapping(value = "/configuration")
    @ResponseBody
    @Operation(
            summary = "Creates or updates run configuration.",
            description = "Creates or updates run configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<RunConfiguration> saveConfiguration(
            @RequestBody RunConfigurationVO configuration) {
        if (configuration.getId() != null) {
            return Result.success(configurationApiService.update(configuration));
        } else {
            return Result.success(configurationApiService.save(configuration));
        }
    }

    @DeleteMapping(value = "/configuration/{id}")
    @ResponseBody
    @Operation(
            summary = "Deletes run configuration.",
            description = "Deletes run configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<RunConfiguration> deleteConfiguration(@PathVariable Long id) {
        return Result.success(configurationApiService.delete(id));
    }

    @GetMapping(value = "/configuration/{id}")
    @ResponseBody
    @Operation(
            summary = "Loads run configuration.",
            description = "Loads run configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<RunConfiguration> loadConfiguration(@PathVariable Long id) {
        return Result.success(configurationApiService.load(id));
    }

    @GetMapping(value = "/configuration/loadAll")
    @ResponseBody
    @Operation(
            summary = "Loads all run configurations.",
            description = "Loads all run configurations.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunConfiguration>> loadAllConfigurations() {
        return Result.success(configurationApiService.loadAll());
    }
}
