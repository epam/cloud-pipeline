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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequiredArgsConstructor
public class StaticResourcesController extends AbstractRestController {

    private static final String HTML_REPORTS = "/static-resources/";
    private final StaticResourceApiService resourcesService;

    @GetMapping(value = "/static-resources/**")
    public byte[] getStaticFile(final HttpServletRequest request) {
        return resourcesService.getContent(request.getPathInfo().replaceFirst(HTML_REPORTS, ""));
    }
}
