/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.dts;

import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesUpdateVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.manager.dts.DtsRegistryManager;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.DTS_REGISTRY_PERMISSIONS;

@Service
@AllArgsConstructor
public class DtsRegistryApiService {
    private DtsRegistryManager dtsRegistryManager;

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public List<DtsRegistry> loadAll() {
        return dtsRegistryManager.loadAll();
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry loadByNameOrId(final String registryId) {
        return dtsRegistryManager.loadByNameOrId(registryId);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry create(DtsRegistryVO dtsRegistryVO) {
        return dtsRegistryManager.create(dtsRegistryVO);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry update(final String registryId, final DtsRegistryVO dtsRegistryVO) {
        return dtsRegistryManager.update(registryId, dtsRegistryVO);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry updateHeartbeat(final String registryId) {
        return dtsRegistryManager.updateHeartbeat(registryId);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry delete(final String registryId) {
        return dtsRegistryManager.delete(registryId);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry upsertPreferences(final String registryId, final DtsRegistryPreferencesUpdateVO preferencesVO) {
        return dtsRegistryManager.upsertPreferences(registryId, preferencesVO);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public DtsRegistry deletePreferences(final String registryId, final DtsRegistryPreferencesRemovalVO preferencesVO) {
        return dtsRegistryManager.deletePreferences(registryId, preferencesVO);
    }
}
