/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.resource;

import com.epam.pipeline.acl.resource.StaticResourceApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@Api(value = "Static resources API")
public class StaticResourcesController extends AbstractRestController {

    private static final FileNameMap FILE_NAME_MAP = URLConnection.getFileNameMap();
    private static final String STATIC_RESOURCES = "/static-resources/";
    private final StaticResourceApiService resourcesService;
    private final PreferenceManager preferenceManager;

    @GetMapping(value = "/static-resources/**")
    public void getStaticFile(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        final DataStorageStreamingContent content = resourcesService.getContent(
                request.getPathInfo().replaceFirst(STATIC_RESOURCES, ""));
        final String fileName = FilenameUtils.getName(content.getName());
        final MediaType mediaType = getMediaType(fileName);
        writeStreamToResponse(response, content.getContent(), fileName, mediaType,
                !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType));
    }

    private MediaType getMediaType(final String fileName) {
        final String[] supportedExtensions = preferenceManager.getPreference(
                SystemPreferences.UI_STORAGE_STATIC_PREVIEW_MASK).split(",");
        final String extension = FilenameUtils.getExtension(fileName);
        return Arrays.stream(supportedExtensions)
                .filter(ext -> ext.trim().equalsIgnoreCase(extension))
                .findFirst()
                .map(ext -> {
                    final String mimeType = FILE_NAME_MAP.getContentTypeFor(fileName);
                    return MediaType.parseMediaType(mimeType);
                })
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
