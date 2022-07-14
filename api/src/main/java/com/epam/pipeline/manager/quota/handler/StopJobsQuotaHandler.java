/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota.handler;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StopJobsQuotaHandler implements QuotaHandler {

    private final UserManager userManager;
    private final MetadataManager metadataManager;
    private final PipelineRunManager pipelineRunManager;
    private final String billingCenterKey;

    public StopJobsQuotaHandler(final UserManager userManager,
                                final MetadataManager metadataManager,
                                final PipelineRunManager pipelineRunManager,
                                @Value("${billing.center.key:}") final String billingCenterKey) {
        this.userManager = userManager;
        this.metadataManager = metadataManager;
        this.pipelineRunManager = pipelineRunManager;
        this.billingCenterKey = billingCenterKey;
    }

    @Override
    public QuotaActionType type() {
        return QuotaActionType.STOP_JOBS;
    }

    @Override
    public void applyActionType(final AppliedQuota appliedQuota, final QuotaActionType type) {
        log.info("Stopping all jobs affected by quota {}.", appliedQuota.getQuota());
        final List<PipelineRun> runsToStop = findAffectedRuns(appliedQuota);
        log.info("{} run(s) will be stopped.", runsToStop.size());
        final Map<String, PipelineUser> users = new HashMap<>();
        runsToStop.forEach(run -> {
            if (isAdminRun(run, users)) {
                return;
            }
            log.info("Stopping run {}.", run.getId());
            //TODO: add logs to run
            pipelineRunManager.updatePipelineStatusIfNotFinal(run.getId(), TaskStatus.STOPPED);
        });
    }

    private boolean isAdminRun(final PipelineRun run, final Map<String, PipelineUser> users) {
        final String owner = run.getOwner();
        final PipelineUser user = users.get(owner);
        if (Objects.nonNull(user)) {
            return user.isAdmin();
        }
        final PipelineUser runUser = userManager.loadUserByName(owner);
        if (Objects.nonNull(runUser)) {
            users.put(owner, runUser);
            return runUser.isAdmin();
        }
        return false;
    }

    private List<PipelineRun> findAffectedRuns(final AppliedQuota appliedQuota) {
        final List<PipelineRun> activeRuns = pipelineRunManager.loadRunsByStatuses(
                Collections.singletonList(TaskStatus.RUNNING));
        final Quota quota = appliedQuota.getQuota();
        if (QuotaType.OVERALL.equals(quota.getType())) {
            return activeRuns;
        }
        final Collection<PipelineUser> affectedUsers = findAffectedUsers(quota)
                .stream()
                .filter(user -> !user.isAdmin())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(affectedUsers)) {
            return Collections.emptyList();
        }
        final Set<String> userNames = affectedUsers.stream()
                .map(user -> user.getUserName().toUpperCase())
                .collect(Collectors.toSet());
        return activeRuns.stream()
                .filter(run -> userNames.contains(run.getOwner().toUpperCase()))
                .collect(Collectors.toList());
    }

    private Collection<PipelineUser> findAffectedUsers(final Quota quota) {
        switch (quota.getType()) {
            case USER:
                return Collections.singletonList(userManager.loadUserByName(quota.getSubject()));
            case GROUP:
                return userManager.loadUsersByGroupOrRole(quota.getSubject());
            case BILLING_CENTER:
                final List<Long> userIds = ListUtils.emptyIfNull(
                        metadataManager.searchMetadataByClassAndKeyValue(
                                AclClass.PIPELINE_USER, billingCenterKey, quota.getSubject()))
                        .stream()
                        .map(EntityVO::getEntityId)
                        .collect(Collectors.toList());
                return userManager.loadUsersById(userIds);
            case OVERALL:
            default:
                return Collections.emptyList();
        }
    }
}
