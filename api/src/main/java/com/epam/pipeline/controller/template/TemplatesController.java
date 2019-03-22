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

package com.epam.pipeline.controller.template;

import java.util.Collection;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.manager.template.TemplateManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Api(value = "Templates")
public class TemplatesController extends AbstractRestController {

    @Autowired
    private TemplateManager templateManager;

    @RequestMapping(value = "/templates/list", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "List available templates.",
            notes = "List available templates.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Collection<Template>> listTemplates() {
        return Result.success(templateManager.getPipelineTemplates());
    }

    @RequestMapping(value = "/templates/folder/list", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Lists available folder templates.",
            notes = "Lists available folder templates.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Collection<Template>> listFolderTemplates() {
        return Result.success(templateManager.getFolderTemplates());
    }
}
