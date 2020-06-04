/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.run;

import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.filter.AclSecuredFilter;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunPermissionManager {

    private static final RunVisibilityPolicy DEFAULT_POLICY = RunVisibilityPolicy.INHERIT;

    private final PipelineRunManager runManager;
    private final PipelineApiService pipelineApiService;
    private final CheckPermissionHelper permissionsHelper;
    private final AuthManager authManager;
    private final DockerRegistryManager registryManager;
    private final ToolGroupManager toolGroupManager;
    private final ToolManager toolManager;
    private final ContextualPreferenceManager preferenceManager;

    /**
     * Run permissions: owner and admin have full access to PipelineRun
     * @param run
     * @param permissionName
     * @return
     */
    public boolean runPermission(final PipelineRun run, final String permissionName) {
        //add assert run not null
        if (permissionsHelper.isOwnerOrAdmin(run.getOwner()) || isRunSshAllowed(run)) {
            return true;
        }
        return inheritPermissions() && Optional.ofNullable(runManager.loadRunParent(run))
                .map(parent -> permissionsHelper.isAllowed(permissionName, parent))
                .orElse(false);
    }

    public boolean runPermission(final Long runId, final String permissionName) {
        return runPermission(runManager.loadPipelineRun(runId), permissionName);
    }

    public boolean runStatusPermission(Long runId, TaskStatus taskStatus, String permissionName) {
        final PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
        if (taskStatus.isFinal()) {
            return permissionsHelper.isOwnerOrAdmin(pipelineRun.getOwner());
        }
        return runPermission(pipelineRun, permissionName);
    }

    public boolean isRunSshAllowed(Long runId) {
        return isRunSshAllowed(runManager.loadPipelineRun(runId));
    }

    public boolean isRunSshAllowed(PipelineRun pipelineRun) {
        if (permissionsHelper.isOwnerOrAdmin(pipelineRun.getOwner())) {
            return true;
        }
        final List<RunSid> sshSharedSids = ListUtils.emptyIfNull(pipelineRun.getRunSids())
                .stream()
                .filter(sid -> RunAccessType.SSH.equals(sid.getAccessType()))
                .collect(toList());
        if (CollectionUtils.isEmpty(sshSharedSids)) {
            return false;
        }
        return Optional.ofNullable(authManager.getCurrentUser())
                .map(currentUser -> isSharedWithPrincipal(sshSharedSids, currentUser) ||
                        isSharedWithGroup(sshSharedSids, currentUser))
                .orElse(false);
    }

    /**
     * Method will check permission for a {@link Tool} if it is registered, or for {@link ToolGroup}
     * if this is a new {@link Tool}. If both {@link Tool} and {@link ToolGroup} do not exist,
     * permission for {@link DockerRegistry} will be checked. Image is expected in format 'group/image'.
     * @param registryId
     * @param image
     * @param permission
     * @return
     */
    public boolean commitPermission(Long registryId, String image, String permission) {
        DockerRegistry registry = registryManager.load(registryId);
        try {
            String trimmedImage = image.startsWith(registry.getPath()) ?
                    image.substring(registry.getPath().length() + 1) : image;
            ToolGroup toolGroup = toolGroupManager.loadToolGroupByImage(registry.getPath(), trimmedImage);
            Optional<Tool> tool = toolManager.loadToolInGroup(trimmedImage, toolGroup.getId());
            return tool.map(t -> t.isNotSymlink() && permissionsHelper.isAllowed(permission, t))
                    .orElseGet(() -> permissionsHelper.isAllowed(permission, toolGroup));
        } catch (IllegalArgumentException e) {
            //case when tool group doesn't exist
            log.trace(e.getMessage(), e);
            return permissionsHelper.isAllowed(permission, registry);
        }
    }

    public void extendFilter(final AclSecuredFilter filter) {
        if (permissionsHelper.isAdmin()) {
            return;
        }
        filter.setOwnershipFilter(authManager.getAuthorizedUser().toLowerCase());
        if (inheritPermissions()) {
            final List<Long> allowedPipelinesList =
                    pipelineApiService.loadAllPipelinesWithoutVersion()
                            .stream()
                            .map(BaseEntity::getId)
                            .collect(toList());
            filter.setAllowedPipelines(allowedPipelinesList);
        }
    }

    private boolean isSharedWithPrincipal(final List<RunSid> sshSharedSids, final PipelineUser currentUser) {
        return sshSharedSids.stream().anyMatch(sid -> Boolean.TRUE.equals(sid.getIsPrincipal()) &&
                sid.getName().equalsIgnoreCase(currentUser.getUserName()));
    }

    private boolean isSharedWithGroup(final List<RunSid> sshSharedSids, final PipelineUser currentUser) {
        final Set<String> userClaims = new HashSet<>();
        userClaims.addAll(ListUtils.emptyIfNull(currentUser.getRoles()).stream().map(Role::getName)
                .collect(toList()));
        userClaims.addAll(ListUtils.emptyIfNull(currentUser.getGroups()));
        return sshSharedSids.stream().anyMatch(sid -> Boolean.FALSE.equals(sid.getIsPrincipal()) &&
                userClaims.contains(sid.getName()));
    }

    private boolean inheritPermissions() {
        final ContextualPreference visibilityPreference = preferenceManager.search(
                Collections.singletonList(SystemPreferences.RUN_VISIBILITY_POLICY.getKey()));
        final RunVisibilityPolicy visibilityPolicy = Optional.ofNullable(visibilityPreference)
                .map(ContextualPreference::getValue)
                .filter(value -> EnumUtils.isValidEnum(RunVisibilityPolicy.class, value))
                .map(RunVisibilityPolicy::valueOf)
                .orElse(DEFAULT_POLICY);
        return RunVisibilityPolicy.INHERIT == visibilityPolicy;
    }
}
