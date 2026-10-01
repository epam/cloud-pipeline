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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "ontology-controller", description = "Ontologies management methods")
@RequestMapping(value = "/ontologies")
@RequiredArgsConstructor
public class OntologyController extends AbstractRestController {
    private final OntologyApiService ontologyApiService;

    @PostMapping
    @Operation(summary = "Creates ontology")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Ontology> create(@RequestBody final Ontology ontology) {
        return Result.success(ontologyApiService.create(ontology));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Gets the ontology by identifier")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Ontology> get(@PathVariable final Long id) {
        return Result.success(ontologyApiService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Updates ontology by id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Ontology> update(@PathVariable final Long id, @RequestBody final Ontology ontology) {
        return Result.success(ontologyApiService.update(id, ontology));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletes ontology")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Ontology> delete(@PathVariable final Long id,
                                   @RequestParam(required = false) final boolean recursive) {
        return Result.success(ontologyApiService.delete(id, recursive));
    }

    @GetMapping("/tree")
    @Operation(summary = "Loads ontology tree by parent id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<Ontology>> getTree(@RequestParam final OntologyType type,
                                          @RequestParam(required = false) final Long parentId,
                                          @RequestParam(defaultValue = "1") final Integer depth) {
        return Result.success(ontologyApiService.getTree(type, parentId, depth));
    }

    @GetMapping("/externals")
    @Operation(summary = "Loads ontologies by external ids")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<Ontology>> getExternals(@RequestParam final List<String> externalIds) {
        return Result.success(ontologyApiService.getExternals(externalIds));
    }

    @GetMapping("/external")
    @Operation(summary = "Loads ontology by external id and parent id")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Ontology> getExternal(@RequestParam final String externalId,
                                        @RequestParam(required = false) final Long parentId) {
        return Result.success(ontologyApiService.getExternal(externalId, parentId));
    }
}
