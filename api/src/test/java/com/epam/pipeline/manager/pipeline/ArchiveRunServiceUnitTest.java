/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ArchiveRunServiceUnitTest {
    private static final String TEST = "test";
    private static final String USER1 = "user1";
    private static final Long USER_ID1 = 1L;
    private static final int METADATA_DAYS = 5;
    private static final int INPUT_DAYS = 3;
    private static final String GROUP1 = "group1";
    private static final String GROUP2 = "group2";
    private static final Long GROUP_ID1 = 1L;
    private static final Long GROUP_ID2 = 2L;
    private static final EntityVO USER_ENTITY1 = new EntityVO(USER_ID1, AclClass.PIPELINE_USER);
    private static final EntityVO GROUP_ENTITY1 = new EntityVO(GROUP_ID1, AclClass.ROLE);
    private static final EntityVO GROUP_ENTITY2 = new EntityVO(GROUP_ID2, AclClass.ROLE);
    private static final int CHUNK_SIZE = 3;

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final MetadataDao metadataDao = mock(MetadataDao.class);
    private final UserManager userManager = mock(UserManager.class);
    private final RoleManager roleManager = mock(RoleManager.class);
    private final ArchiveRunAsynchronousService archiveRunAsyncService = mock(ArchiveRunAsynchronousService.class);
    private final ArchiveRunService archiveRunService = new ArchiveRunService(preferenceManager, messageHelper,
            metadataDao, userManager, roleManager, archiveRunAsyncService);

    @Before
    public void setUp() {
        doReturn(TEST).when(preferenceManager).getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_METADATA_KEY);
        doReturn(CHUNK_SIZE).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_RUNS_CHUNK_SIZE);
    }

    @Test
    public void shouldArchiveRunsForSpecifiedUserWithoutDays() {
        doReturn(user()).when(userManager).loadByNameOrId(USER1);
        doReturn(metadataEntry(USER_ENTITY1)).when(metadataDao).loadMetadataItem(USER_ENTITY1);

        archiveRunService.archiveRuns(USER1, true, null);

        verifyDays(METADATA_DAYS);
    }

    @Test
    public void shouldArchiveRunsForSpecifiedUserWithDays() {
        doReturn(user()).when(userManager).loadByNameOrId(USER1);

        archiveRunService.archiveRuns(USER1, true, INPUT_DAYS);

        verifyDays(INPUT_DAYS);
        notInvoked(metadataDao).loadMetadataItem(any());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailArchiveRunsForUserWithoutDaysAndMetadata() {
        doReturn(user()).when(userManager).loadByNameOrId(USER1);
        doReturn(new MetadataEntry()).when(metadataDao).loadMetadataItem(USER_ENTITY1);

        archiveRunService.archiveRuns(USER1, true, null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailArchiveRunsForUserWithoutDaysAndMetadataNotNumeric() {
        doReturn(user()).when(userManager).loadByNameOrId(USER1);
        doReturn(invalidMetadata(USER_ENTITY1)).when(metadataDao).loadMetadataItem(USER_ENTITY1);

        archiveRunService.archiveRuns(USER1, true, null);
    }

    @Test
    public void shouldArchiveRunsForSpecifiedGroupWithoutDays() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(extendedRole()).when(roleManager).loadRoleWithUsers(GROUP_ID1);
        doReturn(metadataEntry(GROUP_ENTITY1)).when(metadataDao).loadMetadataItem(GROUP_ENTITY1);

        archiveRunService.archiveRuns(GROUP1, false, null);

        verifyDays(METADATA_DAYS);
    }

    @Test
    public void shouldArchiveRunsForSpecifiedGroupWithDays() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(extendedRole()).when(roleManager).loadRoleWithUsers(GROUP_ID1);

        archiveRunService.archiveRuns(GROUP1, false, INPUT_DAYS);

        verifyDays(INPUT_DAYS);
        notInvoked(metadataDao).loadMetadataItem(any());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailArchiveRunsForGroupWithoutDaysAndMetadata() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(new MetadataEntry()).when(metadataDao).loadMetadataItem(GROUP_ENTITY1);

        archiveRunService.archiveRuns(GROUP1, false, null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailArchiveRunsForGroupWithoutDaysAndMetadataNotNumeric() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(invalidMetadata(GROUP_ENTITY1)).when(metadataDao).loadMetadataItem(GROUP_ENTITY1);

        archiveRunService.archiveRuns(GROUP1, false, null);
    }

    @Test
    public void shouldNotArchiveRunsForGroupIfNoOwnersFound() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(extendedRole(null)).when(roleManager).loadRoleWithUsers(GROUP_ID1);

        archiveRunService.archiveRuns(GROUP1, false, INPUT_DAYS);

        notInvoked(archiveRunAsyncService).archiveRunsAsynchronous(any(), any(), anyInt(), anyInt());
    }

    @Test
    public void shouldUseUsersDaysToArchiveRunsForGroup() {
        doReturn(role()).when(roleManager).loadRoleByNameOrId(GROUP1);
        doReturn(extendedRole()).when(roleManager).loadRoleWithUsers(GROUP_ID1);
        doReturn(metadataEntry(GROUP_ENTITY1)).when(metadataDao).loadMetadataItem(GROUP_ENTITY1);
        doReturn(Collections.singletonList(metadataEntry(USER_ENTITY1, INPUT_DAYS))).when(metadataDao)
                .searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);

        archiveRunService.archiveRuns(GROUP1, false, null);
        verifyDays(INPUT_DAYS);
    }

    @Test
    public void shouldGetMinDaysValueIfUserPertainToMultipleGroups() {
        final Role role1 = extendedRole(GROUP_ID1, GROUP1, Collections.singletonList(user()));
        final Role role2 = extendedRole(GROUP_ID2, GROUP2, Collections.singletonList(user()));
        doReturn(Arrays.asList(role1, role2)).when(roleManager).loadAllRoles(true);
        doReturn(Arrays.asList(metadataEntry(GROUP_ENTITY1, INPUT_DAYS), metadataEntry(GROUP_ENTITY2, METADATA_DAYS)))
                .when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.ROLE, TEST);
        doReturn(Collections.singletonList(user())).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID1));

        archiveRunService.archiveRuns();
        verifyDays(INPUT_DAYS);
    }

    @Test
    public void shouldUseUsersDaysToArchiveRuns() {
        doReturn(Collections.singletonList(role())).when(roleManager).loadAllRoles(true);
        doReturn(Collections.singletonList(metadataEntry(GROUP_ENTITY1, INPUT_DAYS)))
                .when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.ROLE, TEST);
        doReturn(Collections.singletonList(user())).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID1));
        doReturn(Collections.singletonList(metadataEntry(USER_ENTITY1, METADATA_DAYS)))
                .when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);

        archiveRunService.archiveRuns();
        verifyDays(METADATA_DAYS);
    }

    private void verifyDays(final int expectedDays) {
        final ArgumentCaptor<Map<String, Date>> argument = ArgumentCaptor.forClass((Class) Map.class);
        verify(archiveRunAsyncService).archiveRunsAsynchronous(argument.capture(), any(), any(), any());
        final Map<String, Date> results = argument.getValue();
        assertThat(results).hasSize(1);
        assertDays(results.get(USER1), expectedDays);
    }

    private void assertDays(final Date resultDate, final int expectedDays) {
        assertThat(Duration.between(resultDate.toInstant(), DateUtils.now().toInstant()).toDays())
                .isEqualTo(expectedDays);
    }

    private PipelineUser user() {
        final PipelineUser user = new PipelineUser();
        user.setUserName(USER1);
        user.setId(USER_ID1);
        return user;
    }

    private Role role() {
        final Role role = new Role();
        role.setName(GROUP1);
        role.setId(GROUP_ID1);
        return role;
    }

    private Role extendedRole() {
        return extendedRole(GROUP_ID1, GROUP1, Collections.singletonList(user()));
    }

    private Role extendedRole(final List<PipelineUser> users) {
        return extendedRole(GROUP_ID1, GROUP1, users);
    }

    private Role extendedRole(final Long roleId, final String roleName, final List<PipelineUser> users) {
        final ExtendedRole role = new ExtendedRole();
        role.setName(roleName);
        role.setId(roleId);
        role.setUsers(users);
        return role;
    }

    private MetadataEntry metadataEntry(final EntityVO entity) {
        return metadataEntry(entity, METADATA_DAYS);
    }

    private MetadataEntry metadataEntry(final EntityVO entity, final Integer days) {
        final PipeConfValue value = new PipeConfValue("string", String.valueOf(days));
        final MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(entity);
        metadataEntry.setData(Collections.singletonMap(TEST, value));
        return metadataEntry;
    }

    private MetadataEntry invalidMetadata(final EntityVO entity) {
        final PipeConfValue value = new PipeConfValue("string", TEST);
        final MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(entity);
        metadataEntry.setData(Collections.singletonMap(TEST, value));
        return metadataEntry;
    }
}
