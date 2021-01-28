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

package com.epam.pipeline.manager.cloud.credentials;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.cloud.credentials.AbstractCloudProfileCredentials;
import com.epam.pipeline.dto.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.cloud.credentials.aws.AWSProfileCredentialsManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.repository.cloud.credentials.CloudProfileCredentialsRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.epam.pipeline.assertions.cloud.credentials.CloudProfileCredentialsAssertions.assertEquals;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.cloud.credentials.CloudProfileCredentialsCreatorUtils
        .awsProfileCredentials;
import static com.epam.pipeline.test.creator.cloud.credentials.CloudProfileCredentialsCreatorUtils
        .awsProfileCredentialsEntity;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getTemporaryCredentials;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getRole;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CloudProfileCredentialsManagerProviderTest {

    private final AWSProfileCredentialsManager cloudProfileCredentialsManager = mockCredentialsManager();
    private final CloudProfileCredentialsRepository cloudProfileCredentialsRepository =
            mock(CloudProfileCredentialsRepository.class);
    private final CloudProfileCredentialsMapper cloudProfileCredentialsMapper =
            mock(CloudProfileCredentialsMapper.class);
    private final PipelineUserRepository userRepository = mock(PipelineUserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final CloudRegionManager cloudRegionManager = mock(CloudRegionManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final CloudProfileCredentialsManagerProvider manager = new CloudProfileCredentialsManagerProvider(
            Collections.singletonList(cloudProfileCredentialsManager), cloudProfileCredentialsRepository,
            cloudProfileCredentialsMapper, userRepository, roleRepository, cloudRegionManager, messageHelper);

    @Test
    public void shouldCreateAWSCredentialsProfile() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        manager.create(credentials);

        verify(cloudProfileCredentialsManager).create(credentials);
    }

    @Test
    public void shouldUpdateAWSCredentialsProfile() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        manager.update(ID, credentials);

        verify(cloudProfileCredentialsManager).update(ID, credentials);
    }

    @Test
    public void shouldGetAWSCredentialsProfile() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);

        final AWSProfileCredentials result = (AWSProfileCredentials) manager.get(ID);
        verify(cloudProfileCredentialsRepository).findOne(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        assertEquals(result, credentials);
    }

    @Test
    public void shouldFailGetAWSCredentialsProfileIfProfileNotFound() {
        doReturn(null).when(cloudProfileCredentialsRepository).findOne(ID);
        assertThrows(IllegalArgumentException.class, () -> manager.get(ID));
    }

    @Test
    public void shouldDeleteAWSCredentialsProfile() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);

        final AWSProfileCredentials result = (AWSProfileCredentials) manager.delete(ID);
        verify(cloudProfileCredentialsRepository).findOne(ID);
        verify(cloudProfileCredentialsRepository).delete(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        assertEquals(result, credentials);
    }

    @Test
    public void shouldFailDeleteAWSCredentialsProfileIfUsersAssociated() {
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setUsers(Collections.singletonList(getPipelineUser()));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThrows(IllegalStateException.class, () -> manager.delete(ID));
    }

    @Test
    public void shouldFailDeleteAWSCredentialsProfileIfRolesAssociated() {
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setRoles(Collections.singletonList(getRole()));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThrows(IllegalStateException.class, () -> manager.delete(ID));
    }

    @Test
    public void shouldFindAllAWSCredentialsProfile() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        final Iterable<AWSProfileCredentialsEntity> entities = () ->
                Collections.singletonList(credentialsEntity).iterator();
        doReturn(entities).when(cloudProfileCredentialsRepository).findAll();
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);

        final List<? extends AbstractCloudProfileCredentials> result = manager.findAll(null);
        verify(cloudProfileCredentialsRepository).findAll();
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        assertThat(result).hasSize(1);
        assertEquals(credentials, (AWSProfileCredentials) result.get(0));
    }

    @Test
    public void shouldGenerateAWSProfileCredentials() {
        final AWSProfileCredentials credentials = awsProfileCredentials();
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        final TemporaryCredentials temporaryCredentials = getTemporaryCredentials();
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        doReturn(temporaryCredentials).when(cloudProfileCredentialsManager)
                .generateProfileCredentials(credentials, null);

        final TemporaryCredentials result = manager.generateProfileCredentials(ID, null);
        verify(cloudProfileCredentialsRepository).findOne(ID);
        notInvoked(cloudRegionManager).load(any());
        verify(cloudProfileCredentialsManager).generateProfileCredentials(credentials, null);
        assertThat(result).isEqualTo(temporaryCredentials);
    }

    @Test
    public void shouldGetAssignedAWSProfileCredentialsForUser() {
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        final PipelineUser pipelineUser = getPipelineUser(TEST_STRING, ID);
        pipelineUser.setCloudProfiles(Collections.singletonList(credentialsEntity));
        doReturn(pipelineUser).when(userRepository).findOne(ID);
        final AWSProfileCredentials credentials = awsProfileCredentials();
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);

        final List<? extends AbstractCloudProfileCredentials> result = manager.getAssignedProfiles(ID, true);
        verify(userRepository).findOne(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        assertThat(result).hasSize(1);
        assertEquals(credentials, (AWSProfileCredentials) result.get(0));
    }

    @Test
    public void shouldFailGetAssignedAWSProfileCredentialsForUserIfUserNotFound() {
        doReturn(null).when(userRepository).findOne(ID);
        assertThrows(IllegalArgumentException.class, () -> manager.getAssignedProfiles(ID, true));
    }

    @Test
    public void shouldGetAssignedAWSProfileCredentialsForRole() {
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        final Role role = getRole(TEST_STRING, ID);
        role.setCloudProfiles(Collections.singletonList(credentialsEntity));
        doReturn(role).when(roleRepository).findOne(ID);
        final AWSProfileCredentials credentials = awsProfileCredentials();
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);

        final List<? extends AbstractCloudProfileCredentials> result = manager.getAssignedProfiles(ID, false);
        verify(roleRepository).findOne(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        assertThat(result).hasSize(1);
        assertEquals(credentials, (AWSProfileCredentials) result.get(0));
    }

    @Test
    public void shouldFailGetAssignedAWSProfileCredentialsForRoleIfRoleNotFound() {
        doReturn(null).when(roleRepository).findOne(ID);
        assertThrows(IllegalArgumentException.class, () -> manager.getAssignedProfiles(ID, false));
    }

    @Test
    public void shouldAssignAWSProfileCredentialsForUser() {
        final AWSProfileCredentialsEntity credentialsEntity1 = mockAwsProfileCredentialsEntity(ID);
        final AWSProfileCredentialsEntity credentialsEntity2 = mockAwsProfileCredentialsEntity(ID_2);

        final Set<Long> profileIds = new HashSet<>();
        profileIds.add(ID);

        final PipelineUser pipelineUser = getPipelineUser(TEST_STRING, ID);
        doReturn(pipelineUser).when(userRepository).findOne(ID);

        final List<? extends AbstractCloudProfileCredentials> result = manager.assignProfiles(ID, true,
                profileIds, ID_2);
        verify(userRepository).findOne(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity1);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity2);
        assertThat(result).hasSize(2);
        assertThat(pipelineUser.getDefaultProfileId()).isEqualTo(ID_2);
    }

    @Test
    public void shouldAssignAWSProfileCredentialsForRole() {
        final AWSProfileCredentialsEntity credentialsEntity1 = mockAwsProfileCredentialsEntity(ID);
        final AWSProfileCredentialsEntity credentialsEntity2 = mockAwsProfileCredentialsEntity(ID_2);

        final Set<Long> profileIds = new HashSet<>();
        profileIds.add(ID);

        final Role role = getRole(TEST_STRING, ID);
        doReturn(role).when(roleRepository).findOne(ID);

        final List<? extends AbstractCloudProfileCredentials> result = manager.assignProfiles(ID, false,
                profileIds, ID_2);
        verify(roleRepository).findOne(ID);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity1);
        verify(cloudProfileCredentialsMapper).toDto(credentialsEntity2);
        assertThat(result).hasSize(2);
        assertThat(role.getDefaultProfileId()).isEqualTo(ID_2);
    }

    @Test
    public void shouldHaveAssignedUser() {
        final PipelineUser pipelineUser = getPipelineUser(TEST_STRING, ID);
        doReturn(pipelineUser).when(userRepository).findOne(ID);
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setUsers(Collections.singletonList(pipelineUser));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThat(manager.hasAssignedUserOrRole(ID, ID, null)).isTrue();
    }

    @Test
    public void shouldNotHaveAssignedUser() {
        final PipelineUser pipelineUser = getPipelineUser(TEST_STRING, ID);
        doReturn(pipelineUser).when(userRepository).findOne(ID);
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setUsers(Collections.singletonList(pipelineUser));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThat(manager.hasAssignedUserOrRole(ID, ID_2, null)).isFalse();
    }

    @Test
    public void shouldHaveAssignedRole() {
        final Role role = getRole(TEST_STRING, ID);
        doReturn(role).when(roleRepository).findOne(ID);
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setRoles(Collections.singletonList(role));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThat(manager.hasAssignedUserOrRole(ID, ID, Collections.singletonList(role))).isTrue();
    }

    @Test
    public void shouldNotHaveAssignedRole() {
        final Role role = getRole(TEST_STRING, ID);
        doReturn(role).when(roleRepository).findOne(ID);
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity();
        credentialsEntity.setRoles(Collections.singletonList(role));
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(ID);

        assertThat(manager.hasAssignedUserOrRole(ID, ID, Collections.singletonList(getRole(TEST_STRING, ID_2))))
                .isFalse();
    }

    private static AWSProfileCredentialsManager mockCredentialsManager() {
        final AWSProfileCredentialsManager manager = mock(AWSProfileCredentialsManager.class);
        doReturn(CloudProvider.AWS).when(manager).getProvider();
        return manager;
    }

    private AWSProfileCredentialsEntity mockAwsProfileCredentialsEntity(final Long id) {
        final AWSProfileCredentialsEntity credentialsEntity = awsProfileCredentialsEntity(id);
        doReturn(credentialsEntity).when(cloudProfileCredentialsRepository).findOne(id);
        final AWSProfileCredentials credentials = awsProfileCredentials(id);
        doReturn(credentials).when(cloudProfileCredentialsMapper).toDto(credentialsEntity);
        return credentialsEntity;
    }
}
