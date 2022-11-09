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
import com.epam.pipeline.external.datastorage.exception.InvalidPathException;
import com.epam.pipeline.external.datastorage.manager.preference.PreferenceService;
import com.epam.pipeline.external.datastorage.manager.resource.StaticResourcesService;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import okhttp3.Headers;
import okhttp3.ResponseBody;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import retrofit2.Response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@Api(value = "Static resources API")
@RequestMapping("/static-resources")
@RequiredArgsConstructor
public class StaticResourcesController extends AbstractRestController {

    private static final String STATIC_RESOURCES = "/static-resources/";
    private static final String DATA_SHARING_STATIC_RESOURCE_HEADERS = "data.sharing.static.resource.headers";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE = "Content-Type";

    private final StaticResourcesService resourcesService;
    private final PreferenceService preferenceService;

    @GetMapping(value = "/**")
    public void getStaticFile(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        try {
            final String path = request.getPathInfo().replaceFirst(STATIC_RESOURCES, "");
            final Response<ResponseBody> content = resourcesService.getContent(path);
            final String fileName = FilenameUtils.getName(path);

            final Headers responseHeaders = content.headers();
            final String contentDisposition = responseHeaders.get(CONTENT_DISPOSITION);
            final MediaType mediaType = MediaType.parseMediaType(responseHeaders.get(CONTENT_TYPE));
            final Map<String, String> headers = getCustomHeaders(fileName);

            headers.put(CONTENT_DISPOSITION, contentDisposition);
            headers.put(CONTENT_TYPE, mediaType.getType());
            writeStreamToResponse(response, content.body().byteStream(), fileName, mediaType,
                    !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType), getCustomHeaders(fileName));
        } catch (InvalidPathException e) {
            response.setHeader("Location", request.getRequestURI() + "/");
            response.setStatus(HttpStatus.FOUND.value());
        }
    }

    private Map<String, String> getCustomHeaders(final String fileName) {
        final Map<String, Map<String, String>> headers =
                preferenceService.getValue(DATA_SHARING_STATIC_RESOURCE_HEADERS,
                        new TypeReference<Map<String, Map<String, String>>>() {});
        final String extension = FilenameUtils.getExtension(fileName);
        if (MapUtils.isEmpty(headers) || !headers.containsKey(extension)) {
            return new HashMap<>();
        }
        return new HashMap<>(headers.get(extension));
    }
}
