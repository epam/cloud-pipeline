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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.controller.vo.AwsRegionVO;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AwsRegionApiService {

    private final AwsRegionManager awsRegionManager;

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject,'READ')")
    public List<AwsRegion> loadAll() {
        return awsRegionManager.loadAll();
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    public AwsRegion load(final Long id) {
        return awsRegionManager.load(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AwsRegion create(final AwsRegionVO region) {
        return awsRegionManager.create(region);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AwsRegion update(final Long id, final AwsRegionVO region) {
        return awsRegionManager.update(id, region);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AwsRegion delete(final Long id) {
        return awsRegionManager.delete(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<String> loadAllAvailable() {
        return awsRegionManager.loadAllAvailable();
    }
}
