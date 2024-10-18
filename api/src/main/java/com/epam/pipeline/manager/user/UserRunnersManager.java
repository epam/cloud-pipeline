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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.user.RunnerSidVO;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.mapper.user.RunnerSidMapper;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserRunnersManager {
    private final PipelineUserRepository pipelineUserRepository;
    private final MessageHelper messageHelper;
    private final RunnerSidMapper mapper;

    public List<RunnerSid> getRunners(final Long id) {
        return getUserOrThrow(id).getAllowedRunners();
    }

    public boolean hasUserAsRunner(final PipelineUser user, final String runAsUser) {
        return ListUtils.emptyIfNull(getUserByNameOrThrow(runAsUser).getAllowedRunners()).stream()
                .anyMatch(aclSidEntity -> isRunnerAllowed(aclSidEntity, user));
    }

    @Transactional
    public List<RunnerSidVO> saveRunners(final Long id, final List<RunnerSidVO> runners) {
        final PipelineUser user = getUserOrThrow(id);
        ListUtils.emptyIfNull(runners).forEach(this::validateRunner);
        final List<RunnerSid> entities = ListUtils.emptyIfNull(runners)
                .stream()
                .map(mapper::toEntity)
                .collect(Collectors.toList());
        user.setAllowedRunners(entities);
        pipelineUserRepository.save(user);
        return runners;
    }

    public RunnerSid findRunnerSid(final String userName, final String runAsUser) {
        final PipelineUser user = getUserByNameOrThrow(userName);
        final List<RunnerSid> allowedRunners = getUserByNameOrThrow(runAsUser).getAllowedRunners();
        return ListUtils.emptyIfNull(allowedRunners).stream()
                .filter(runnerSid -> isRunnerAllowedForUser(runnerSid, userName))
                .findAny()
                .orElse(ListUtils.emptyIfNull(allowedRunners).stream()
                        .filter(runnerSid -> isRunnerAllowedForRoles(runnerSid, user.getAuthorities()))
                        .findAny()
                        .orElse(null));
    }

    private PipelineUser getUserOrThrow(final Long id) {
        final PipelineUser user = pipelineUserRepository.findOne(id);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_ID_NOT_FOUND, id));
        return user;
    }

    private PipelineUser getUserByNameOrThrow(final String userName) {
        return pipelineUserRepository.findByUserName(userName).orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName)));
    }

    private boolean isRunnerAllowed(final RunnerSid runnersAclSid, final PipelineUser user) {
        return isRunnerAllowedForUser(runnersAclSid, user.getUserName())
                || isRunnerAllowedForRoles(runnersAclSid, user.getAuthorities());
    }

    private boolean isRunnerAllowedForUser(final RunnerSid runnerSid, final String userName) {
        return runnerSid.isPrincipal() && StringUtils.equalsIgnoreCase(runnerSid.getName(), userName);
    }

    private boolean isRunnerAllowedForRoles(final RunnerSid runnersAclSid, final Set<String> authorities) {
        return !runnersAclSid.isPrincipal()
                && SetUtils.emptyIfNull(authorities).stream()
                .anyMatch(authority -> StringUtils.equalsIgnoreCase(authority, runnersAclSid.getName()));
    }

    private void validateRunner(final RunnerSidVO runnerSid) {
        Assert.state(StringUtils.isNotBlank(runnerSid.getName()), messageHelper.getMessage(
                MessageConstants.ERROR_RUN_ALLOWED_SID_NAME_NOT_FOUND));
    }
}
