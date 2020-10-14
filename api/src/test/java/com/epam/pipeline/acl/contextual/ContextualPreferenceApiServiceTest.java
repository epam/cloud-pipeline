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

package com.epam.pipeline.acl.contextual;

import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.contextual.ContextualPreferenceCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ContextualPreferenceApiServiceTest extends AbstractAclTest {

    @Autowired
    private ContextualPreferenceApiService preferenceApiService;

    @Autowired
    private ContextualPreferenceManager mockPreferenceManager;

    private final ContextualPreference contextualPreference =
            ContextualPreferenceCreatorUtils.getContextualPreference();

    private final List<ContextualPreference> preferenceList = Collections.singletonList(contextualPreference);

    private final ContextualPreferenceExternalResource cpeResource =
            ContextualPreferenceCreatorUtils.getCPExternalResource();

    private final ContextualPreferenceVO contextualPreferenceVO =
            ContextualPreferenceCreatorUtils.getContextualPreferenceVO();

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllContextualPreferencesForAdmin() {
        doReturn(preferenceList).when(mockPreferenceManager).loadAll();

        List<ContextualPreference> resultPreferenceList = preferenceApiService.loadAll();

        assertThat(resultPreferenceList.size()).isEqualTo(1);
        assertThat(resultPreferenceList.get(0)).isEqualTo(contextualPreference);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyLoadingAllContextualPreferencesForNotAdmin() {
        doReturn(preferenceList).when(mockPreferenceManager).loadAll();

        preferenceApiService.loadAll();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).load(TEST_STRING, cpeResource);

        ContextualPreference resultPreference = preferenceApiService.load(TEST_STRING, cpeResource);

        assertThat(resultPreference).isEqualTo(contextualPreference);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyLoadingContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).load(TEST_STRING, cpeResource);

        preferenceApiService.load(TEST_STRING, cpeResource);
    }

    @Test
    public void shouldSearchContextualPreference() {
        doReturn(contextualPreference).when(mockPreferenceManager).search(TEST_STRING_LIST, cpeResource);

        ContextualPreference resultPreference = preferenceApiService.search(TEST_STRING_LIST, cpeResource);

        assertThat(resultPreference).isEqualTo(contextualPreference);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpsertContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).upsert(contextualPreferenceVO);

        ContextualPreference resultPreference = preferenceApiService.upsert(contextualPreferenceVO);

        assertThat(resultPreference).isEqualTo(contextualPreference);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyUpsertContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).upsert(contextualPreferenceVO);

        preferenceApiService.upsert(contextualPreferenceVO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).delete(TEST_STRING, cpeResource);

        ContextualPreference resultPreference = preferenceApiService.delete(TEST_STRING, cpeResource);

        assertThat(resultPreference).isEqualTo(contextualPreference);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyDeletionContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).delete(TEST_STRING, cpeResource);

        preferenceApiService.delete(TEST_STRING, cpeResource);
    }
}
