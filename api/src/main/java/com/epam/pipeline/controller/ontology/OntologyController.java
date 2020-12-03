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

package com.epam.pipeline.controller.ontology;

import com.epam.pipeline.acl.ontology.OntologyApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.dto.ontology.OntologyType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Ontologies management methods")
@RequestMapping(value = "/ontologies")
@RequiredArgsConstructor
public class OntologyController extends AbstractRestController {
    private final OntologyApiService ontologyApiService;

    @PostMapping
    @ApiOperation(value = "Creates ontology", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Ontology> create(@RequestBody final Ontology ontology) {
        return Result.success(ontologyApiService.create(ontology));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "Gets the ontology by identifier", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Ontology> get(@PathVariable final Long id) {
        return Result.success(ontologyApiService.get(id));
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "Updates ontology by id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Ontology> update(@PathVariable final Long id, @RequestBody final Ontology ontology) {
        return Result.success(ontologyApiService.update(id, ontology));
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "Deletes ontology", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Ontology> delete(@PathVariable final Long id) {
        return Result.success(ontologyApiService.delete(id));
    }

    @GetMapping("/tree")
    @ApiOperation(value = "Loads ontology tree by parent id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<Ontology>> getTree(@RequestParam final OntologyType type,
                                          @RequestParam(required = false) final Long parentId,
                                          @RequestParam(defaultValue = "1") final Integer depth) {
        return Result.success(ontologyApiService.getTree(type, parentId, depth));
    }

    @GetMapping("/externals")
    @ApiOperation(value = "Loads ontologies by external ids", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<Ontology>> getExternals(@RequestParam final List<String> externalIds) {
        return Result.success(ontologyApiService.getExternals(externalIds));
    }

    @GetMapping("/external")
    @ApiOperation(value = "Loads ontology by external id and parent id", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Ontology> getExternal(@RequestParam final String externalId,
                                        @RequestParam(required = false) final Long parentId) {
        return Result.success(ontologyApiService.getExternal(externalId, parentId));
    }
}
