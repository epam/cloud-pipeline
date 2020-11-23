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

package com.epam.pipeline.acl.preference;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.preferences.PreferenceCreatorUtils.getPreference;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class PreferenceApiServiceTest extends AbstractAclTest {

    private final Preference preference = getPreference();
    private final List<Preference> preferenceList = Collections.singletonList(preference);

    @Autowired
    private PreferenceApiService preferenceApiService;

    @Autowired
    private PreferenceManager mockPreferenceManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    public void shouldLoadAll() {
        doReturn(preferenceList).when(mockPreferenceManager).loadAll();
        doReturn(true).when(mockAuthManager).isAdmin();

        assertThat(preferenceApiService.loadAll()).isEqualTo(preferenceList);
    }

    @Test
    public void shouldLoadVisible() {
        doReturn(preferenceList).when(mockPreferenceManager).loadVisible();

        assertThat(preferenceApiService.loadAll()).isEqualTo(preferenceList);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePreferencesForAdmin() {
        doReturn(preferenceList).when(mockPreferenceManager).update(preferenceList);

        assertThat(preferenceApiService.update(preferenceList)).isEqualTo(preferenceList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdatePreferencesForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> preferenceApiService.update(preferenceList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPreferenceForAdmin() {
        doReturn(Optional.of(preference)).when(mockPreferenceManager).load(TEST_NAME);

        assertThat(preferenceApiService.load(TEST_NAME)).isEqualTo(preference);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadVisiblePreferenceForNotAdmin() {
        final Preference visiblePreference = new Preference();
        visiblePreference.setVisible(true);
        doReturn(Optional.of(visiblePreference)).when(mockPreferenceManager).load(TEST_NAME);

        assertThat(preferenceApiService.load(TEST_NAME)).isEqualTo(visiblePreference);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPreferenceForNotAdmin() {
        final Preference preference = new Preference();
        preference.setVisible(false);
        doReturn(Optional.of(preference)).when(mockPreferenceManager).load(TEST_NAME);

        assertThrows(AccessDeniedException.class, () -> preferenceApiService.load(TEST_NAME));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePreferenceForAdmin() {
        preferenceApiService.delete(TEST_NAME);

        verify(mockPreferenceManager).delete(TEST_NAME);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeletePreferenceForNotAdmin() {
        assertThrows(AccessDeniedException.class, () -> preferenceApiService.delete(TEST_NAME));
    }
}
