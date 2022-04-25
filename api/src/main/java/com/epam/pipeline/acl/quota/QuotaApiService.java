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

package com.epam.pipeline.acl.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.manager.quota.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_BILLING_MANAGER;

@Service
@RequiredArgsConstructor
public class QuotaApiService {
    private final QuotaService quotaService;

    @PreAuthorize(ADMIN_OR_BILLING_MANAGER)
    public Quota create(final Quota quota) {
        return quotaService.create(quota);
    }

    @PreAuthorize(ADMIN_OR_BILLING_MANAGER)
    public Quota get(final Long id) {
        return quotaService.get(id);
    }

    @PreAuthorize(ADMIN_OR_BILLING_MANAGER)
    public Quota update(final Long id, final Quota quota) {
        return quotaService.update(id, quota);
    }

    @PreAuthorize(ADMIN_OR_BILLING_MANAGER)
    public void delete(final Long id) {
        quotaService.delete(id);
    }

    @PreAuthorize(ADMIN_OR_BILLING_MANAGER)
    public List<Quota> getAll(final boolean loadActive) {
        return quotaService.getAll(loadActive);
    }
}
