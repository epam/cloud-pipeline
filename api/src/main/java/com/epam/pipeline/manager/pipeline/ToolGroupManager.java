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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.mapper.ToolGroupWithIssuesMapper;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@AclSync
public class ToolGroupManager implements SecuredEntityManager {
    private static final Pattern REPOSITORY_AND_GROUP = Pattern.compile("^(.*)\\/([^/]*)$");
    private static final Pattern GROUP_AND_IMAGE = Pattern.compile("^(.*)\\/(.*)$");

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private DockerRegistryManager dockerRegistryManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private ToolGroupWithIssuesMapper toolGroupWithIssuesMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public ToolGroup create(final ToolGroup group) {
        Assert.notNull(group.getName(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                                                                 "name"));
        Assert.notNull(group.getRegistryId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                                                                 "registryId"));

        DockerRegistry registry = dockerRegistryManager.load(group.getRegistryId());
        group.setParent(registry);
        if (!StringUtils.hasText(group.getOwner())) {
            group.setOwner(authManager.getAuthorizedUser());
        }
        Assert.isTrue(!toolGroupDao.loadToolGroup(group.getName(), group.getRegistryId()).isPresent(),
                      messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_ALREADY_EXIST, group.getName(),
                                               registry.getName()));

        toolGroupDao.createToolGroup(group);
        return group;
    }

    /**
     * Updates a {@link ToolGroup}. The fields, allowed for update are: description
     * @param toolGroup
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ToolGroup updateToolGroup(final ToolGroup toolGroup) {
        Assert.notNull(toolGroup.getId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                                                                 "id"));

        ToolGroup old = toolGroupDao.loadToolGroup(toolGroup.getId()).orElseThrow(() -> new IllegalArgumentException(
            messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_NOT_FOUND, toolGroup.getId())));

        old.setDescription(toolGroup.getDescription());

        toolGroupDao.updateToolGroup(old);
        return old;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ToolGroup delete(final String id, final boolean force) {
        final ToolGroup group = loadByNameOrId(id);
        handleChildTools(group, force);
        toolGroupDao.deleteToolGroup(group.getId());
        return group;
    }

    public List<ToolGroup> loadByRegistryId(Long registryId) {
        String currentUserName = makePrivateGroupName();
        return toolGroupDao.loadToolGroups(registryId).stream()
            .peek(g -> g.setPrivateGroup(g.getName().equalsIgnoreCase(currentUserName)))
            .collect(Collectors.toList());
    }

    public List<ToolGroup> loadByRegistryNameOrId(String registry) {
        if (NumberUtils.isDigits(registry)) {
            return loadByRegistryId(Long.parseLong(registry));
        } else {
            return loadByRegistryName(registry);
        }
    }

    private List<ToolGroup> loadByRegistryName(String registryName) {
        String currentUserName = makePrivateGroupName();
        DockerRegistry registry = dockerRegistryManager.loadByNameOrId(registryName);

        return toolGroupDao.loadToolGroups(registry.getId()).stream()
            .peek(g -> g.setPrivateGroup(g.getName().equalsIgnoreCase(currentUserName)))
            .collect(Collectors.toList());
    }

    public ToolGroup loadPrivate(Long registryId) {
        return toolGroupDao.loadToolGroup(makePrivateGroupName(), registryId)
            .map(g -> {
                g.setTools(toolManager.loadToolsByGroup(g.getId()));
                g.setPrivateGroup(true);
                return g;
            })
            .orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_PRIVATE_TOOL_GROUP_NOT_FOUND, registryId)));
    }

    public ToolGroupWithIssues loadToolsWithIssuesCount(Long id) {
        ToolGroup group = toolGroupDao.loadToolGroup(id).orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_NOT_FOUND, id)));
        group.setPrivateGroup(group.getName().equalsIgnoreCase(makePrivateGroupName()));
        ToolGroupWithIssues groupWithIssues = toolGroupWithIssuesMapper.toToolGroupWithIssues(group);
        groupWithIssues.setToolsWithIssues(toolManager.loadToolsWithIssuesCountByGroup(id));
        return groupWithIssues;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ToolGroup createPrivate(Long registryId) {
        ToolGroup privateGroup = new ToolGroup();
        String privateGroupName = makePrivateGroupName();
        privateGroup.setName(privateGroupName);
        privateGroup.setRegistryId(registryId);
        privateGroup.setOwner(authManager.getAuthorizedUser());
        privateGroup.setPrivateGroup(true);

        DockerRegistry registry = dockerRegistryManager.load(registryId);
        Assert.notNull(registry, messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, registryId));

        Assert.isTrue(!toolGroupDao.loadToolGroup(privateGroup.getName(), privateGroup.getRegistryId()).isPresent(),
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_ALREADY_EXIST, privateGroup.getName(),
                        registry.getName()));

        privateGroup.setParent(registry);

        toolGroupDao.createToolGroup(privateGroup);

        makePrivate(privateGroup);

        return privateGroup;
    }

    /**
     * Loads a ToolGroup with nested Tools
     * @param id ID of the group
     * @return a ToolGroup with nested Tools
     */
    @Override
    public ToolGroup load(Long id) {
        ToolGroup group = toolGroupDao.loadToolGroup(id).orElseThrow(() -> new IllegalArgumentException(
            messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_NOT_FOUND, id)));

        group.setTools(toolManager.loadToolsByGroup(group.getId()));
        group.setPrivateGroup(group.getName().equalsIgnoreCase(
                makePrivateGroupName()));

        return group;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ToolGroup changeOwner(Long id, String owner) {
        ToolGroup group = load(id);

        group.setOwner(owner);
        toolGroupDao.updateToolGroup(group);

        return group;
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.TOOL_GROUP;
    }

    @Override
    public ToolGroup loadByNameOrId(String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            return load(Long.parseLong(identifier));
        } else {
            Pair<String, String> registryAndGroupName = getPrefixAndName(identifier);
            List<ToolGroup> groups = toolGroupDao.loadToolGroupsByNameAndRegistryName(registryAndGroupName.getRight(),
                                                                             registryAndGroupName.getLeft());
            Assert.isTrue(groups.size() <= 1,
                          messageHelper.getMessage(MessageConstants.ERROR_TOO_MANY_RESULTS, identifier));
            Assert.isTrue(!groups.isEmpty(),
                          messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_NOT_FOUND, identifier));

            ToolGroup result = groups.get(0);
            result.setTools(toolManager.loadToolsByGroup(result.getId()));
            result.setPrivateGroup(result.getName().equalsIgnoreCase(makePrivateGroupName()));

            return result;
        }
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ToolGroup loadWithParents(final Long id) {
        Optional<ToolGroup> loadResult = toolGroupDao.loadToolGroup(id);
        if (loadResult.isPresent()) {
            ToolGroup toolGroup = loadResult.get();
            toolGroup.setParent(new DockerRegistry(toolGroup.getRegistryId()));
        }
        return loadResult.orElse(null);
    }

    /**
     * Splits image into group and image name,
     * e.g. for 'library/centos' group is 'library' and image is 'centos'
     * @param repository
     * @return
     */
    public ImmutablePair<String, String> getGroupAndTool(String repository) {
        Matcher matcher = GROUP_AND_IMAGE.matcher(repository);
        String toolGroupName;
        String toolName;
        if (matcher.find()) {
            toolGroupName = matcher.group(1);
            toolName = matcher.group(2);
        } else {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_IS_NOT_PROVIDED, repository));
        }
        return new ImmutablePair<>(toolGroupName, toolName);
    }

    /**
     * Loads {@link ToolGroup} by registry path ans full immage, e.g. 'library/image',
     * where 'library' is group name
     * @param registry
     * @param image
     * @return
     */
    public ToolGroup loadToolGroupByImage(String registry, String image) {
        ImmutablePair<String, String> groupAndTool = getGroupAndTool(image);
        String groupName = groupAndTool.getLeft();
        return loadByNameOrId(registry + Constants.PATH_DELIMITER + groupName);
    }

    /**
     * Checks that {@link ToolGroup} with specified registryName and groupName exists,
     * @param registryName name of a docker registry where group is can be
     * @param groupName name of docker images group to be checked
     * @return true if group already exists otherwise return false
     */
    public boolean doesToolGroupExist(String registryName, String groupName) {
        List<ToolGroup> groups = toolGroupDao.loadToolGroupsByNameAndRegistryName(groupName, registryName);
        return CollectionUtils.isNotEmpty(groups);
    }
    
    public boolean isGroupPrivate(ToolGroup group) {
        return group.getName().equalsIgnoreCase(makePrivateGroupName());
    }

    /**
     * Splits registry and name from a string of pattern 'registry:5000/name'
     * @param identifier an identifier
     * @return a pair of registry and name
     */
    public static Pair<String, String> getPrefixAndName(String identifier) {
        Matcher matcher = REPOSITORY_AND_GROUP.matcher(identifier);

        if (matcher.find()) {
            return new ImmutablePair<>(matcher.group(1), matcher.group(2));
        } else {
            return new ImmutablePair<>(null, identifier);
        }
    }

    private void makePrivate(ToolGroup group) {
        PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setUserName("ROLE_USER");
        grantVO.setPrincipal(false);
        grantVO.setMask(AclPermission.ALL_DENYING_PERMISSIONS.getMask());
        grantVO.setAclClass(AclClass.TOOL_GROUP);
        grantVO.setId(group.getId());

        grantPermissionManager.setPermissions(grantVO);
    }

    private String makePrivateGroupName() {
        String userName = authManager.getAuthorizedUser();
        String groupName = userName.trim().toLowerCase();
        return groupName.replaceAll("[^a-z0-9\\-]+", "-");
    }

    private void handleChildTools(final ToolGroup group, final boolean force) {
        if (CollectionUtils.isEmpty(group.getTools())) {
            return;
        }
        Assert.isTrue(force, messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_NOT_EMPTY));
        ListUtils.emptyIfNull(group.getTools())
                .forEach(tool -> toolManager.delete(tool));

    }
}
