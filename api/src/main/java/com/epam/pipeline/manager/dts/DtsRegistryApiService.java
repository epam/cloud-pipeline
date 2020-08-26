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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;

@Service
@AllArgsConstructor
public class DtsRegistryApiService {
    private DtsRegistryManager dtsRegistryManager;

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public List<DtsRegistry> loadAll() {
        return dtsRegistryManager.loadAll();
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public DtsRegistry load(Long registryId) {
        return dtsRegistryManager.load(registryId);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public DtsRegistry create(DtsRegistryVO dtsRegistryVO) {
        return dtsRegistryManager.create(dtsRegistryVO);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public DtsRegistry update(Long registryId, DtsRegistryVO dtsRegistryVO) {
        return dtsRegistryManager.update(registryId, dtsRegistryVO);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public DtsRegistry delete(Long registryId) {
        return dtsRegistryManager.delete(registryId);
    }
}
