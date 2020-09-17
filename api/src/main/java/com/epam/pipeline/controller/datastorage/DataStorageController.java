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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@Api(value = "Datastorage methods")
public class DataStorageController extends AbstractRestController {

    private static final String ID = "id";
    private static final String FROM_REGION = "fromRegion";
    private static final String PATH = "path";
    private static final String VERSION = "version";
    private static final String PIPELINE_ID = "pipelineId";
    private static final String CLOUD = "cloud";
    private static final String FALSE = "false";
    private static final int BYTES_IN_KB = 1024;
    private static final String NO_FILES_SPECIFIED = "No files specified";

    @Autowired
    private DataStorageApiService dataStorageApiService;

    @RequestMapping(value = "/datastorage/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all data storages.",
            notes = "Returns all data storages.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AbstractDataStorage>> getDataStorages() {
        return Result.success(dataStorageApiService.getDataStorages());
    }

    @RequestMapping(value = "/datastorage/available", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all data storages allowed for current user (READ or WRITE).",
            notes = "Returns all data storages allowed for current user (READ or WRITE).",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AbstractDataStorage>> getAvailableStorages() {
        return Result.success(dataStorageApiService.getAvailableStorages());
    }

    @RequestMapping(value = "/datastorage/availableWithMounts", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all data storages allowed for current user (READ or WRITE) and FileShareMount object." +
                    "If fronRegion is specified this method will return only allowed for mount" +
                    " storages for specified region.",
            notes = "Returns all data storages allowed for current user (READ or WRITE) and FileShareMount object." +
                    "If fronRegion is specified this method will return only allowed for mount " +
                    "storages for specified region.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DataStorageWithShareMount>> getAvailableStoragesWithMountObjects(
            @RequestParam(value = FROM_REGION, required = false) final Long regionId) {
        return Result.success(dataStorageApiService.getAvailableStoragesWithShareMount(regionId));
    }

    @RequestMapping(value = "/datastorage/mount", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all data storages that current user is allowed to mount.",
            notes = "Returns all data storages that current user is allowed to mount " +
                    "(user has READ and WRITE permissions).",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AbstractDataStorage>> getWritableDataStorages() {
        return Result.success(dataStorageApiService.getWritableStorages());
    }

    @RequestMapping(value = "/datastorage/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a data storage, specified by id.",
            notes = "Returns a data storage, specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> loadDataStorage(@PathVariable(value = ID) final Long id) {
        return Result.success(dataStorageApiService.load(id));
    }

    @RequestMapping(value = "/datastorage/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a datastorage, specified by ID or name.",
            notes = "Returns a datastorage, specified by ID or name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> findDataStorage(@RequestParam(value = ID) final String identifier) {
        return Result.success(dataStorageApiService.loadByNameOrId(identifier));
    }

    @RequestMapping(value = "/datastorage/findByPath", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a datastorage, specified by ID, name or one of path prefixes.",
            notes = "Returns a datastorage, specified by ID, name or one of path prefixes.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> findDataStorageByPath(@RequestParam(value = ID) final String identifier) {
        return Result.success(dataStorageApiService.loadByPathOrId(identifier));
    }

    @RequestMapping(value = "/datastorage/{id}/list", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage's items.",
            notes = "Returns data storage's items",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AbstractDataStorageItem>> getDataStorageItems(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(defaultValue = FALSE) final Boolean showVersion) {
        if (showVersion) {
            return Result.success(dataStorageApiService
                    .getDataStorageItemsOwner(id, path, showVersion, null, null).getResults());
        } else {
            return Result.success(dataStorageApiService
                    .getDataStorageItems(id, path, showVersion, null, null).getResults());
        }
    }

    @RequestMapping(value = "/datastorage/{id}/list/page", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a paged result with data storage's items.",
            notes = "Returns a paged with storage's items",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageListing> getDataStorageItems(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(defaultValue = FALSE) final Boolean showVersion,
            @RequestParam(required = false) final Integer pageSize,
            @RequestParam(required = false) final String marker) {
        if (showVersion) {
            return Result.success(dataStorageApiService
                    .getDataStorageItemsOwner(id, path, showVersion, pageSize, marker));
        } else {
            return Result.success(dataStorageApiService
                    .getDataStorageItems(id, path, showVersion, pageSize, marker));
        }
    }


    @RequestMapping(value = "/datastorage/{id}/list", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates or renames files or folders.",
            notes = "Creates or renames files or folders.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<AbstractDataStorageItem>> updateDataStorageItems(
            @PathVariable(value = ID) final Long id,
            @RequestBody List<UpdateDataStorageItemVO> items) {
        return Result.success(dataStorageApiService.updateDataStorageItems(id, items));
    }

    @RequestMapping(value = "/datastorage/{id}/list/upload", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Uploads a file to data storage.",
            notes = "Uploads a file to data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UploadFileMetadata> uploadFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = PATH, required = false) final String folder,
            HttpServletRequest request) throws FileUploadException {
        MultipartFile file = consumeMultipartFile(request);

        UploadFileMetadata fileMeta = new UploadFileMetadata();
        fileMeta.setFileName(FilenameUtils.getName(file.getOriginalFilename()));
        fileMeta.setFileSize(file.getSize() / BYTES_IN_KB + " Kb");
        fileMeta.setFileType(file.getContentType());
        try {
            dataStorageApiService.createDataStorageFile(id, folder, fileMeta.getFileName(), file.getBytes());
        } catch (IOException e) {
            throw new DataStorageException("Failed to upload file to datastorage.", e);
        }
        return Collections.singletonList(fileMeta);
    }

    @RequestMapping(value = "/datastorage/{id}/upload/stream", method= RequestMethod.POST)
    @ResponseBody
    public Result<List<DataStorageFile>> uploadStream(HttpServletRequest request,
                                        @PathVariable Long id,
                                        @RequestParam(value = PATH, required = false) String folder)
        throws IOException, FileUploadException {
        Assert.isTrue(ServletFileUpload.isMultipartContent(request), "Not a multipart request");

        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator iterator = upload.getItemIterator(request);

        Assert.isTrue(iterator.hasNext(), NO_FILES_SPECIFIED);
        boolean found = false;
        List<DataStorageFile> uploadedFiles = new ArrayList<>();
        while (iterator.hasNext()) {
            FileItemStream stream = iterator.next();
            if (!stream.isFormField()) {
                found = true;
                try (InputStream dataStream = stream.openStream()) { //TODO: try with Buffered streams
                    uploadedFiles.add(dataStorageApiService.createDataStorageFile(id, folder, stream.getName(),
                            dataStream));
                }
            }
        }

        Assert.isTrue(found, NO_FILES_SPECIFIED);

        return Result.success(uploadedFiles);
    }

    @RequestMapping(value = "/datastorage/{id}/download", method= RequestMethod.GET)
    public void downloadStream(HttpServletResponse response, @PathVariable Long id,
                               @RequestParam String path,
                               @RequestParam(value = VERSION, required = false) final String version)
        throws IOException {
        DataStorageStreamingContent content = dataStorageApiService.getStreamingContent(id, path, version);
        writeStreamToResponse(response, content.getContent(), content.getName());
    }

    @RequestMapping(value = "/datastorage/{id}/content", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Uploads a file represented as bytes to data storage.",
            notes = "Uploads a file represented as bytes to data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageFile> uploadStorageItem(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestBody String content) {
        return Result.success(dataStorageApiService.createDataStorageFile(id, path,
                content.getBytes(Charset.defaultCharset())));
    }

    @RequestMapping(value = "/datastorage/{id}/list", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes files or folders.",
            notes = "Deletes files or folders.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Integer> deleteDataStorageItems(
            @PathVariable(value = ID) final Long id,
            @RequestBody List<UpdateDataStorageItemVO> items,
            @RequestParam(defaultValue = FALSE) Boolean totally) {
        if (totally || (!CollectionUtils.isEmpty(items) && items.stream()
                .anyMatch(item -> StringUtils.hasText(item.getVersion())))) {
            return Result.success(dataStorageApiService.deleteDataStorageItemsOwner(id, items, totally));
        } else {
            return Result.success(dataStorageApiService.deleteDataStorageItems(id, items, totally));
        }
    }

    @RequestMapping(value = "/datastorage/{id}/downloadRedirect", method = RequestMethod.GET)
    @ApiOperation(
            value = "Generates item's download url and redirect to it.",
            notes = "Generates item's download url and redirect to it",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public String generateItemUrlAndRedirect(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestParam(required = false) final ContentDisposition contentDisposition) {
        final DataStorageDownloadFileUrl url = StringUtils.hasText(version) ?
                dataStorageApiService.generateDataStorageItemUrlOwner(id, path, version, contentDisposition) :
                dataStorageApiService.generateDataStorageItemUrl(id, path, version, contentDisposition);
        return String.format("redirect:%s", url.getUrl());
    }


    @RequestMapping(value = "/datastorage/{id}/generateUrl", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage item's download url.",
            notes = "Returns data storage item's download url",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageDownloadFileUrl> generateDataStorageItemUrl(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestParam(required = false) final ContentDisposition contentDisposition) {
        if (StringUtils.hasText(version)) {
            return Result.success(dataStorageApiService.generateDataStorageItemUrlOwner(id, path, version,
                    contentDisposition));
        } else {
            return Result.success(dataStorageApiService.generateDataStorageItemUrl(id, path, version,
                    contentDisposition));
        }
    }

    @GetMapping(value = "/datastorage/{id}/generateUploadUrl")
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage item's upload url.",
            notes = "Returns data storage item's upload url.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<DataStorageDownloadFileUrl> generateDataStorageItemUploadUrl(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path) {
        return Result.success(dataStorageApiService.generateDataStorageItemUploadUrl(id, path));
    }

    @RequestMapping(value = "/datastorage/{id}/content", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage item's download url.",
            notes = "Returns data storage item's download url",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageItemContent> getDataStorageItemContent(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestParam(value = VERSION, required = false) final String version) {
        if (StringUtils.hasText(version)) {
            return Result.success(dataStorageApiService.getDataStorageItemContentOwner(id, path, version));
        } else {
            return Result.success(dataStorageApiService.getDataStorageItemContent(id, path, version));
        }
    }

    @RequestMapping(value = "/datastorage/{id}/generateUrl", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage items download urls.",
            notes = "Returns data storage items download urls",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DataStorageDownloadFileUrl>> generateDataStorageItemsUrls(
            @PathVariable(value = ID) final Long id,
            @RequestBody final GenerateDownloadUrlVO generateDownloadUrlVO) {
        return Result.success(dataStorageApiService
                .generateDataStorageItemUrl(id, generateDownloadUrlVO.getPaths()));
    }

    @PostMapping("/datastorage/{id}/generateUploadUrl")
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage items upload urls.",
            notes = "Returns data storage items upload urls.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<DataStorageDownloadFileUrl>> generateDataStorageItemsUploadUrls(
            @PathVariable(value = ID) final Long id,
            @RequestBody final List<String> paths) {
        return Result.success(dataStorageApiService.generateDataStorageItemUploadUrl(id, paths));
    }

    @RequestMapping(value = "/datastorage/{id}/list/restore", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Restores file version.",
            notes = "Restores file version",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result restoreFileVersion(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestParam(value = VERSION, required = false) final String version) {
        dataStorageApiService.restoreFileVersion(id, path, version);
        return Result.success();
    }


    @RequestMapping(value = "/datastorage/save", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new data storage.",
            notes = "Registers a new data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<SecuredEntityWithAction<AbstractDataStorage>> registerDataStorage(
            @RequestBody DataStorageVO dataStorageVO,
            @RequestParam(value = CLOUD, defaultValue = FALSE) final Boolean proceedOnCloud,
            @RequestParam(value = "skipPolicy", defaultValue = FALSE) final boolean skipPolicy){
        return Result.success(dataStorageApiService.create(dataStorageVO, proceedOnCloud, skipPolicy));
    }

    @RequestMapping(value = "/datastorage/update", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update the given data storage.",
            notes = "It's possible to update name, description and parent folder",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> updateDataStorage(@RequestBody DataStorageVO dataStorageVO){
        return Result.success(dataStorageApiService.update(dataStorageVO));
    }

    @RequestMapping(value = "/datastorage/policy", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update the given data storage policy.",
            notes = "It's possible to update storage policy: versioning, backup, sts and lts duration",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> updateStoragePolicy(@RequestBody DataStorageVO dataStorageVO){
        return Result.success(dataStorageApiService.updatePolicy(dataStorageVO));
    }


    @RequestMapping(value = "/datastorage/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a data storage, specified by ID.",
            notes = "Deletes a data storage, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorage> deleteDataStorage(@PathVariable(value = ID) final Long id,
                                                         @RequestParam(value = CLOUD, defaultValue = FALSE)
                                                         final Boolean proceedOnCloud) {
        return Result.success(dataStorageApiService.delete(id, proceedOnCloud));
    }

    @RequestMapping(value = "/datastorage/rule/register", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new data storage rule.",
            notes = "Registers a new data storage rule.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageRule> saveDataStorageRule(@RequestBody DataStorageRule rule) {
        return Result.success(dataStorageApiService.createRule(rule));
    }

    @RequestMapping(value = "/datastorage/rule/load", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads a data storage rule specified by id.",
            notes = "Loads a data storage rule specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DataStorageRule>> loadDataStorageRule(
            @RequestParam(value = PIPELINE_ID, required = false) Long pipelineId,
            @RequestParam(value = "fileMask", required = false) String fileMask) {
        return Result.success(dataStorageApiService.loadRules(pipelineId, fileMask));
    }

    @RequestMapping(value = "/datastorage/rule/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a data storage rule specified by id.",
            notes = "Deletes a data storage rule specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageRule> deleteDataStorageRule(@RequestParam(value = ID) Long pipelineId,
            @RequestParam(value = "fileMask") String fileMask) {
        return Result.success(dataStorageApiService.deleteRule(pipelineId, fileMask));
    }

    @RequestMapping(value = "/datastorage/tempCredentials/", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Generate temporary credentials for bucket operations (cp/mv).",
            notes = "Generate temporary credentials for bucket operations (cp/mv).",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<TemporaryCredentials> generateTemporaryCredentials(
            @RequestBody List<DataStorageAction> operations) {
        dataStorageApiService.validateOperation(operations);
        return Result.success(dataStorageApiService.generateCredentials(operations));
    }

    @RequestMapping(value = "/datastorage/{id}/tags", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates or updates data storage item tags, by datastorage id and object path.",
            notes = "Creates or updates data storage item tags, by datastorage id and object path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> updateTags(@PathVariable(value = ID) final Long id,
                                                  @RequestParam(value = PATH) String path,
                                                  @RequestParam(value = VERSION, required = false) String version,
                                                  @RequestParam(defaultValue = FALSE) final Boolean rewrite,
                                                  @RequestBody final Map<String, String> tags) {
        return Result.success(dataStorageApiService.updateDataStorageObjectTags(id, path, tags, version, rewrite));
    }

    @RequestMapping(value = "/datastorage/{id}/tags", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage item tags, specified by datastorage id and object path.",
            notes = "Returns data storage item tags, specified by datastorage id and object path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> loadTagsById(@PathVariable(value = ID) final Long id,
                                                    @RequestParam(value = PATH) String path,
                                                    @RequestParam(value = VERSION, required = false) String version) {
        return StringUtils.hasText(version) ?
                Result.success(dataStorageApiService.loadDataStorageObjectTagsOwner(id, path, version)) :
                Result.success(dataStorageApiService.loadDataStorageObjectTags(id, path, version));
    }

    @DeleteMapping(value = "/datastorage/{id}/tags")
    @ResponseBody
    @ApiOperation(
            value = "Deletes data storage item tags, specified by datastorage id and object path.",
            notes = "Deletes data storage item tags, specified by datastorage id and object path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> deleteTagsById(@PathVariable(value = ID) final Long id,
                                                      @RequestParam(value = PATH) String path,
                                                      @RequestParam(value = VERSION, required = false) String version,
                                                      @RequestBody final Set<String> tags) {
        return Result.success(dataStorageApiService.deleteDataStorageObjectTags(id, path, tags, version));
    }

    @GetMapping(value = "/datastorage/{id}/tags/list")
    @ResponseBody
    @ApiOperation(
            value = "Returns data storage's item with tags, specified by datastorage id and object path.",
            notes = "Returns data storage's item with tags, specified by datastorage id and object path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AbstractDataStorageItem> getDataStorageItemsWithTags(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PATH) final String path,
            @RequestParam(defaultValue = FALSE) final Boolean showVersion) {
        return showVersion
                ? Result.success(dataStorageApiService.getDataStorageItemOwnerWithTags(id, path, showVersion))
                : Result.success(dataStorageApiService.getDataStorageItemWithTags(id, path, showVersion));
    }

    @GetMapping(value = "/datastorage/{id}/sharedLink")
    @ResponseBody
    @ApiOperation(
            value = "Returns shared link for the datastorage.",
            notes = "Returns shared link for the datastorage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<String> getDataStorageSharedLink(@PathVariable(value = ID) final Long id) {
        return Result.success(dataStorageApiService.getDataStorageSharedLink(id));
    }

    @GetMapping(value = "/datastorage/permission")
    @ResponseBody
    @ApiOperation(
            value = "Returns all data storages with permissions for all users.",
            notes = "Returns all data storages with permissions for all users.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<EntityWithPermissionVO> getStoragePermissions(
            @RequestParam final Integer page,
            @RequestParam final Integer pageSize,
            @RequestParam(required = false) final Integer filterMask) {
        return Result.success(dataStorageApiService.getStoragePermission(page, pageSize, filterMask));
    }

    @PostMapping(value = "/datastorage/path/size")
    @ResponseBody
    @ApiOperation(
            value = "Returns full size specified by path.",
            notes = "Returns full size specified by path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PathDescription>> getDataSizes(@RequestBody final List<String> paths) {
        return Result.success(dataStorageApiService.getDataSizes(paths));
    }

    @GetMapping(value = "/datastorage/path/usage")
    @ResponseBody
    @ApiOperation(
            value = "Returns storage usage statistics.",
            notes = "Returns storage usage statistics.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageUsage> getStorageUsage(@RequestParam final String id,
                                                @RequestParam(required = false) final String path) {
        return Result.success(dataStorageApiService.getStorageUsage(id, path));
    }

    @GetMapping(value = "/datastorage/sharedStorage")
    @ResponseBody
    @ApiOperation(
            value = "Returns storage to be used as shared folder of a Pipeline Run.",
            notes = "Returns storage to be used as shared folder of a Pipeline Run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DataStorageWithShareMount> getSharedFSStorage() {
        return Result.success(dataStorageApiService.getSharedFSStorage());
    }
}
