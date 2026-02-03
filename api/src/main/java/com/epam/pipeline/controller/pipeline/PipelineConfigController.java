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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.acl.pipeline.PipelineConfigApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Tag(name = "Pipeline Configurations")
public class PipelineConfigController extends AbstractRestController {

    private static final String ID = "id";
    private static final String VERSION = "version";

    @Autowired
    private PipelineConfigApiService configApiService;

    @RequestMapping(value = "/pipeline/{id}/configurations", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets list of pipeline version configurations.",
            description = "Gets list of pipeline version configurations along with default values.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ConfigurationEntry>> getPipelineConfigurations(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version) throws GitClientException {
        return Result.success(configApiService.loadConfigurations(id, version));
    }

    @RequestMapping(value = "/pipeline/{id}/configurations", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Creates a new pipeline configuration.",
            description = "Creates a new pipeline configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ConfigurationEntry>> createPipelineConfiguration(
            @PathVariable(value = ID) Long id, @RequestBody ConfigurationEntry configuration)
            throws GitClientException {
        return Result.success(configApiService.addConfiguration(id, configuration));
    }

    @RequestMapping(value = "/pipeline/{id}/configurations/rename", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Renames a pipeline configuration.",
            description = "Renames a pipeline configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ConfigurationEntry>> createPipelineConfiguration(
            @PathVariable(value = ID) Long id, @RequestParam String oldName, @RequestParam String newName)
            throws GitClientException {
        return Result.success(configApiService.renameConfiguration(id, oldName, newName));
    }

    @RequestMapping(value = "/pipeline/{id}/configurations", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a pipeline configuration.",
            description = "Deletes a pipeline configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ConfigurationEntry>> deletePipelineConfiguration(
            @PathVariable(value = ID) Long id, @RequestParam String configName)
            throws GitClientException {
        return Result.success(configApiService.deleteConfiguration(id, configName));
    }

    @RequestMapping(value = "/pipeline/{id}/parameters", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets list of pipeline version parameters.",
            description = "Gets list of pipeline version parameters along with default values.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineConfiguration> getPipelineParameters(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(value = "name", required = false) final String configName) throws GitClientException {
        return Result.success(configApiService.loadParametersFromScript(id, version, configName));
    }

    @RequestMapping(value = "/pipeline/{id}/language", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Return language for given pipeline id",
            description = "Return language for given pipeline id")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<String> getPipelineLanguage(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(value = "name", required = false) final String configName) throws GitClientException {
        return Result.success(configApiService.loadParametersFromScript(id, version, configName).getLanguage());
    }
}
