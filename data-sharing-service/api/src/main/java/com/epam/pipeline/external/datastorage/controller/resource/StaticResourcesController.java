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

package com.epam.pipeline.external.datastorage.controller.resource;

import com.epam.pipeline.external.datastorage.controller.AbstractRestController;
import com.epam.pipeline.external.datastorage.manager.preference.PreferenceService;
import com.epam.pipeline.external.datastorage.manager.resource.StaticResourcesService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Arrays;

@RestController
@Api(value = "Static resources API")
@RequestMapping("/static-resources")
@RequiredArgsConstructor
public class StaticResourcesController extends AbstractRestController {

    private static final FileNameMap FILE_NAME_MAP = URLConnection.getFileNameMap();
    private static final String STATIC_RESOURCES = "/static-resources/";
    private static final String UI_STORAGE_STATIC_PREVIEW_MASK = "ui.storage.static.preview.mask";

    private final StaticResourcesService resourcesService;
    private final PreferenceService preferenceService;

    @GetMapping(value = "/**")
    public void getStaticFile(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        final String path = request.getPathInfo().replaceFirst(STATIC_RESOURCES, "");
        final String fileName = FilenameUtils.getName(path);
        final MediaType mediaType = getMediaType(fileName);
        final InputStream content = resourcesService.getContent(path);
        writeStreamToResponse(response, content, fileName, mediaType,
                !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType));
    }

    private MediaType getMediaType(final String fileName) {
        final String preference = preferenceService.getValue(UI_STORAGE_STATIC_PREVIEW_MASK);
        if (StringUtils.isBlank(preference)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        final String[] supportedExtensions =preference.split(",");
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
