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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Api(value = "Run Configuration methods")
public class ConfigurationController extends AbstractRestController {

    @Autowired
    private RunConfigurationApiService configurationApiService;

    @PostMapping(value = "/configuration")
    @ResponseBody
    @ApiOperation(
            value = "Creates or updates run configuration.",
            notes = "Creates or updates run configuration.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
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
    @ApiOperation(
            value = "Deletes run configuration.",
            notes = "Deletes run configuration.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<RunConfiguration> deleteConfiguration(@PathVariable Long id) {
        return Result.success(configurationApiService.delete(id));
    }

    @GetMapping(value = "/configuration/{id}")
    @ResponseBody
    @ApiOperation(
            value = "Loads run configuration.",
            notes = "Loads run configuration.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<RunConfiguration> loadConfiguration(@PathVariable Long id) {
        return Result.success(configurationApiService.load(id));
    }

    @GetMapping(value = "/configuration/loadAll")
    @ResponseBody
    @ApiOperation(
            value = "Loads all run configurations.",
            notes = "Loads all run configurations.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunConfiguration>> loadAllConfigurations() {
        return Result.success(configurationApiService.loadAll());
    }
}
