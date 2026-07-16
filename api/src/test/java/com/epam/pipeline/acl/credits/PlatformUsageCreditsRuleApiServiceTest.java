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

package com.epam.pipeline.acl.credits;

import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsRuleService;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.filterFieldVOMap;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRule;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsRuleCreatorsUtils.platformUsageCreditsRuleList;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class PlatformUsageCreditsRuleApiServiceTest extends AbstractAclTest {

    @Autowired
    private PlatformUsageCreditsRuleApiService platformUsageCreditsRuleApiService;

    @Autowired
    private PlatformUsageCreditsRuleService mockPlatformUsageCreditsRuleService;

    @Test
    @WithMockUser
    public void shouldLoadAll() {
        final List<PlatformUsageCreditsUpdateRule> rules = platformUsageCreditsRuleList();
        doReturn(rules).when(mockPlatformUsageCreditsRuleService).loadAll();

        assertThat(platformUsageCreditsRuleApiService.loadAll()).isEqualTo(rules);
        verify(mockPlatformUsageCreditsRuleService).loadAll();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateForAdmin() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        doReturn(rule).when(mockPlatformUsageCreditsRuleService).create(rule);

        assertThat(platformUsageCreditsRuleApiService.create(rule)).isEqualTo(rule);
        verify(mockPlatformUsageCreditsRuleService).create(rule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateForNonAdmin() {
        assertThrows(AccessDeniedException.class, () ->
                platformUsageCreditsRuleApiService.create(platformUsageCreditsRule()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateForAdmin() {
        final PlatformUsageCreditsUpdateRule rule = platformUsageCreditsRule();
        doReturn(rule).when(mockPlatformUsageCreditsRuleService).update(ID, rule);

        assertThat(platformUsageCreditsRuleApiService.update(ID, rule)).isEqualTo(rule);
        verify(mockPlatformUsageCreditsRuleService).update(ID, rule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateForNonAdmin() {
        assertThrows(AccessDeniedException.class, () ->
                platformUsageCreditsRuleApiService.update(ID, platformUsageCreditsRule()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteForAdmin() {
        platformUsageCreditsRuleApiService.delete(ID);

        verify(mockPlatformUsageCreditsRuleService).delete(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteForNonAdmin() {
        assertThrows(AccessDeniedException.class, () -> platformUsageCreditsRuleApiService.delete(ID));
    }

    @Test
    @WithMockUser
    public void shouldGetKeywords() {
        final Map<String, List<FilterFieldVO>> keywords = filterFieldVOMap();
        doReturn(keywords).when(mockPlatformUsageCreditsRuleService).getKeywords();

        assertThat(platformUsageCreditsRuleApiService.getKeywords()).isEqualTo(keywords);
        verify(mockPlatformUsageCreditsRuleService).getKeywords();
    }
}
