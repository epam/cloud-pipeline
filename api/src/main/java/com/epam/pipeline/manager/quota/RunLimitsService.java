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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.RoleManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunLimitsService {

    private static final String USER_LIMIT_KEY = "<user-contextual-limit>";
    private static final String USER_GLOBAL_LIMIT_KEY = "<user-global-limit>";
    private static final String LAUNCH_MAX_RUNS_USER_LIMIT = "launch.max.runs.user";
    private static final String LAUNCH_MAX_RUNS_GROUP_LIMIT = "launch.max.runs.group";
    private static final List<TaskStatus> ACTIVE_RUN_STATUSES = Arrays.asList(TaskStatus.RESUMING, TaskStatus.RUNNING);
    private final PipelineRunManager runManager;
    private final RoleManager roleManager;
    private final ContextualPreferenceManager contextualPreferenceManager;
    private final MessageHelper messageHelper;
    private final AuthManager authManager;
    private final PreferenceManager preferenceManager;

    public void checkRunLaunchLimits(final Integer newInstancesCount) {
        if (authManager.isAdmin()) {
            return;
        }
        final PipelineUser user = authManager.getCurrentUser();
        final Map<String, Integer> currentLimits = getCurrentUserLaunchLimits(user, false);
        currentLimits.entrySet().stream()
            .findFirst()
            .filter(e -> exceedsUserLimit(user.getUserName(), newInstancesCount, e.getValue()))
            .ifPresent(e -> {
                final String userName = user.getUserName();
                final String limitName = e.getKey();
                final Integer limitValue = e.getValue();
                log.info("Launch of new jobs is restricted as [{}] user will exceed [{}] runs limit [{}]",
                         userName, limitName, limitValue);
                throw new LaunchQuotaExceededException(messageHelper.getMessage(
                    MessageConstants.ERROR_RUN_LAUNCH_USER_LIMIT_EXCEEDED, userName, limitName, limitValue));
            });
    }

    public Map<String, Integer> getCurrentUserLaunchLimits(final boolean loadAll) {
        if (authManager.isAdmin()) {
            return Collections.emptyMap();
        }
        return getCurrentUserLaunchLimits(authManager.getCurrentUser(), loadAll);
    }

    private Map<String, Integer> getCurrentUserLaunchLimits(final PipelineUser user, final boolean loadAll) {

        final Optional<Integer> userContextualLimit = findUserLimit(user.getId());
        if (requiresSingleLimitOnly(userContextualLimit, loadAll)) {
            return returnLimitAsMap(userContextualLimit, USER_LIMIT_KEY);
        }

        final Map<String, Integer> groupsLimits = findUserGroupsLimits(getGroups(user))
            .collect(Collectors.toMap(GroupLimit::getGroupName, GroupLimit::getRunsLimit));
        if (MapUtils.isNotEmpty(groupsLimits) && !loadAll) {
            return findMostStrictGroupLimit(groupsLimits);
        }

        final Optional<Integer> userGlobalLimit = getUserGlobalLimit();
        if (requiresSingleLimitOnly(userGlobalLimit, loadAll)) {
            return returnLimitAsMap(userGlobalLimit, USER_GLOBAL_LIMIT_KEY);
        }

        addLimitIfPresent(groupsLimits, userContextualLimit, USER_LIMIT_KEY);
        addLimitIfPresent(groupsLimits, userGlobalLimit, USER_GLOBAL_LIMIT_KEY);
        return groupsLimits;
    }
    
    private boolean requiresSingleLimitOnly(final Optional<Integer> limit, final boolean loadAll) {
        return limit.isPresent() && !loadAll;
    }

    private Map<String, Integer> findMostStrictGroupLimit(final Map<String, Integer> groupsLimits) {
        return groupsLimits.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(e -> Collections.singletonMap(e.getKey(), e.getValue()))
            .get();
    }

    private Map<String, Integer> returnLimitAsMap(final Optional<Integer> limit, final String limitKey) {
        return limit.map(limitValue -> Collections.singletonMap(limitKey, limitValue)).get();
    }

    private void addLimitIfPresent(final Map<String, Integer> allLimits, final Optional<Integer> limit,
                                   final String limitKey) {
        limit.ifPresent(limitValue -> allLimits.put(limitKey, limitValue));
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

    private Optional<Integer> findUserLimit(final Long userId) {
        return findLimitPreference(LAUNCH_MAX_RUNS_USER_LIMIT, ContextualPreferenceLevel.USER, userId)
            .map(ContextualPreference::getValue)
            .filter(NumberUtils::isNumber)
            .map(Integer::parseInt);
    }

    private Optional<Integer> getUserGlobalLimit() {
        return preferenceManager.findPreference(SystemPreferences.LAUNCH_MAX_RUNS_USER_GLOBAL_LIMIT);
    }

    private Stream<GroupLimit> findUserGroupsLimits(final Set<String> groups) {
        final Map<String, ExtendedRole> groupIdsMapping = getAllMatchingGroupsMapping(groups);
        return contextualPreferenceManager.load(LAUNCH_MAX_RUNS_GROUP_LIMIT).stream()
            .filter(pref -> isTargetGroupPreference(pref, groupIdsMapping))
            .map(pref -> mapToLimitDetails(pref, groupIdsMapping));
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

    private Optional<ContextualPreference> findLimitPreference(final String pref,
                                                               final ContextualPreferenceLevel resourceLevel,
                                                               final Long resourceId) {
        final ContextualPreferenceExternalResource preferenceResource =
            new ContextualPreferenceExternalResource(resourceLevel, resourceId.toString());
        return contextualPreferenceManager.find(pref, preferenceResource);
    }

    private boolean exceedsUserLimit(final String userName, final Integer newInstancesCount, final Integer limit) {
        return getActiveRunsForUsers(Collections.singletonList(userName)) + newInstancesCount > limit;
    }

    private Integer getActiveRunsForUsers(final List<String> userNames) {
        final PipelineRunFilterVO runFilterVO = new PipelineRunFilterVO();
        runFilterVO.setStatuses(ACTIVE_RUN_STATUSES);
        if (CollectionUtils.isNotEmpty(userNames)) {
            runFilterVO.setOwners(userNames);
        }
        return runManager.countPipelineRuns(runFilterVO);
    }

    private boolean isTargetGroupPreference(final ContextualPreference preference,
                                            final Map<String, ExtendedRole> groupIdsMapping) {
        final ContextualPreferenceExternalResource resource = preference.getResource();
        return resource.getLevel().equals(ContextualPreferenceLevel.ROLE)
               && groupIdsMapping.containsKey(resource.getResourceId());
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
