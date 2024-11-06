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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.user.RunnerSidVO;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.mapper.user.RunnerSidMapper;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.assertj.core.api.Assertions.assertThat;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserRunnersManagerTest {
    private static final String USER_NAME = "user";
    private static final String ROLE_NAME = "role";
    private static final String SERVICE_ACCOUNT = "service";

    private final PipelineUserRepository pipelineUserRepository = mock(PipelineUserRepository.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final RunnerSidMapper runnerSidMapper = mock(RunnerSidMapper.class);
    private final UserRunnersManager manager = new UserRunnersManager(
            pipelineUserRepository, messageHelper, runnerSidMapper);
    private final RunnerSid userRunnerSid = RunnerSid.builder().name(USER_NAME).principal(true).build();
    private final RunnerSidVO userRunnerSidVO = RunnerSidVO.builder().name(USER_NAME).principal(true).build();
    private final RunnerSid roleRunnerSid = RunnerSid.builder().name(ROLE_NAME).principal(false).build();

    @Test
    public void shouldGetRunners() {
        when(pipelineUserRepository.findOne(ID)).thenReturn(serviceAccountWithAllowedUser());

        final List<RunnerSid> runners = manager.getRunners(ID);

        assertThat(runners).hasSize(1).contains(userRunnerSid);
    }

    @Test
    public void shouldSaveRunners() {
        when(pipelineUserRepository.findOne(ID)).thenReturn(UserCreatorUtils.getPipelineUser());
        when(runnerSidMapper.toEntity(userRunnerSidVO)).thenReturn(userRunnerSid);
        final List<RunnerSidVO> runners = manager.saveRunners(ID, Collections.singletonList(userRunnerSidVO));

        verify(pipelineUserRepository).save((PipelineUser) any());
        assertThat(runners).hasSize(1).contains(userRunnerSidVO);
    }

    @Test
    public void shouldReturnTrueIfUserRunnerPresents() {
        when(pipelineUserRepository.findByUserName(SERVICE_ACCOUNT))
                .thenReturn(Optional.of(serviceAccountWithAllowedUser()));
        final PipelineUser currentUser = UserCreatorUtils.getPipelineUser(USER_NAME);

        assertThat(manager.hasUserAsRunner(currentUser, SERVICE_ACCOUNT)).isTrue();
    }

    @Test
    public void shouldReturnFalseIfUserRunnerNotPresents() {
        when(pipelineUserRepository.findByUserName(TEST_STRING))
                .thenReturn(Optional.of(UserCreatorUtils.getPipelineUser()));
        final PipelineUser currentUser = UserCreatorUtils.getPipelineUser(USER_NAME);

        assertThat(manager.hasUserAsRunner(currentUser, TEST_STRING)).isFalse();
    }

    @Test
    public void shouldReturnTrueIfRunnerPresentsInRoles() {
        final PipelineUser serviceAccount = serviceAccountWithAllowedRole();
        when(pipelineUserRepository.findByUserName(SERVICE_ACCOUNT)).thenReturn(Optional.of(serviceAccount));

        final PipelineUser currentUser = UserCreatorUtils.getPipelineUser(USER_NAME);
        currentUser.setRoles(Collections.singletonList(new Role(ROLE_NAME)));

        assertThat(manager.hasUserAsRunner(currentUser, SERVICE_ACCOUNT)).isTrue();
    }

    @Test
    public void shouldFindRunnerSidByUser() {
        final PipelineUser serviceAccount = serviceAccountWithAllowedUser();
        when(pipelineUserRepository.findByUserName(SERVICE_ACCOUNT)).thenReturn(Optional.of(serviceAccount));

        final PipelineUser currentUser = UserCreatorUtils.getPipelineUser(USER_NAME);
        when(pipelineUserRepository.findByUserName(USER_NAME)).thenReturn(Optional.of(currentUser));

        assertThat(manager.findRunnerSid(USER_NAME, SERVICE_ACCOUNT)).isEqualTo(userRunnerSid);
    }

    @Test
    public void shouldFindRunnerSidByRole() {
        final PipelineUser serviceAccount = serviceAccountWithAllowedRole();
        when(pipelineUserRepository.findByUserName(SERVICE_ACCOUNT)).thenReturn(Optional.of(serviceAccount));

        final PipelineUser currentUser = UserCreatorUtils.getPipelineUser(USER_NAME);
        currentUser.setRoles(Collections.singletonList(new Role(ROLE_NAME)));
        when(pipelineUserRepository.findByUserName(USER_NAME)).thenReturn(Optional.of(currentUser));

        assertThat(manager.findRunnerSid(USER_NAME, SERVICE_ACCOUNT)).isEqualTo(roleRunnerSid);
    }

    private PipelineUser serviceAccountWithAllowedUser() {
        final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(SERVICE_ACCOUNT);
        pipelineUser.setAllowedRunners(Collections.singletonList(userRunnerSid));
        return pipelineUser;
    }

    private PipelineUser serviceAccountWithAllowedRole() {
        final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(SERVICE_ACCOUNT);
        pipelineUser.setAllowedRunners(Collections.singletonList(roleRunnerSid));
        return pipelineUser;
    }
}
