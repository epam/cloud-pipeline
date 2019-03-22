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

package com.epam.pipeline.manager.firecloud;

import com.epam.pipeline.entity.firecloud.FirecloudMethod;
import com.epam.pipeline.entity.firecloud.FirecloudMethodConfiguration;
import com.epam.pipeline.entity.firecloud.FirecloudMethodParameters;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class FirecloudApiService {
    private FirecloudManager firecloudManager;

    @PreAuthorize("hasRole('ADMIN') OR hasRole('FIRECLOUD_USER')")
    public List<FirecloudMethod> loadAll(String refreshToken) {
        return firecloudManager.getMethods(refreshToken);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasRole('FIRECLOUD_USER')")
    public List<FirecloudMethodConfiguration> loadMethodConfigurations(
            String refreshToken, String workspace, String methodName, Long snapshot) {
        return firecloudManager.getConfigurations(refreshToken, workspace, methodName, snapshot);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasRole('FIRECLOUD_USER')")
    public FirecloudMethodParameters loadMethodParameters(
            String refreshToken, String workspace, String methodName, Long snapshot) {
        return firecloudManager.getParameters(refreshToken, workspace, methodName, snapshot);
    }
}
