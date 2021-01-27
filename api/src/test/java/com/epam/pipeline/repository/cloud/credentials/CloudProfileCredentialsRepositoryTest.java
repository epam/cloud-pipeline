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

package com.epam.pipeline.repository.cloud.credentials;

import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.repository.cloud.credentials.aws.AWSProfileCredentialsRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.test.creator.cloud.credentials.CloudProfileCredentialsCreatorUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.assertions.cloud.credentials.CloudProfileCredentialsAssertions.assertEquals;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getRole;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class CloudProfileCredentialsRepositoryTest extends AbstractJpaTest {
    private static final String USER1 = "USER1";
    private static final String USER2 = "USER2";
    private static final String TEST_ROLE1 = "ROLE_TEST1";
    private static final String TEST_ROLE2 = "ROLE_TEST2";

    @Autowired
    private CloudProfileCredentialsRepository cloudProfileCredentialsRepository;
    @Autowired
    private AWSProfileCredentialsRepository awsProfileCredentialsRepository;
    @Autowired
    private PipelineUserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserDao userDao;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void awsCrudTest() {
        final AWSProfileCredentialsEntity awsEntity = CloudProfileCredentialsCreatorUtils.awsProfileCredentialsEntity();
        awsProfileCredentialsRepository.save(awsEntity);

        entityManager.clear();
        assertThat(awsEntity.getId(), notNullValue());
        final Long entityId = awsEntity.getId();

        final AWSProfileCredentialsEntity storedAwsEntity = awsProfileCredentialsRepository.findOne(entityId);
        assertEquals(awsEntity, storedAwsEntity);

        awsEntity.setProfileName(TEST_STRING);
        awsProfileCredentialsRepository.save(awsEntity);
        entityManager.flush();
        entityManager.clear();

        final AWSProfileCredentialsEntity updatedEntity = awsProfileCredentialsRepository.findOne(entityId);
        assertEquals(awsEntity, updatedEntity);

        final CloudProfileCredentialsEntity storedEntity = cloudProfileCredentialsRepository.findOne(entityId);
        assertThat(storedEntity.getId(), is(entityId));
        assertThat(storedEntity.getCloudProvider(), is(CloudProvider.AWS));

        final List<CloudProfileCredentialsEntity> entities =
                StreamSupport.stream(cloudProfileCredentialsRepository.findAll().spliterator(), false)
                        .collect(Collectors.toList());
        assertThat(entities.size(), is(1));
        assertThat(entities.get(0).getId(), is(entityId));

        cloudProfileCredentialsRepository.delete(entityId);

        entityManager.flush();
        entityManager.clear();

        assertThat(cloudProfileCredentialsRepository.findOne(entityId), nullValue());
    }

    @Test
    @Transactional
    public void shouldAssignUsers() {
        final PipelineUser user1 = userDao.createUser(getPipelineUser(USER1), Collections.emptyList());
        final PipelineUser user2 = userDao.createUser(getPipelineUser(USER2), Collections.emptyList());

        final AWSProfileCredentialsEntity profileEntity = CloudProfileCredentialsCreatorUtils
                .awsProfileCredentialsEntity();
        awsProfileCredentialsRepository.save(profileEntity);

        final Long profileId = profileEntity.getId();
        final List<CloudProfileCredentialsEntity> profiles = new ArrayList<>();
        profiles.add(profileEntity);

        final PipelineUser userEntity1 = userRepository.findOne(user1.getId());
        userEntity1.setCloudProfiles(profiles);
        userRepository.save(userEntity1);

        final PipelineUser userEntity2 = userRepository.findOne(user2.getId());
        userEntity2.setCloudProfiles(profiles);
        userRepository.save(userEntity2);

        entityManager.flush();
        entityManager.clear();

        final CloudProfileCredentialsEntity storedProfileEntity = cloudProfileCredentialsRepository.findOne(profileId);
        assertThat(storedProfileEntity.getId(), is(profileId));
        assertThat(storedProfileEntity.getUsers().size(), is(2));
    }

    @Test
    @Transactional
    public void shouldAssignRole() {
        final Role role1 = roleRepository.save(getRole(TEST_ROLE1));
        final Role role2 = roleRepository.save(getRole(TEST_ROLE2));

        final AWSProfileCredentialsEntity profileEntity = CloudProfileCredentialsCreatorUtils
                .awsProfileCredentialsEntity();
        awsProfileCredentialsRepository.save(profileEntity);

        final Long profileId = profileEntity.getId();
        final List<CloudProfileCredentialsEntity> profiles = new ArrayList<>();
        profiles.add(profileEntity);

        role1.setCloudProfiles(profiles);
        roleRepository.save(role1);
        role2.setCloudProfiles(profiles);
        roleRepository.save(role2);

        entityManager.flush();
        entityManager.clear();

        final CloudProfileCredentialsEntity storedProfileEntity = cloudProfileCredentialsRepository.findOne(profileId);
        assertThat(storedProfileEntity.getId(), is(profileId));
        assertThat(storedProfileEntity.getRoles().size(), is(2));
    }
}
