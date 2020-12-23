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

package com.epam.pipeline.acl.cluster;

import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.manager.cluster.InfrastructureManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class InfrastructureApiService {

    private final InfrastructureManager infrastructureManager;


    @PreAuthorize(ADMIN_ONLY)
    public InstanceDNSRecord createInstanceDNSRecord(final Long regionId, final InstanceDNSRecord dnsRecord) {
        return infrastructureManager.createInstanceDNSRecord(regionId, dnsRecord);
    }

}
