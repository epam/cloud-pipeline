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

package com.epam.pipeline.acl.dts;

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

    private final DtsRegistry dtsRegistry = DtsCreatorUtils.getDtsRegistry();
    private final List<DtsRegistry> dtsRegistries = Collections.singletonList(dtsRegistry);
    private final DtsRegistryVO dtsRegistryVO = DtsCreatorUtils.getDtsRegistryVO();

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
    @WithMockUser()
    public void shouldLoadAllDtsRegistriesForUser() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThat(dtsRegistryApiService.loadAll()).isEqualTo(dtsRegistries);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyLoadAllDtsRegistriesWhenRolesAreNotValid() {
        doReturn(dtsRegistries).when(mockDtsRegistryManager).loadAll();

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.loadAll());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).load(ID);

        assertThat(dtsRegistryApiService.load(ID)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser()
    public void shouldLoadDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).load(ID);

        assertThat(dtsRegistryApiService.load(ID)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyLoadDtsRegistryWhenRolesAreNotValid() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).load(ID);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateDtsRegistryForAdmin() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThat(dtsRegistryApiService.create(dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser()
    public void shouldCreateDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).create(dtsRegistryVO);

        assertThat(dtsRegistryApiService.create(dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyCreateDtsRegistryWhenRolesAreNotValid() {
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
    @WithMockUser()
    public void shouldUpdateDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).update(ID, dtsRegistryVO);

        assertThat(dtsRegistryApiService.update(ID, dtsRegistryVO)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyUpdateDtsRegistryWhenRolesAreNotValid() {
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
    @WithMockUser()
    public void shouldDeleteDtsRegistryForUser() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThat(dtsRegistryApiService.delete(ID)).isEqualTo(dtsRegistry);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyDeleteDtsRegistryWhenRolesAreNotValid() {
        doReturn(dtsRegistry).when(mockDtsRegistryManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> dtsRegistryApiService.delete(ID));
    }
}
