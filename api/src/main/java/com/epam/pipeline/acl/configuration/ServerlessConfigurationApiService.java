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

package com.epam.pipeline.acl.configuration;

import com.epam.pipeline.manager.configuration.ServerlessConfigurationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
public class ServerlessConfigurationApiService {

    private final ServerlessConfigurationManager serverlessConfigurationManager;

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#id, "
            + "'com.epam.pipeline.entity.configuration.RunConfiguration', 'READ')")
    public String generateUrl(final Long id, final String name) {
        return serverlessConfigurationManager.generateUrl(id, name);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    public String run(final Long id, final String configName, final HttpServletRequest request) {
        return serverlessConfigurationManager.run(id, configName, request);
    }
}
