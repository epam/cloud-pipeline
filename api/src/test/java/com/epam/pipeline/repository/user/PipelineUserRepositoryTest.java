/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.user;

import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.user.UserCreatorUtils.ROLE_OWNER;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PipelineUserRepositoryTest extends AbstractJpaTest {
    private static final String USER_NAME = "user";
    private static final String ROLE_NAME = "role";
    private static final String USER_NAME_2 = "user2";
    private static final String SERVICE_ACCOUNT = "service";

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private UserDao userDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private PipelineUserRepository pipelineUserRepository;

    @Test
    @Transactional
    public void shouldLoadRunnersForUser() {
        final PipelineUser serviceAccount = userDao.createUser(getPipelineUser(SERVICE_ACCOUNT),
                Collections.emptyList());

        final RunnerSid userSid = aclSidEntity(USER_NAME, true);
        final RunnerSid roleSid = aclSidEntity(ROLE_NAME, false);

        serviceAccount.setAllowedRunners(Arrays.asList(userSid, roleSid));
        pipelineUserRepository.save(serviceAccount);

        entityManager.flush();

        final List<RunnerSid> allowedRunners = pipelineUserRepository.findOne(serviceAccount.getId())
                .getAllowedRunners();
        assertThat(allowedRunners.size(), is(2));
        allowedRunners.forEach(this::assertSid);
    }

    @Test
    @Transactional
    public void shouldLoadUsersWithRoles() {
        final Role targetRole = roleDao.createRole(ROLE_NAME, ROLE_OWNER);
        userDao.createUser(getPipelineUser(USER_NAME), Collections.emptyList());
        userDao.createUser(getPipelineUser(USER_NAME_2), Collections.singletonList(targetRole.getId()));

        final List<PipelineUser> result = pipelineUserRepository.findByRoles_NameIn(
                Collections.singletonList(targetRole.getName()));
        assertThat(result.size(), is(1));
    }

    private void assertSid(final RunnerSid runnerSid) {
        if (runnerSid.isPrincipal()) {
            assertUserSid(runnerSid);
            return;
        }
        assertRoleSid(runnerSid);
    }

    private void assertUserSid(final RunnerSid loaded) {
        assertThat(loaded, notNullValue());
        assertThat(loaded.getName(), is(USER_NAME));
        assertThat(loaded.getAccessType(), is(RunAccessType.SSH));
        assertTrue(loaded.isPrincipal());
    }

    private void assertRoleSid(final RunnerSid loaded) {
        assertThat(loaded, notNullValue());
        assertThat(loaded.getName(), is(ROLE_NAME));
        assertThat(loaded.getAccessType(), is(RunAccessType.SSH));
        assertFalse(loaded.isPrincipal());
    }

    private RunnerSid aclSidEntity(final String sidName, final boolean principal) {
        final RunnerSid aclSidEntity = new RunnerSid();
        aclSidEntity.setName(sidName);
        aclSidEntity.setPrincipal(principal);
        aclSidEntity.setAccessType(RunAccessType.SSH);
        return aclSidEntity;
    }
}
