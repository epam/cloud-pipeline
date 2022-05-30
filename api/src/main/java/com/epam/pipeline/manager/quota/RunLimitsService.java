/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.exception.quota.LaunchQuotaExceededException;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.RoleManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunLimitsService {

    private static final List<TaskStatus> ACTIVE_RUN_STATUSES = new ArrayList<>(Arrays.asList(TaskStatus.RESUMING,
                                                                                              TaskStatus.RUNNING));
    private final PipelineRunManager runManager;
    private final RoleManager roleManager;
    private final ContextualPreferenceManager contextualPreferenceManager;
    private final MessageHelper messageHelper;
    private final AuthManager authManager;

    public void checkRunLaunchLimits(final Integer newInstancesCount) {
        if (authManager.isAdmin()) {
            return;
        }
        final PipelineUser user = authManager.getCurrentUser();
        final String userName = user.getUserName();
        final List<ContextualPreference> allPreferences = contextualPreferenceManager.loadAll();
        checkUserLimits(user.getId(), userName, newInstancesCount, allPreferences);
        checkUserGroupsLimits(userName, getGroups(user), newInstancesCount, allPreferences);
    }

    private Set<String> getGroups(final PipelineUser user) {
        final Set<String> groups = CollectionUtils.emptyIfNull(user.getRoles()).stream()
            .filter(role -> !role.isPredefined())
            .map(Role::getName)
            .map(this::getNameWithoutRolePrefix)
            .collect(Collectors.toSet());
        groups.addAll(user.getGroups());
        return groups;
    }

    private void checkUserLimits(final Long userId, final String userName, final Integer newInstancesCount,
                                 final List<ContextualPreference> allPreferences) {
        allPreferences.stream()
            .filter(pref -> pref.getName().equals(SystemPreferences.LAUNCH_MAX_RUNS_USER_LIMIT.getKey()))
            .filter(pref -> isUserPreference(pref, userId))
            .map(ContextualPreference::getValue)
            .filter(NumberUtils::isNumber)
            .map(Integer::parseInt)
            .filter(limit -> exceedsUserLimit(userName, newInstancesCount, limit))
            .findFirst()
            .ifPresent(limit -> {
                log.info("Launch of new jobs is restricted as [{}] user will exceed runs limit [{}]", userName, limit);
                throw new LaunchQuotaExceededException(messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_LAUNCH_USER_LIMIT_EXCEEDED, userName, limit));
            });
    }

    private void checkUserGroupsLimits(final String userName, final Set<String> groups, final Integer newInstancesCount,
                                       final List<ContextualPreference> allPreferences) {
        final Map<String, ExtendedRole> groupIdsMapping = getAllMatchingGroupsMapping(groups);
        allPreferences.stream()
            .filter(pref -> pref.getName().equals(SystemPreferences.LAUNCH_MAX_RUNS_GROUP_LIMIT.getKey()))
            .filter(pref -> isTargetGroupPreference(pref, groupIdsMapping.keySet()))
            .map(pref -> mapToLimitDetails(pref, groupIdsMapping))
            .filter(limitDetails -> exceedsGroupLimit(limitDetails, newInstancesCount))
            .findFirst()
            .ifPresent(limitDetails -> {
                log.info("Launch of new jobs is restricted as [{}] user will exceed group runs limit [{}, {}]",
                         userName, limitDetails.getGroupName(), limitDetails.getRunsLimit());
                throw new LaunchQuotaExceededException(messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_LAUNCH_GROUP_LIMIT_EXCEEDED, userName,
                    limitDetails.getGroupName(), limitDetails.getRunsLimit()));
            });
    }

    private Map<String, ExtendedRole> getAllMatchingGroupsMapping(final Set<String> groups) {
        return roleManager.loadAllRoles(true).stream()
            .filter(role -> !role.isPredefined())
            .filter(ExtendedRole.class::isInstance)
            .map(ExtendedRole.class::cast)
            .filter(role -> groups.contains(getNameWithoutRolePrefix(role.getName())))
            .collect(Collectors.toMap(role -> role.getId().toString(), Function.identity()));
    }

    private GroupLimit mapToLimitDetails(final ContextualPreference pref,
                                         final Map<String, ExtendedRole> groupIdsMapping) {
        final ExtendedRole groupDetails = groupIdsMapping.get(pref.getResource().getResourceId());
        final String groupName = getNameWithoutRolePrefix(groupDetails.getName());
        final List<String> groupUsers = groupDetails.getUsers().stream()
            .map(PipelineUser::getUserName)
            .collect(Collectors.toList());
        return new GroupLimit(groupName, Integer.parseInt(pref.getValue()), groupUsers);
    }

    private boolean exceedsGroupLimit(final GroupLimit limitDetails, final Integer newInstancesCount) {
        return getActiveRunsForUsers(limitDetails.getGroupUsers()) + newInstancesCount > limitDetails.getRunsLimit();
    }

    private boolean exceedsUserLimit(final String userName, final Integer newInstancesCount, final Integer limit) {
        return getActiveRunsForUsers(Collections.singletonList(userName)) + newInstancesCount > limit;
    }

    private Integer getActiveRunsForUsers(final List<String> userNames) {
        final PipelineRunFilterVO runFilterVO = new PipelineRunFilterVO();
        runFilterVO.setOwners(userNames);
        runFilterVO.setStatuses(ACTIVE_RUN_STATUSES);
        return runManager.countPipelineRuns(runFilterVO);
    }

    private boolean isUserPreference(final ContextualPreference preference, final Long userId) {
        return preference.getResource().equals(
            new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, userId.toString()));
    }

    private boolean isTargetGroupPreference(final ContextualPreference preference, final Set<String> targetGroupIds) {
        final ContextualPreferenceExternalResource resource = preference.getResource();
        return resource.getLevel().equals(ContextualPreferenceLevel.ROLE)
               && targetGroupIds.contains(resource.getResourceId());
    }

    private String getNameWithoutRolePrefix(final String fullName) {
        return fullName.substring(Role.ROLE_PREFIX.length());
    }

    @Value
    private static class GroupLimit {

        private final String groupName;
        private final Integer runsLimit;
        private final List<String> groupUsers;
    }
}
