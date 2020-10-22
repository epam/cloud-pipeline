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

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ContextualPreferenceApiServiceTest extends AbstractAclTest {

    private final ContextualPreference contextualPreference =
            ContextualPreferenceCreatorUtils.getContextualPreference();
    private final ContextualPreferenceExternalResource externalResource =
            ContextualPreferenceCreatorUtils.getCPExternalResource();
    private final ContextualPreferenceVO contextualPreferenceVO =
            ContextualPreferenceCreatorUtils.getContextualPreferenceVO();

    private final List<ContextualPreference> preferenceList =
            ContextualPreferenceCreatorUtils.getContextualPreferenceList();

    @Autowired
    private ContextualPreferenceApiService preferenceApiService;

    @Autowired
    private ContextualPreferenceManager mockPreferenceManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllContextualPreferencesForAdmin() {
        doReturn(preferenceList).when(mockPreferenceManager).loadAll();

        assertThat(preferenceApiService.loadAll()).hasSize(1).contains(contextualPreference);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingAllContextualPreferencesForNotAdmin() {
        doReturn(preferenceList).when(mockPreferenceManager).loadAll();

        assertThrows(AccessDeniedException.class, () -> preferenceApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).load(TEST_STRING, externalResource);

        assertThat(preferenceApiService.load(TEST_STRING, externalResource)).isEqualTo(contextualPreference);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).load(TEST_STRING, externalResource);

        assertThrows(AccessDeniedException.class, () -> preferenceApiService.load(TEST_STRING, externalResource));
    }

    @Test
    @WithMockUser
    public void shouldSearchContextualPreference() {
        doReturn(contextualPreference).when(mockPreferenceManager).search(TEST_STRING_LIST, externalResource);

        assertThat(preferenceApiService.search(TEST_STRING_LIST, externalResource)).isEqualTo(contextualPreference);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpsertContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).upsert(contextualPreferenceVO);

        assertThat(preferenceApiService.upsert(contextualPreferenceVO)).isEqualTo(contextualPreference);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpsertContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).upsert(contextualPreferenceVO);

        assertThrows(AccessDeniedException.class, () -> preferenceApiService.upsert(contextualPreferenceVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteContextualPreferenceForAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).delete(TEST_STRING, externalResource);

        assertThat(preferenceApiService.delete(TEST_STRING, externalResource)).isEqualTo(contextualPreference);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeletionContextualPreferenceForNotAdmin() {
        doReturn(contextualPreference).when(mockPreferenceManager).delete(TEST_STRING, externalResource);

        assertThrows(AccessDeniedException.class, () -> preferenceApiService.delete(TEST_STRING, externalResource));
    }
}
