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

package com.epam.pipeline.controller.metadata;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.acl.metadata.MetadataEntityApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@Tag(name = "MetadataEntities")
public class MetadataEntityController extends AbstractRestController {

    private static final String ID = "id";
    private static final String NAME = "name";

    @Autowired
    private MetadataEntityApiService metadataEntityApiService;

    @RequestMapping(value = "/metadataClass/register", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Registers a new user entity class.",
            description = "Registers a new user entity class.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataClass> registerMetadataClass(@RequestParam(value = NAME) final String entityName) {
        return Result.success(metadataEntityApiService.createMetadataClass(entityName));
    }

    @RequestMapping(value = "/metadataClass/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns all metadata classes.",
            description = "Returns all metadata classes.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataClass>> loadAllMetadataClasses() {
        return Result.success(metadataEntityApiService.loadAllMetadataClasses());
    }

    @RequestMapping(value = "/metadataClass/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata class, specified by id.",
            description = "Deletes a metadata class, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataClass> deleteMetadataClass(@PathVariable(value = ID) final Long id) {
        return Result.success(metadataEntityApiService.deleteMetadataClass(id));
    }

    @PostMapping(value = "/metadataClass/{id}/external")
    @ResponseBody
    @Operation(
            summary = "Updates a metadata external class, specified by metadata's id.",
            description = "Updates a metadata external class, specified by metadata's id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataClass> updateExternalClass(@PathVariable(value = ID) final Long id,
                                                     @RequestParam FireCloudClass externalClassName) {
        return Result.success(metadataEntityApiService.updateExternalClassName(id, externalClassName));
    }

    @RequestMapping(value = "/metadataEntity/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Registers a new user entity metadata.",
            description = "Registers a new user entity metadata.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> loadMetadataEntity(@PathVariable(value = ID) final Long id) {
        return Result.success(metadataEntityApiService.loadMetadataEntity(id));
    }

    @RequestMapping(value = "/metadataEntity/loadExternal", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads entity by externalID.",
            description = "Loads entity by externalID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> loadMetadataEntity(@RequestParam(value = ID) final String id,
            @RequestParam final String className, @RequestParam final Long folderId) {
        return Result.success(metadataEntityApiService.loadByExternalId(id, className, folderId));
    }

    @RequestMapping(value = "/metadataEntity/filter", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Filters and sorts metadata entities.",
            description = "Filters and sorts metadata entities.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<MetadataEntity>>> filterMetadataEntities(@RequestBody MetadataFilter filter) {
        return Result.success(metadataEntityApiService.filterMetadata(filter));
    }

    @RequestMapping(value = "/metadataEntity/upload", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Uploads metadata entities from a text file.",
            description = "Uploads metadata entities from a text file. "
                    + "Method accepts the following file formats: csv, tsv, tdf.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataEntity>> uploadMetadataFromFile(
            @RequestParam Long parentId,
            @RequestParam("file") MultipartFile file) throws FileUploadException {
        if (file.isEmpty()) {
            throw new FileUploadException("File is empty!");
        }
        return Result.success(metadataEntityApiService.uploadMetadataFromFile(parentId, file));
    }

    @RequestMapping(value = "/metadataEntity/keys", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns all present keys in a folder for a MetadataClass.",
            description = "Returns all present keys in a folder for a MetadataClass.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataField>> getMetadataKeys(@RequestParam Long folderId,
            @RequestParam String metadataClass) {
        return Result.success(metadataEntityApiService.getMetadataKeys(folderId, metadataClass));
    }

    @RequestMapping(value = "/metadataEntity/fields", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns all classes and keys in a folder recursively.",
            description = "Returns all classes and keys in a folder recursively.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Collection<MetadataClassDescription>> getMetadataFields(@RequestParam Long folderId) {
        return Result.success(metadataEntityApiService.getMetadataFields(folderId));
    }

    @RequestMapping(value = "/metadataEntity/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata entity, specified by id.",
            description = "Deletes a metadata entity, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> deleteMetadataEntity(@PathVariable(value = ID) final Long id) {
        return Result.success(metadataEntityApiService.deleteMetadataEntity(id));
    }

    @RequestMapping(value = "/metadataEntity/save", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Update user entity. If id not specified or not found a new one will be created",
            description = "Update user entity. If id not specified or not found a new one will be created.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> updateMetadataEntity(@RequestBody MetadataEntityVO metadataEntityVO) {
        MetadataEntity entity = metadataEntityVO.getEntityId() == null ?
                metadataEntityApiService.createMetadataEntity(metadataEntityVO) :
                metadataEntityApiService.updateMetadataEntity(metadataEntityVO);
        return Result.success(entity);
    }

    @RequestMapping(value = "/metadataEntity/updateKey", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Update metadata entity item key.",
            description = "Update metadata entity item key.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> updateMetadataEntityItemKey(@RequestBody MetadataEntityVO metadataEntityVO) {
        return Result.success(metadataEntityApiService.updateMetadataItemKey(metadataEntityVO));
    }

    @RequestMapping(value = "/metadataEntity/{id}/deleteKey", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a metadata entity key, specified by id and class.",
            description = "Deletes a metadata entity key, specified by id and class.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntity> deleteMetadataItemKey(@PathVariable(value = ID) Long id,
                                                       @RequestParam(value = "key") final String key) {
        return Result.success(metadataEntityApiService.deleteMetadataItemKey(id, key));
    }

    @DeleteMapping(value = "/metadataEntity/deleteList")
    @ResponseBody
    @Operation(
            summary = "Deletes a list of metadata entities.",
            description = "Deletes a list of metadata entities.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Set<Long>> deleteMetadataEntities(@RequestBody final Set<Long> entitiesIds) {
        return Result.success(metadataEntityApiService.deleteMetadataEntities(entitiesIds));
    }

    @DeleteMapping(value = "/metadataEntity/deleteFromProject")
    @ResponseBody
    @Operation(
            summary = "Deletes metadata entities from project.",
            description = "Deletes all metadata entities from project. " +
                    "If entityClass is provided, only entities of this class are deleted.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result deleteMetadataEntities(@RequestParam final Long projectId,
                                         @RequestParam(required = false) final String entityClass) {
        metadataEntityApiService.deleteMetadataFromProject(projectId, entityClass);
        return Result.success();
    }

    @PostMapping("/metadataEntity/entities")
    @ResponseBody
    @Operation(
            summary = "Loads specified metadata entities.",
            description = "Loads specified metadata entities.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> loadEntitiesData(@RequestBody Set<Long> entitiesIds) {
        return Result.success(metadataEntityApiService.loadEntitiesData(entitiesIds));
    }

    @GetMapping("/metadataEntity/download")
    @ResponseBody
    @Operation(
            summary = "Download specified metadata entity as a csv/tsv file.",
            description = "Download specified metadata entity as a csv/tsv file.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void downloadEntityAsFile(
            @RequestParam final Long folderId,
            @RequestParam final String entityClass,
            @RequestParam(required = false) final List<Long> entityIds,
            @RequestParam(required = false, defaultValue = "tsv") final String fileFormat,
            final HttpServletResponse response) throws IOException {
        final InputStream inputStream =
                metadataEntityApiService.getMetadataEntityFile(folderId, entityClass, entityIds, fileFormat);
        writeStreamToResponse(response, inputStream, String.format("%s.%s", entityClass, fileFormat));
    }

}
