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
    private static final String REGISTRY_ID_AS_STRING = Long.toString(ID);

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
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.loadByNameOrId(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldLoadDtsRegistryByIdForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.loadByNameOrId(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }


    @Test
    @WithMockUser
    public void shouldDenyDtsRegistryByIdForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadByNameOrId(REGISTRY_ID_AS_STRING));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyLoadDtsRegistryByIdWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).loadByNameOrId(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadByNameOrId(REGISTRY_ID_AS_STRING));
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
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);

        assertThat(dtsRegistryApiService.update(REGISTRY_ID_AS_STRING, dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldUpdateDtsRegistryForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);

        assertThat(dtsRegistryApiService.update(REGISTRY_ID_AS_STRING, dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);

        assertThrows(AccessDeniedException.class,
            () -> dtsRegistryApiService.update(REGISTRY_ID_AS_STRING, dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyUpdateDtsRegistryWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);

        assertThrows(AccessDeniedException.class,
            () -> dtsRegistryApiService.update(REGISTRY_ID_AS_STRING, dtsRegistryVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDtsRegistryHeartbeatForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).updateHeartbeat(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.updateHeartbeat(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldUpdateDtsRegistryHeartbeatForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).updateHeartbeat(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.updateHeartbeat(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDtsRegistryHeartbeatForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).updateHeartbeat(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class,
            () -> dtsRegistryApiService.updateHeartbeat(REGISTRY_ID_AS_STRING));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyUpdateDtsRegistryHeartbeatWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).updateHeartbeat(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class,
            () -> dtsRegistryApiService.updateHeartbeat(REGISTRY_ID_AS_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.delete(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldDeleteDtsRegistryForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(REGISTRY_ID_AS_STRING);

        assertThat(dtsRegistryApiService.delete(REGISTRY_ID_AS_STRING)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.delete(REGISTRY_ID_AS_STRING));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyDeleteDtsRegistryWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(REGISTRY_ID_AS_STRING);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.delete(REGISTRY_ID_AS_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDtsRegistryPreferencesForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);

        assertThat(dtsRegistryApiService.upsertPreferences(REGISTRY_ID_AS_STRING, updateVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldUpdateDtsRegistryPreferencesForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);

        assertThat(dtsRegistryApiService.upsertPreferences(REGISTRY_ID_AS_STRING, updateVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldUpdateDeleteDtsRegistryPreferencesForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);

        assertThrows(AccessDeniedException.class, () ->
            dtsRegistryApiService.upsertPreferences(REGISTRY_ID_AS_STRING, updateVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldUpdateDeleteDtsRegistryPreferencesWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);

        assertThrows(AccessDeniedException.class, () ->
            dtsRegistryApiService.upsertPreferences(REGISTRY_ID_AS_STRING, updateVO));
    }


    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDtsRegistryPreferencesForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);

        assertThat(dtsRegistryApiService.deletePreferences(REGISTRY_ID_AS_STRING, removalVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = DTS_MANAGER)
    public void shouldDeleteDtsRegistryPreferencesForManager() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);

        assertThat(dtsRegistryApiService.deletePreferences(REGISTRY_ID_AS_STRING, removalVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDtsRegistryPreferencesForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);

        assertThrows(AccessDeniedException.class, () ->
            dtsRegistryApiService.deletePreferences(REGISTRY_ID_AS_STRING, removalVO));
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyDeleteDtsRegistryPreferencesWithoutUserRole() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);

        assertThrows(AccessDeniedException.class, () ->
            dtsRegistryApiService.deletePreferences(REGISTRY_ID_AS_STRING, removalVO));
    }
}
