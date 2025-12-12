/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.entity;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.acl.entity.EntityApiService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@Tag(name = "Entities")
public class EntityController extends AbstractRestController {

    @Autowired
    private EntityApiService entityApiService;

    @GetMapping(value = "entities")
    @ResponseBody
    @Operation(
            summary = "Loads entity by identifier.",
            description = "Loads entity by identifier.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractSecuredEntity> loadEntity(@RequestParam String identifier, @RequestParam AclClass aclClass) {
        return Result.success(entityApiService.loadByNameOrId(aclClass, identifier));
    }

    @PostMapping(value = "entities")
    @ResponseBody
    @Operation(
            summary = "Loads all entities with their permissions.",
            description = "Loads all entities with their permissions.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Map<AclClass, List<AbstractSecuredEntity>>> loadEntities(
            @RequestParam(required = false) final AclClass aclClass,
            @RequestBody final AclSid aclSid) {
        return Result.success(entityApiService.loadAvailable(aclSid, aclClass));
    }
}
