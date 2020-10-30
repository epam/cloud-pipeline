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
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.fileupload.FileUploadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

@Controller
@Api(value = "Metadata")
public class MetadataController extends AbstractRestController {

    @Autowired
    private MetadataApiService metadataApiService;

    @RequestMapping(value = "/metadata/updateKey", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update metadata item key.",
            notes = "Update metadata item key.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> updateMetadataItemKey(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItemKey(metadataVO));
    }

    @RequestMapping(value = "/metadata/updateKeys", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update metadata item keys.",
            notes = "Update metadata item keys.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> updateMetadataItemKeys(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItemKeys(metadataVO));
    }

    @RequestMapping(value = "/metadata/update", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update metadata item.",
            notes = "Update metadata item.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> updateMetadataItem(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.updateMetadataItem(metadataVO));
    }

    @RequestMapping(value = "/metadata/load", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Returns a list of metadata, specified by id and class.",
            notes = "Returns a list of metadata, specified by id and class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataEntry>> loadMetadataItems(@RequestBody List<EntityVO> entities) {
        return Result.success(metadataApiService.listMetadataItems(entities));
    }

    @RequestMapping(value = "/metadata/keys", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Get list of metadata keys for a class.",
            notes = "Get list of metadata keys for a class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Set<String>> getMetadataKeys(@RequestParam final AclClass entityClass) {
        return Result.success(metadataApiService.getMetadataKeys(entityClass));
    }

    @RequestMapping(value = "/metadata/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns an entity, specified by name.",
            notes = "Returns an entity, specified by name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> findMetadataEntityIdByName(@RequestParam(value = "entityName")
                                                                final String entityName,
                                                            @RequestParam(value = "entityClass")
                                                                final AclClass entityClass) {
        return Result.success(metadataApiService.findMetadataEntityIdByName(entityName, entityClass));
    }

    @RequestMapping(value = "/metadata/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a metadata, specified by id and class.",
            notes = "Deletes a metadata, specified by id and class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> deleteMetadataItem(@RequestBody EntityVO entityVO) {
        return Result.success(metadataApiService.deleteMetadataItem(entityVO));
    }

    @RequestMapping(value = "/metadata/deleteKey", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a metadata key, specified by id and class.",
            notes = "Deletes a metadata key, specified by id and class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> deleteMetadataItemKey(@RequestBody EntityVO entityVO,
                                                       @RequestParam(value = "key") final String key) {
        return Result.success(metadataApiService.deleteMetadataItemKey(entityVO, key));
    }

    @RequestMapping(value = "/metadata/deleteKeys", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a metadata keys, specified by id and class.",
            notes = "Deletes a metadata keys, specified by id and class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> deleteMetadataItemKeys(@RequestBody MetadataVO metadataVO) {
        return Result.success(metadataApiService.deleteMetadataItemKeys(metadataVO));
    }

    @PostMapping(value = "/metadata/upload")
    @ResponseBody
    @ApiOperation(
            value = "Uploads metadata from tsv/tdf or csv.",
            notes = "Uploads metadata from tsv/tdf or csv.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<MetadataEntry> uploadMetadataFromFile(
            @RequestParam(value = "id") final Long entityId,
            @RequestParam(value = "class") final AclClass entityClass,
            @RequestParam(value = "merge", defaultValue = "false") final boolean mergeWithExistingMetadata,
            final HttpServletRequest request
    ) throws FileUploadException {
        final MultipartFile file = consumeMultipartFile(request);
        return Result.success(metadataApiService.uploadMetadataFromFile(new EntityVO(entityId, entityClass),
                file, mergeWithExistingMetadata));
    }

    @GetMapping(value = "/metadata/folder")
    @ResponseBody
    @ApiOperation(
            value = "Loads metadata for all entities in folder.",
            notes = "Loads metadata for all entities in folder.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataEntryWithIssuesCount>> loadEntitiesMetadataFromFolder(
            @RequestParam(required = false) final Long parentFolderId) {
        return Result.success(metadataApiService.loadEntitiesMetadataFromFolder(parentFolderId));
    }

    @GetMapping(value = "/metadata/search")
    @ResponseBody
    @ApiOperation(
            value = "Loads metadata by entity class and key-value pair.",
            notes = "Loads metadata by entity class and key-value pair.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<EntityVO>> searchMetadataByClassAndKeyValue(@RequestParam final AclClass entityClass,
                                                                   @RequestParam final String key,
                                                                   @RequestParam final String value) {
        return Result.success(metadataApiService.searchMetadataByClassAndKeyValue(entityClass, key, value));
    }

}
