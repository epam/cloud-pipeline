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
package com.epam.pipeline.elasticsearchagent;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.model.git.GitCommit;
import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import com.epam.pipeline.elasticsearchagent.model.git.GitProject;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import org.junit.platform.commons.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;

public final class ObjectCreationUtils {

    private static final String PRETTY_NAME_ATTRIBUTE = "Name";

    public static PipelineUser buildPipelineUser(final String name, final String prettyName,
                                                 final List<String> groups) {
        PipelineUser user = new PipelineUser();
        user.setId(1L);
        user.setUserName(name);
        user.setGroups(groups);

        if (StringUtils.isNotBlank(prettyName)) {
            user.setAttributes(Collections.singletonMap(PRETTY_NAME_ATTRIBUTE, prettyName));
        }

        return user;
    }

    public static PermissionsContainer buildPermissions(final Set<String> allowedUsers, final Set<String> deniedUsers,
                                                        final Set<String> allowedGroups,
                                                        final Set<String> deniedGroups) {
        PermissionsContainer permissionsContainer = new PermissionsContainer();
        permissionsContainer.setAllowedUsers(allowedUsers);
        permissionsContainer.setDeniedUsers(deniedUsers);
        permissionsContainer.setAllowedGroups(allowedGroups);
        permissionsContainer.setDeniedGroups(deniedGroups);
        return permissionsContainer;
    }

    public static EntityPermissionVO buildEntityPermissionVO(final String owner,
                                                             final Set<String> allowedUsers,
                                                             final Set<String> deniedUsers,
                                                             final Set<String> allowedGroups,
                                                             final Set<String> deniedGroups) {
        Set<AclPermissionEntry> permissions = new HashSet<>();
        allowedUsers.forEach(user -> permissions.add(getPermissionEntry(user, true, 1)));
        deniedUsers.forEach(user -> permissions.add(getPermissionEntry(user, true, 2)));
        allowedGroups.forEach(group -> permissions.add(getPermissionEntry(group, false, 1)));
        deniedGroups.forEach(group -> permissions.add(getPermissionEntry(group, false, 2)));

        EntityPermissionVO entityPermissionVO = new EntityPermissionVO();
        entityPermissionVO.setOwner(owner);
        entityPermissionVO.setPermissions(permissions);

        return entityPermissionVO;
    }

    public static MetadataEntry buildMetadataEntry(final AclClass aclClass,
                                                   final Long entityId,
                                                   final String metadataValue) {
        EntityVO entityVO = new EntityVO(entityId, aclClass);

        MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(entityVO);
        metadataEntry.setData(Collections.singletonMap(TEST_NAME, new PipeConfValue(TEST_NAME, metadataValue)));
        return metadataEntry;
    }

    public static GitEvent buildGitEvent(final String repositoryUrl,
                                         final List<String> filePaths,
                                         final GitEventType gitEventType,
                                         final String branch) {
        GitProject gitProject = new GitProject();
        gitProject.setRepositoryUrl(repositoryUrl);

        GitCommit commit = new GitCommit();
        commit.setTimestamp(LocalDateTime.now());
        commit.setAdded(filePaths);

        GitEvent gitEvent = new GitEvent();
        gitEvent.setProject(gitProject);
        gitEvent.setEventType(gitEventType);
        gitEvent.setReference(branch);
        gitEvent.setCommits(Collections.singletonList(commit));

        return gitEvent;
    }

    private static AclPermissionEntry getPermissionEntry(final String userName,
                                                         final boolean isPrincipal,
                                                         final Integer mask) {
        final AclSid aclSid = new AclSid(userName, isPrincipal);
        return new AclPermissionEntry(aclSid, mask);
    }

    private ObjectCreationUtils() {
        // no-op
    }
}
