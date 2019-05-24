/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epam.pipeline.external.datastorage.entity.datastorage.DataStorage;
import com.epam.pipeline.external.datastorage.entity.credentials.AbstractTemporaryCredentials;
import com.epam.pipeline.external.datastorage.entity.credentials.DataStorageAction;
import com.epam.pipeline.external.datastorage.entity.item.AbstractDataStorageItem;
import com.epam.pipeline.external.datastorage.entity.item.DataStorageDownloadFileUrl;
import com.epam.pipeline.external.datastorage.entity.item.DataStorageItemContent;
import com.epam.pipeline.external.datastorage.entity.item.DataStorageListing;
import com.epam.pipeline.external.datastorage.entity.item.GenerateDownloadUrlVO;
import com.epam.pipeline.external.datastorage.entity.item.UpdateDataStorageItemVO;
import com.epam.pipeline.external.datastorage.manager.datastorage.DataStorageManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@Api(value = "Datastorage API")
public class DataStorageController {

    private static final String FALSE = "false";
    private final DataStorageManager dataStorageManager;

    @Autowired
    public DataStorageController(DataStorageManager dataStorageManager) {
        this.dataStorageManager = dataStorageManager;
    }

    @GetMapping(value = "/datastorage/{id}/load")
    @ApiOperation(
        value = "Returns a data storage, specified by id.",
        notes = "Returns a data storage, specified by id.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<DataStorage> loadDataStorage(@PathVariable final Long id) {
        return Result.success(dataStorageManager.loadStorage(id));
    }

    @GetMapping("/datastorage/{id}/list")
    public Result<List<AbstractDataStorageItem>> list(@PathVariable long id,
                                                      @RequestParam(defaultValue = FALSE) Boolean showVersion,
                                                      @RequestParam(required = false) String path) {
        return Result.success(dataStorageManager.listStorage(id, path, showVersion));
    }

    @GetMapping("/datastorage/{id}/list/page")
    public Result<DataStorageListing> list(@PathVariable long id,
                                           @RequestParam(required = false) final String path,
                                           @RequestParam(defaultValue = FALSE) final Boolean showVersion,
                                           @RequestParam(required = false) final Integer pageSize,
                                           @RequestParam(required = false) final String marker) {
        return Result.success(dataStorageManager.listStorage(id, path, showVersion, pageSize, marker));
    }

    @PostMapping(value = "/datastorage/{id}/list")
    public Result<List<AbstractDataStorageItem>> updateDataStorageItems(
                                                            @PathVariable long id,
                                                            @RequestBody List<UpdateDataStorageItemVO> items) {
        return Result.success(dataStorageManager.updateDataStorageItems(id, items));
    }

    @DeleteMapping(value = "/datastorage/{id}/list")
    public Result<Integer> deleteDataStorageItems(@PathVariable long id,
                                                  @RequestBody List<UpdateDataStorageItemVO> items,
            @RequestParam(defaultValue = FALSE) boolean totally) {
        return Result.success(dataStorageManager.deleteDataStorageItems(id, items, totally));
    }

    @GetMapping(value = "/datastorage/{id}/generateUrl")
    public Result<DataStorageDownloadFileUrl> generateDataStorageItemUrl(
            @PathVariable long id,
            @RequestParam final String path,
            @RequestParam(required = false) final String version) {
        return Result.success(dataStorageManager.generateDownloadUrl(id, path, version));
    }

    @PostMapping(value = "/datastorage/{id}/generateUrl")
    public Result<List<DataStorageDownloadFileUrl>> generateDataStorageItemsUrls(
            @PathVariable long id,
            @RequestBody final GenerateDownloadUrlVO generateDownloadUrlVO) {
        return Result.success(dataStorageManager.generateDataStorageItemUrls(id, generateDownloadUrlVO));
    }

    @GetMapping("/datastorage/{id}/generateUploadUrl")
    public Result<DataStorageDownloadFileUrl> generateUploadUrl(@PathVariable final long id,
                                                                @RequestParam final String path) {
        return Result.success(dataStorageManager.generateUploadUrl(id, path));
    }

    @GetMapping(value = "/datastorage/{id}/item/content")
    public Result<DataStorageItemContent> downloadFile(@PathVariable long id,
                                                       @RequestParam final String path,
                                                       @RequestParam(required = false) final String version) {
        return Result.success(dataStorageManager.downloadItem(id, path, version));
    }

    @GetMapping("/datastorage/{id}/item/tags")
    public Result<AbstractDataStorageItem> getDataStorageItemsWithTags(@PathVariable long id,
                                                                       @RequestParam final String path,
            @RequestParam(defaultValue = FALSE) final Boolean showVersion) {
        return Result.success(dataStorageManager.getItemWithTags(id, path, showVersion));
    }

    @GetMapping(value = "/datastorage/{id}/tags")
    public Result<Map<String, String>> loadTags(@PathVariable long id,
                                                @RequestParam String path,
                                                @RequestParam(required = false) String version) {
        return Result.success(dataStorageManager.getItemTags(id, path, version));
    }

    @DeleteMapping(value = "/datastorage/{id}/tags")
    public Result<Map<String, String>> deleteTags(@PathVariable long id,
                                                  @RequestParam String path,
                                                  @RequestParam(required = false) String version,
                                                  @RequestBody final Set<String> tags) {
        return Result.success(dataStorageManager.deleteItemTags(id, path, tags, version));
    }

    @PostMapping(value = "/datastorage/{id}/tags")
    public Result<Map<String, String>> updateTags(@PathVariable long id,
                                                  @RequestParam String path,
                                                  @RequestParam(required = false) String version,
                                                  @RequestParam(defaultValue = FALSE) final Boolean rewrite,
                                                  @RequestBody final Map<String, String> tags) {
        return Result.success(dataStorageManager.updateItemsTags(id, path, tags, version, rewrite));
    }

    @PostMapping(value = "/datastorage/{id}/tempCredentials")
    public Result<AbstractTemporaryCredentials> generateTemporaryCredentials(
            @PathVariable long id,
            @RequestBody List<DataStorageAction> operations) {
        return Result.success(dataStorageManager.generateCredentials(id, operations));
    }

    protected void writeStreamToResponse(HttpServletResponse response, InputStream stream, String contentDisposition)
            throws IOException {
        try (InputStream myStream = stream) {
            // Set the content type and attachment header.
            response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

            // Copy the stream to the response's output stream.
            IOUtils.copy(myStream, response.getOutputStream());
            response.flushBuffer();
        }
    }
}
