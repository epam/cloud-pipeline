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

package com.epam.pipeline.manager.cloudaccess;

import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessPolicy;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class CloudAccessManagementFacadeImp implements CloudAccessManagementFacade {

    private final PreferenceManager preferenceManager;

    private Map<CloudProvider, CloudAccessManagementService> cloudAccessServices;

    @Autowired
    public void setCloudAccessServices(List<CloudAccessManagementService> services) {
        cloudAccessServices = services.stream()
                .collect(Collectors.toMap(CloudAwareService::getProvider, s -> s));
    }

    @Override
    public CloudUserAccessKeys generateKeys(String username, CloudUserAccessPolicy accessPolicy) {
        return null;
    }

    @Override
    public CloudUserAccessKeys updateKeys(String username, CloudUserAccessPolicy accessPolicy) {
        return null;
    }

    @Override
    public void revokeKeys(String username, String keysId) {

    }
}
