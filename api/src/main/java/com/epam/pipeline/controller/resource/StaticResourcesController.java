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
import com.epam.pipeline.entity.sharing.Modification;
import com.epam.pipeline.entity.sharing.StaticResourceSettings;
import com.epam.pipeline.exception.InvalidPathException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.util.ReplacingInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        try {
            final DataStorageStreamingContent content = resourcesService.getContent(
                    request.getPathInfo().replaceFirst(STATIC_RESOURCES, ""));
            final String fileName = FilenameUtils.getName(content.getName());
            final MediaType mediaType = getMediaType(fileName);
            final StaticResourceSettings settings = getStaticResourceSettings(fileName);

            writeStreamToResponse(response, getCustomContent(content.getContent(), settings), fileName, mediaType,
                    !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType), getCustomHeaders(settings));
        } catch (InvalidPathException e) {
            response.setHeader("Location", request.getRequestURI() + ProviderUtils.DELIMITER);
            response.setStatus(HttpStatus.FOUND.value());
        }
    }

    private StaticResourceSettings getStaticResourceSettings(final String fileName) {
        final Map<String, StaticResourceSettings> settings = preferenceManager.getPreference(
                SystemPreferences.DATA_SHARING_STATIC_RESOURCE_SETTINGS);
        if (settings == null) {
            return new StaticResourceSettings();
        }
        final String extension = FilenameUtils.getExtension(fileName);
        return settings.getOrDefault(extension, new StaticResourceSettings());
    }

    private Map<String, String> getCustomHeaders(final StaticResourceSettings settings) {
        return Optional.ofNullable(settings.getHeaders()).orElse(Collections.emptyMap());
    }

    private InputStream getCustomContent(final InputStream content,
                                         final StaticResourceSettings settings) {
        final List<Modification> modifications = Optional.ofNullable(settings.getModifications())
                .orElse(Collections.emptyList());
        return CollectionUtils.isNotEmpty(modifications) ? replace(content, modifications, 0) : content;
    }

    private InputStream replace(final InputStream content, final List<Modification> modifications, final int n) {
        if (n == modifications.size()) {
            return content;
        } else {
            Modification modification = modifications.get(n);
            return replace(new ReplacingInputStream(content,
                    modification.getPattern(), modification.getReplacement()), modifications, n+1);
        }
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
