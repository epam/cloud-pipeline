/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.metadata;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.acl.metadata.MetadataApiService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

//import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

@Controller
@Tag(name = "Metadata")
public class MetadataController extends AbstractRestController {

    @Autowired
    private MetadataApiService metadataApiService;

    @RequestMapping(value = "/metadata/updateKey", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Update metadata item key.",
            description = "Update metadata item key.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> updateMetadataItemKey(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItemKey(metadataVO));
    }

    @RequestMapping(value = "/metadata/updateKeys", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Update metadata item keys.",
            description = "Update metadata item keys.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> updateMetadataItemKeys(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItemKeys(metadataVO));
    }

    @RequestMapping(value = "/metadata/update", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Update metadata item.",
            description = "Update metadata item.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> updateMetadataItem(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItem(metadataVO));
    }

    @RequestMapping(value = "/metadata/load", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Returns a list of metadata, specified by id and class.",
            description = "Returns a list of metadata, specified by id and class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<MetadataEntry>> loadMetadataItems(@RequestBody List<EntityVO> entities) {
        return Result.success(metadataApiService.listMetadataItems(entities));
    }

    @RequestMapping(value = "/metadata/{key}/load", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Returns a list of metadata with only specific key, for entities specified by id and class.",
            description = "Returns a list of metadata with only specific key, for entities  specified by id and class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<MetadataEntry>> loadMetadataItems(@PathVariable(value = "key") final String key,
                                                         @RequestBody final List<EntityVO> entities) {
        return Result.success(metadataApiService.listMetadataItemsByKey(key, entities));
    }

    @RequestMapping(value = "/metadata/keys", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Get list of metadata keys for a class.",
            description = "Get list of metadata keys for a class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Set<String>> getMetadataKeys(@RequestParam final AclClass entityClass) {
        return Result.success(metadataApiService.getMetadataKeys(entityClass));
    }

    @RequestMapping(value = "/metadata/find", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns an entity, specified by name.",
            description = "Returns an entity, specified by name.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> findMetadataEntityIdByName(@RequestParam(value = "entityName")
                                                                final String entityName,
                                                            @RequestParam(value = "entityClass")
                                                                final AclClass entityClass) {
        return Result.success(metadataApiService.findMetadataEntityIdByName(entityName, entityClass));
    }

    @RequestMapping(value = "/metadata/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata, specified by id and class.",
            description = "Deletes a metadata, specified by id and class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> deleteMetadataItem(@RequestBody EntityVO entityVO) {
        return Result.success(metadataApiService.deleteMetadataItem(entityVO));
    }

    @RequestMapping(value = "/metadata/deleteKey", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata key, specified by id and class.",
            description = "Deletes a metadata key, specified by id and class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> deleteMetadataItemKey(@RequestBody EntityVO entityVO,
                                                       @RequestParam(value = "key") final String key) {
        return Result.success(metadataApiService.deleteMetadataItemKey(entityVO, key));
    }

    @RequestMapping(value = "/metadata/deleteKeys", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata keys, specified by id and class.",
            description = "Deletes a metadata keys, specified by id and class.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> deleteMetadataItemKeys(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.deleteMetadataItemKeys(metadataVO));
    }

    @PostMapping(value = "/metadata/upload")
    @ResponseBody
    @Operation(
            summary = "Uploads metadata from tsv/tdf or csv.",
            description = "Uploads metadata from tsv/tdf or csv.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<MetadataEntry> uploadMetadataFromFile(
            @RequestParam(value = "id") final Long entityId,
            @RequestParam(value = "class") final AclClass entityClass,
            @RequestParam(value = "merge", defaultValue = "false") final boolean mergeWithExistingMetadata,
            final HttpServletRequest request
    ) throws FileUploadException, java.io.IOException {
        final var file = consumeMultipartFile(request);
        if (file.isEmpty()) {
            throw new FileUploadException("File is empty");
        }
        return Result.success(metadataApiService.uploadMetadataFromFile(new EntityVO(entityId, entityClass),
                file, mergeWithExistingMetadata));
    }

    @GetMapping(value = "/metadata/folder")
    @ResponseBody
    @Operation(
            summary = "Loads metadata for all entities in folder.",
            description = "Loads metadata for all entities in folder.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<MetadataEntryWithIssuesCount>> loadEntitiesMetadataFromFolder(
            @RequestParam(required = false) final Long parentFolderId) {
        return Result.success(metadataApiService.loadEntitiesMetadataFromFolder(parentFolderId));
    }

    @GetMapping(value = "/metadata/search")
    @ResponseBody
    @Operation(
            summary = "Loads metadata by entity class and key-value pair. Value is not required.",
            description = "Loads metadata by entity class and key-value pair. Value is not required.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<EntityVO>> searchMetadataByClassAndKeyValue(@RequestParam final AclClass entityClass,
                                                                   @RequestParam final String key,
                                                                   @RequestParam(required = false) final String value) {
        return Result.success(metadataApiService.searchMetadataByClassAndKeyValue(entityClass, key, value));
    }

    @GetMapping(value = "/metadata/search/entry")
    @ResponseBody
    @Operation(
            summary = "Loads entity and its metadata by entity class and key-value pair. Value is not required.",
            description = "Loads entity and its metadata by entity class and key-value pair. Value is not required.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<MetadataEntry>> searchMetadataEntriesByClassAndKeyValue(
            @RequestParam final AclClass entityClass, @RequestParam final String key,
            @RequestParam(required = false) final String value) {
        return Result.success(metadataApiService.searchMetadataEntriesByClassAndKeyValue(entityClass, key, value));
    }

    @PostMapping("/metadata/sync/categoricalAttributes")
    @Operation(
        summary = "Fill in categorical attributes table based on a current metadata.",
        description = "Fill in categorical attributes table based on a current metadata.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public void syncCategoricalAttributesWithMetadata() {
        metadataApiService.syncWithCategoricalAttributes();
    }
}
