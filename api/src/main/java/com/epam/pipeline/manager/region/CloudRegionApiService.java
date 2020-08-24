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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CloudRegionApiService {

    private final CloudRegionManager cloudRegionManager;

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject,'READ')")
    public List<? extends AbstractCloudRegion> loadAll() {
        return cloudRegionManager.loadAll();
    }

    @PreAuthorize(AclExpressions.FULL_BILLING_ACCESS)
    public List<? extends AbstractCloudRegion> loadAllForBilling() {
        return cloudRegionManager.loadAllForBilling();
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    public AbstractCloudRegion load(final Long id) {
        return cloudRegionManager.load(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AbstractCloudRegion create(final AbstractCloudRegionDTO region) {
        return cloudRegionManager.create(region);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AbstractCloudRegion update(final Long id, final AbstractCloudRegionDTO region) {
        return cloudRegionManager.update(id, region);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AbstractCloudRegion delete(final Long id) {
        return cloudRegionManager.delete(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<String> loadAllAvailable(CloudProvider provider) {
        return cloudRegionManager.loadAllAvailable(provider);
    }

    public List<CloudProvider> loadProviders() {
        return cloudRegionManager.loadProviders();
    }
}
