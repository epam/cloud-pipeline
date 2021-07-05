/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.dts;

import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesUpdateVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.manager.dts.DtsRegistryManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.dts.DtsCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DtsRegistryApiServiceTest extends AbstractAclTest {

    private static final String DTS_MANAGER = "DTS_MANAGER";

    private final DtsRegistry dtsRegistry = DtsCreatorUtils.getDtsRegistry();
    private final List<DtsRegistry> dtsRegistries = Collections.singletonList(dtsRegistry);
    private final DtsRegistryVO dtsRegistryVO = DtsCreatorUtils.getDtsRegistryVO();
    private final DtsRegistryPreferencesUpdateVO updateVO = DtsCreatorUtils.getPreferenceUpdateVO();
    private final DtsRegistryPreferencesRemovalVO removalVO = DtsCreatorUtils.getPreferenceRemovalVO();

    @Autowired
    private DtsRegistryApiService dtsRegistryApiService;

    @Autowired
    private DtsRegistryManager mockDtsRegistryManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllDtsRegistriesForAdmin() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThat(dtsRegistryApiService.loadAll()).isEqualTo(dtsRegistries);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldLoadAllDtsRegistriesForManager() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThat(dtsRegistryApiService.loadAll()).isEqualTo(dtsRegistries);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadAllDtsRegistriesForUser() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyLoadAllDtsRegistriesWithoutUserRole() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadDtsRegistryByIdForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(Long.toString(ID));

        assertThat(dtsRegistryApiService.loadByNameOrId(Long.toString(ID))).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldLoadDtsRegistryByIdForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(Long.toString(ID));

        assertThat(dtsRegistryApiService.loadByNameOrId(Long.toString(ID))).isEqualTo(dtsRegistry);
    }


    @Test
    @WithMockUser
    public void shouldDenyDtsRegistryByIdForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(Long.toString(ID));

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadByNameOrId(Long.toString(ID)));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyLoadDtsRegistryByIdWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(Long.toString(ID));

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadByNameOrId(Long.toString(ID)));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThat(dtsRegistryApiService.create(dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldCreateDtsRegistryForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThat(dtsRegistryApiService.create(dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.create(dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyCreateDtsRegistryWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.create(dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(ID, dtsRegistryVO);

        assertThat(dtsRegistryApiService.update(ID, dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldUpdateDtsRegistryForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(ID, dtsRegistryVO);

        assertThat(dtsRegistryApiService.update(ID, dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(ID, dtsRegistryVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.update(ID, dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyUpdateDtsRegistryWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(ID, dtsRegistryVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.update(ID, dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThat(dtsRegistryApiService.delete(ID)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldDeleteDtsRegistryForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThat(dtsRegistryApiService.delete(ID)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.delete(ID));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyDeleteDtsRegistryWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.delete(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDtsRegistryPreferencesForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(ID, updateVO);

        assertThat(dtsRegistryApiService.upsertPreferences(ID, updateVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldUpdateDtsRegistryPreferencesForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(ID, updateVO);

        assertThat(dtsRegistryApiService.upsertPreferences(ID, updateVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateDeleteDtsRegistryPreferencesForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(ID, updateVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.upsertPreferences(ID, updateVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldUpdateDeleteDtsRegistryPreferencesWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(ID, updateVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.upsertPreferences(ID, updateVO));
    }


    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDtsRegistryPreferencesForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(ID, removalVO);

        assertThat(dtsRegistryApiService.deletePreferences(ID, removalVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldDeleteDtsRegistryPreferencesForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(ID, removalVO);

        assertThat(dtsRegistryApiService.deletePreferences(ID, removalVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDtsRegistryPreferencesForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(ID, removalVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.deletePreferences(ID, removalVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyDeleteDtsRegistryPreferencesWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(ID, removalVO);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.deletePreferences(ID, removalVO));
    }
}
