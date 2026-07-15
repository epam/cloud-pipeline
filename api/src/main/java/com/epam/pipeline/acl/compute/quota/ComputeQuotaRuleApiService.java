/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.compute.quota;

import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.manager.compute.quota.ComputeQuotaRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class ComputeQuotaRuleApiService {

    private final ComputeQuotaRuleService manager;

    public List<ComputeQuotaRule> loadAll() {
        return manager.loadAll();
    }

    @PreAuthorize(ADMIN_ONLY)
    public ComputeQuotaRule create(final ComputeQuotaRule rule) {
        return manager.create(rule);
    }

    @PreAuthorize(ADMIN_ONLY)
    public ComputeQuotaRule update(final Long id, final ComputeQuotaRule rule) {
        return manager.update(id, rule);
    }

    @PreAuthorize(ADMIN_ONLY)
    public void delete(final Long id) {
        manager.delete(id);
    }

    public List<FilterFieldVO> getKeywords() {
        return manager.getKeywords();
    }
}
