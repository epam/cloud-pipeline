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
import com.epam.pipeline.manager.compute.quota.ComputeQuotaRuleManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.ID;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRule;
import static com.epam.pipeline.test.creator.compute.quota.ComputeQuotaRuleCreatorsUtils.computeQuotaRuleList;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class ComputeQuotaRuleApiServiceTest extends AbstractAclTest {

    @Autowired
    private ComputeQuotaRuleApiService computeQuotaRuleApiService;

    @Autowired
    private ComputeQuotaRuleManager mockComputeQuotaRuleManager;

    @Test
    @WithMockUser
    public void shouldLoadAll() {
        final List<ComputeQuotaRule> rules = computeQuotaRuleList();
        doReturn(rules).when(mockComputeQuotaRuleManager).loadAll();

        assertThat(computeQuotaRuleApiService.loadAll()).isEqualTo(rules);
        verify(mockComputeQuotaRuleManager).loadAll();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateForAdmin() {
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(rule).when(mockComputeQuotaRuleManager).create(rule);

        assertThat(computeQuotaRuleApiService.create(rule)).isEqualTo(rule);
        verify(mockComputeQuotaRuleManager).create(rule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> computeQuotaRuleApiService.create(computeQuotaRule()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateForAdmin() {
        final ComputeQuotaRule rule = computeQuotaRule();
        doReturn(rule).when(mockComputeQuotaRuleManager).update(ID, rule);

        assertThat(computeQuotaRuleApiService.update(ID, rule)).isEqualTo(rule);
        verify(mockComputeQuotaRuleManager).update(ID, rule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> computeQuotaRuleApiService.update(ID, computeQuotaRule()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForAdmin() {
        computeQuotaRuleApiService.delete(ID);

        verify(mockComputeQuotaRuleManager).delete(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> computeQuotaRuleApiService.delete(ID));
    }

    @Test
    @WithMockUser
    public void shouldGetKeywords() {
        final List<FilterFieldVO> keywords = Collections.emptyList();
        doReturn(keywords).when(mockComputeQuotaRuleManager).getKeywords();

        assertThat(computeQuotaRuleApiService.getKeywords()).isEqualTo(keywords);
        verify(mockComputeQuotaRuleManager).getKeywords();
    }
}
