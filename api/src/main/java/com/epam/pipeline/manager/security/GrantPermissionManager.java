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

package com.epam.pipeline.manager.security;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.filter.AclSecuredFilter;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineWithPermissions;
import com.epam.pipeline.entity.pipeline.RepositoryTool;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.security.acl.EntityPermission;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.event.EntityEventServiceManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.PipelineApiService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.AccessControlEntryImpl;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.AclDataAccessException;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class GrantPermissionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrantPermissionManager.class);
    private static final String OWNER = "OWNER";

    @Autowired private PermissionEvaluator permissionEvaluator;

    @Autowired private PermissionFactory permissionFactory;

    @Autowired private JdbcMutableAclServiceImpl aclService;

    @Autowired private PipelineApiService pipelineApiService;

    @Autowired private ToolManager toolManager;

    @Autowired private ToolGroupManager toolGroupManager;

    @Autowired private DockerRegistryManager registryManager;

    @Autowired private PipelineRunManager runManager;

    @Autowired private MessageHelper messageHelper;

    @Autowired private SidRetrievalStrategy sidRetrievalStrategy;

    @Autowired private PermissionsService permissionsService;

    @Autowired private AuthManager authManager;

    @Autowired private NodesManager nodesManager;

    @Autowired private UserManager userManager;

    @Autowired private EntityManager entityManager;

    @Autowired private MetadataEntityManager metadataEntityManager;

    @Autowired private IssueManager issueManager;

    @Autowired private PipelineWithPermissionsMapper pipelineWithPermissionsMapper;

    @Autowired private FolderManager folderManager;

    @Autowired private ConfigurationProviderManager configurationProviderManager;

    @Autowired private PermissionsHelper permissionsHelper;

    @Autowired private AbstractEntityPermissionMapper entityPermissionMapper;

    @Autowired private EntityEventServiceManager entityEventServiceManager;

    public boolean isActionAllowedForUser(AbstractSecuredEntity entity, String user, Permission permission) {
        return isActionAllowedForUser(entity, user, Collections.singletonList(permission));
    }

    /**
     * Checks whether actions is allowed for a user
     * Each {@link Permission} is processed individually since default permission resolving will
     * allow actions if any of {@link Permission} is allowed, and we need all {@link Permission}
     * to be granted
     * @param entity
     * @param user
     * @param permissions
     * @return
     */
    public boolean isActionAllowedForUser(AbstractSecuredEntity entity, String user, List<Permission> permissions) {
        List<Sid> sids = convertUserToSids(user);
        if (isAdmin(sids) || entity.getOwner().equalsIgnoreCase(user)) {
            return true;
        }
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);

        try {
            for (Permission permission : permissions) {
                boolean isGranted = acl.isGranted(Collections.singletonList(permission), sids, true);
                if (!isGranted) {
                    return false;
                }
            }
        } catch (AclDataAccessException e) {
            LOGGER.warn(e.getMessage());
            return false;
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AclSecuredEntry setPermissions(PermissionGrantVO grantVO) {
        validateParameters(grantVO);
        AbstractSecuredEntity entity = entityManager.load(grantVO.getAclClass(), grantVO.getId());
        Assert.isTrue(!entity.isLocked(),
                messageHelper.getMessage(MessageConstants.ERROR_ENTITY_IS_LOCKED,
                        entity.getAclClass(), entity.getId()));
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        Permission permission = permissionFactory.buildFromMask(grantVO.getMask());
        String sidName = grantVO.getUserName().toUpperCase();
        Sid sid = aclService.createOrGetSid(sidName, grantVO.getPrincipal());
        LOGGER.debug("Granting permissions for sid {}", sid);
        int sidEntryIndex = findSidEntry(acl, sid);
        if (sidEntryIndex != -1) {
            acl.deleteAce(sidEntryIndex);
        }
        acl.insertAce(Math.max(sidEntryIndex, 0), permission, sid, true);
        MutableAcl updatedAcl = aclService.updateAcl(acl);
        AclSecuredEntry aclSecuredEntry = convertAclToEntryForUser(entity, updatedAcl, sid);
        updateEventsWithChildrenAndIssues(entity);
        return aclSecuredEntry;
    }

    public AclSecuredEntry getPermissions(Long id, AclClass aclClass) {
        AbstractSecuredEntity entity = entityManager.load(aclClass, id);
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        return convertAclToEntry(entity, acl);
    }

    public Map<AbstractSecuredEntity, List<AclPermissionEntry>> getPermissions(
            Set<AbstractSecuredEntity> securedEntities) {
        Map<ObjectIdentity, Acl> acls = aclService.getObjectIdentities(securedEntities);
        Map<AbstractSecuredEntity, List<AclPermissionEntry>> result = new HashMap<>();
        securedEntities.forEach(securedEntity -> {
            Acl acl = acls.get(new ObjectIdentityImpl(securedEntity));
            Assert.isInstanceOf(MutableAcl.class, acl,
                    messageHelper.getMessage(MessageConstants.ERROR_MUTABLE_ACL_RETURN));
            List<AclPermissionEntry> permissions = new ArrayList<>();
            acl.getEntries().forEach(aclEntry -> permissions.add(
                    new AclPermissionEntry(aclEntry.getSid(), aclEntry.getPermission().getMask())));
            result.put(securedEntity, permissions);
        });
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AclSecuredEntry deletePermissions(Long id, AclClass aclClass,
            String user, boolean isPrincipal) {
        AbstractSecuredEntity entity = entityManager.load(aclClass, id);
        Assert.isTrue(!entity.isLocked(),
                messageHelper.getMessage(MessageConstants.ERROR_ENTITY_IS_LOCKED,
                        entity.getAclClass(), entity.getId()));
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        Sid sid = aclService.getSid(user.toUpperCase(), isPrincipal);
        int sidEntryIndex = findSidEntry(acl, sid);
        if (sidEntryIndex != -1) {
            acl.deleteAce(sidEntryIndex);
            acl = aclService.updateAcl(acl);
        }
        AclSecuredEntry aclSecuredEntry = convertAclToEntryForUser(entity, acl, sid);
        updateEventsWithChildrenAndIssues(entity);
        return aclSecuredEntry;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AclSecuredEntry deleteAllPermissions(Long id, AclClass aclClass) {
        AbstractSecuredEntity entity = entityManager.load(aclClass, id);
        Assert.isTrue(!entity.isLocked(),
                messageHelper.getMessage(MessageConstants.ERROR_ENTITY_IS_LOCKED,
                        entity.getAclClass(), entity.getId()));
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        acl = deleteAllAces(acl);
        return convertAclToEntry(entity, acl);
    }

    /**
     * Sets NO_WRITE & NO_EXECUTE permissions for an entity and all it's children
     * @param entity specifies entity
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AclSecuredEntry lockEntity(AbstractSecuredEntity entity){
        clearWriteExecutePermissions(entity);
        Permission noWriteExecutePermission = permissionFactory
                .buildFromMask(AclPermission.NO_WRITE.getMask() | AclPermission.NO_EXECUTE.getMask());
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        GrantedAuthoritySid userRoleSid = new GrantedAuthoritySid(DefaultRoles.ROLE_USER.getName());
        List<AccessControlEntry> aces = acl.getEntries();
        Permission mergedPermission = noWriteExecutePermission;
        for (AccessControlEntry ace : aces) {
            if (ace.getSid().equals(userRoleSid)) {
                mergedPermission = permissionFactory
                        .buildFromMask(ace.getPermission().getMask() | noWriteExecutePermission.getMask());
                break;
            }
        }
        clearAces(acl);
        acl.insertAce(0, mergedPermission, userRoleSid, true);
        acl = aclService.updateAcl(acl);
        return convertAclToEntry(entity, acl);
    }

    /**
     * Removes NO_WRITE & NO_EXECUTE permissions from an entity
     * @param entity
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void unlockEntity(AbstractSecuredEntity entity) {
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        GrantedAuthoritySid userRoleSid = new GrantedAuthoritySid(DefaultRoles.ROLE_USER.getName());
        int entryIndex = findSidEntry(acl, userRoleSid);
        if (entryIndex == -1) {
            return;
        }
        AccessControlEntry ace = acl.getEntries().get(entryIndex);
        Permission newPermission = permissionFactory
                .buildFromMask(permissionsService.unsetBits(ace.getPermission().getMask(),
                        AclPermission.NO_WRITE.getMask(), AclPermission.NO_EXECUTE.getMask()));
        acl.deleteAce(entryIndex);
        acl.insertAce(Math.max(entryIndex, 0), newPermission, userRoleSid, true);
        aclService.updateAcl(acl);

    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteGrantedAuthority(String name) {
        Long sidId = aclService.getSidId(name, false);
        if (sidId == null) {
            LOGGER.debug("Granted authority with name {} was not found in ACL", name);
            return;
        }
        aclService.deleteSidById(sidId);
    }

    public Integer getPermissionsMask(AbstractSecuredEntity entity, boolean merge,
            boolean includeInherited) {
        return getPermissionsMask(entity, merge, includeInherited, getSids());
    }

    public Integer getPermissionsMask(AbstractSecuredEntity entity, boolean merge,
            boolean includeInherited, List<Sid> sids) {
        if (isAdmin(sids)) {
            return merge ?
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK :
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL;
        }
        return retrieveMaskForSid(entity, merge, includeInherited, sids);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AclSecuredEntry changeOwner(final Long id, final AclClass aclClass, final String userName) {
        Assert.isTrue(StringUtils.isNotBlank(userName), "User name is required "
                + "to change owner of an object.");
        final AbstractSecuredEntity entity = entityManager.load(aclClass, id);
        final UserContext userContext = userManager.loadUserContext(userName);
        Assert.notNull(userContext, String.format("The user with name %s doesn't exist.", userName));
        if (entity.getOwner().equalsIgnoreCase(userName)) {
            LOGGER.info("The resource you're trying to change owner is already owned by this user.");
            return new AclSecuredEntry(entity);
        }
        aclService.changeOwner(entity, userName);
        return new AclSecuredEntry(entityManager.changeOwner(aclClass, id, userName));
    }

    public void filterTree(AbstractHierarchicalEntity entity, Permission permission) {
        filterTree(getSids(), entity, permission);
    }

    public void filterTree(String userName, AbstractHierarchicalEntity entity, Permission permission) {
        filterTree(convertUserToSids(userName), entity, permission);
    }

    private void filterTree(List<Sid> sids, AbstractHierarchicalEntity entity, Permission permission) {
        if (entity == null) {
            return;
        }
        if (isAdmin(sids)) {
            return;
        }
        processHierarchicalEntity(0, entity, new HashMap<>(), permission, true, sids);
    }

    public boolean ownerPermission(Long id, AclClass aclClass) {
        AbstractSecuredEntity entity = entityManager.load(aclClass, id);
        return permissionsHelper.isOwner(entity);
    }

    public boolean hasMetadataOwnerPermission(final Long id, final AclClass aclClass) {
        if (aclClass.isSupportsEntityManager()) {
            return ownerPermission(id, aclClass);
        }
        return hasPipelineUserOrRolePermission(id, aclClass);
    }

    public boolean isOwnerOrAdmin(String owner) {
        String user = authManager.getAuthorizedUser();
        if (user == null || user.equals(AuthManager.UNAUTHORIZED_USER)) {
            return false;
        }
        return user.equalsIgnoreCase(owner) || isAdmin(getSids());
    }

    public boolean isRunSshAllowed(Long runId) {
        return isRunSshAllowed(runManager.loadPipelineRun(runId));
    }

    public boolean isRunSshAllowed(PipelineRun pipelineRun) {
        if (isOwnerOrAdmin(pipelineRun.getOwner())) {
            return true;
        }
        final List<RunSid> sshSharedSids = ListUtils.emptyIfNull(pipelineRun.getRunSids())
                .stream()
                .filter(sid -> RunAccessType.SSH.equals(sid.getAccessType()))
                .collect(toList());
        if (CollectionUtils.isEmpty(sshSharedSids)) {
            return false;
        }
        final PipelineUser currentUser = authManager.getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return isSharedWithPrincipal(sshSharedSids, currentUser) || isSharedWithGroup(sshSharedSids, currentUser);
    }

    public boolean runPermission(Long runId, String permissionName) {
        PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
        if (permissionsHelper.isOwner(pipelineRun)) {
            return true;
        }
        if (isRunSshAllowed(pipelineRun)) {
            return true;
        }
        AbstractSecuredEntity parent = pipelineRun.getParent();
        if (parent == null) {
            return false;
        }
        return permissionEvaluator
                .hasPermission(SecurityContextHolder.getContext().getAuthentication(), parent,
                        permissionName);
    }

    public boolean runStatusPermission(Long runId, TaskStatus taskStatus, String permissionName) {
        PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
        if (taskStatus.isFinal()) {
            return isOwnerOrAdmin(pipelineRun.getOwner());
        }
        return runPermission(pipelineRun, permissionName);
    }

    public boolean storagePermission(Long storageId, String permissionName) {
        AbstractSecuredEntity storage = entityManager.load(AclClass.DATA_STORAGE, storageId);
        if (permissionName.equals(OWNER)) {
            return isOwnerOrAdmin(storage.getOwner());
        } else {
            return permissionsHelper.isAllowed(permissionName, storage);
        }
    }

    public boolean listedStoragePermissions(List<DataStorageAction> actions) {
        for (DataStorageAction action : actions) {
            AbstractSecuredEntity storage = entityManager.load(AclClass.DATA_STORAGE, action.getId());
            if ((action.isReadVersion() || action.isWriteVersion()) && !isOwnerOrAdmin(storage.getOwner())) {
                return false;
            }
            if (action.isRead() && !permissionsHelper.isAllowed("READ", storage)) {
                return false;
            }
            if (action.isWrite() && !permissionsHelper.isAllowed("WRITE", storage)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasDataStoragePathsPermission(final List<PathDescription> paths, final String permissionName) {
        return ListUtils.emptyIfNull(paths).stream()
                .allMatch(path -> permissionsHelper.isAllowed(permissionName,
                        entityManager.load(AclClass.DATA_STORAGE, path.getDataStorageId())));
    }

    public boolean checkStorageShared(Long storageId) {
        UserContext context = authManager.getUserContext();
        if (context.isExternal()) {
            AbstractDataStorage storage = (AbstractDataStorage) entityManager.load(AclClass.DATA_STORAGE, storageId);
            return storage.isShared();
        }

        return true;
    }

    /**
     * To create private group in a registry user must have ay least one 'READ' permission
     * in the whole tree hierarchy (since it means that registry is visible to user) and
     * 'WRITE' permission shouldn't be denied for registry itself
     * @param registryId to check permissions
     * @return true if permission is granted
     */
    public boolean createPrivateToolGroupPermission(Long registryId) {
        DockerRegistry dockerRegistryTree = registryManager.getDockerRegistryTree(registryId);
        filterTree(dockerRegistryTree, AclPermission.READ);
        return isCreatePrivateGroupAllowed(dockerRegistryTree);
    }

    private boolean isCreatePrivateGroupAllowed(AbstractHierarchicalEntity dockerRegistryTree) {
        Integer registryPermissionMask = getPermissionsMask(dockerRegistryTree, false, false);
        //write is denied
        if (!permissionsService.permissionIsNotDenied(registryPermissionMask, AclPermission.WRITE)) {
            return false;
        }
        //user has read to registry itself or has access to at least one child
        return permissionsService.isPermissionGranted(registryPermissionMask, AclPermission.READ) ||
                permissionsService.isPermissionGranted(registryPermissionMask, AclPermission.WRITE) ||
                !CollectionUtils.isEmpty(dockerRegistryTree.getChildren());
    }

    public boolean toolPermission(RepositoryTool repoTool, String permissionName) {
        if (!repoTool.getRegistered()) {
            return true;
        }
        AbstractSecuredEntity tool = repoTool.getTool();
        if (tool == null) {
            return true;
        }
        boolean allowed = permissionEvaluator
                .hasPermission(SecurityContextHolder.getContext().getAuthentication(), tool,
                        permissionName);
        if (allowed) {
            tool.setMask(getPermissionsMask(tool, true, true));
            repoTool.setMask(tool.getMask());
        }
        return allowed;
    }

    public boolean toolPermission(String registry, String image, String permissionName) {
        AbstractSecuredEntity tool = toolManager.loadTool(registry, image);
        return permissionsHelper.isAllowed(permissionName, tool);
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
        DockerRegistry registry = (DockerRegistry)entityManager.load(AclClass.DOCKER_REGISTRY, registryId);
        try {
            String trimmedImage = image.startsWith(registry.getPath()) ?
                    image.substring(registry.getPath().length() + 1) : image;
            ToolGroup toolGroup = toolGroupManager.loadToolGroupByImage(registry.getPath(), trimmedImage);
            Optional<Tool> tool = toolManager.loadToolInGroup(trimmedImage, toolGroup.getId());
            return tool.map(t -> permissionsHelper.isAllowed(permission, t))
                    .orElseGet(() -> permissionsHelper.isAllowed(permission, toolGroup));
        } catch (IllegalArgumentException e) {
            //case when tool group doesn't exist
            LOGGER.trace(e.getMessage(), e);
            return permissionsHelper.isAllowed(permission, registry);
        }

    }

    public boolean toolGroupPermission(String groupId, String permissionName) {
        ToolGroup group = toolGroupManager.loadByNameOrId(groupId);
        return permissionsHelper.isAllowed(permissionName, group);
    }

    public boolean toolGroupChildPermission(String groupId, String permissionName) {
        ToolGroup group = toolGroupManager.loadByNameOrId(groupId);
        return permissionsHelper.isAllowed(permissionName, group) &&
                ListUtils.emptyIfNull(group.getTools()).stream()
                        .allMatch(tool -> permissionsHelper.isAllowed(permissionName, tool));
    }

    public boolean runPermission(PipelineRun run, String permissionName) {
        if (permissionsHelper.isOwner(run)) {
            run.setMask(AbstractSecuredEntity.ALL_PERMISSIONS_MASK);
            return true;
        }
        AbstractSecuredEntity parent = runManager.loadRunParent(run);
        if (parent == null) {
            run.setMask(0);
            return false;
        }
        boolean allowed = permissionEvaluator
                .hasPermission(SecurityContextHolder.getContext().getAuthentication(), parent,
                        permissionName);
        if (allowed) {
            run.setMask(getPermissionsMask(parent, true, true));
        }
        return allowed;
    }

    public boolean metadataEntityPermission(Long entityId, String permissionName) {
        MetadataEntity entity = metadataEntityManager.load(entityId);
        if (entity.getParent() == null || entity.getParent().getId() == null) {
            return isAdmin(getSids());
        }
        AbstractSecuredEntity securedEntity = entityManager.load(AclClass.FOLDER, entity.getParent().getId());
        return permissionsHelper.isAllowed(permissionName, securedEntity);
    }

    public boolean metadataEntitiesPermission(Set<Long> entitiesIds, String permissionName) {
        for (Long entityId : entitiesIds) {
            if (!metadataEntityPermission(entityId, permissionName)) {
                return false;
            }
        }
        return true;
    }

    public boolean metadataPermission(Long entityId, AclClass entityClass, String permissionName) {
        if (!entityClass.isSupportsEntityManager()) {
            return hasPipelineUserOrRolePermission(entityId, entityClass);
        }
        final AbstractSecuredEntity securedEntity = entityManager.load(entityClass, entityId);
        if (permissionName.equals(OWNER)) {
            return isOwnerOrAdmin(securedEntity.getOwner());
        } else {
            return permissionsHelper.isAllowed(permissionName, securedEntity);
        }
    }

    public boolean metadataPermission(final MetadataEntry metadataEntry, final String permissionName) {
        final EntityVO entity = metadataEntry.getEntity();
        return metadataPermission(entity.getEntityId(), entity.getEntityClass(), permissionName);
    }

    public boolean listMetadataPermissions(final List<EntityVO> entities, final String permissionName) {
        for (EntityVO entity : entities) {
            if (!entity.getEntityClass().isSupportsEntityManager()) {
                if (!hasPipelineUserOrRolePermission(entity.getEntityId(), entity.getEntityClass())) {
                    return false;
                }
            } else if (!metadataPermission(entity.getEntityId(), entity.getEntityClass(), permissionName)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPipelineUserOrRolePermission(final Long entityId, final AclClass entityClass) {
        if (entityClass.equals(AclClass.ROLE)) {
            return isAdmin(getSids());
        }
        if (entityClass.equals(AclClass.PIPELINE_USER)) {
            final PipelineUser user = userManager.loadUserById(entityId);
            return isOwnerOrAdmin(user.getUserName());
        }
        return false;
    }

    public boolean entityPermission(AbstractSecuredEntity entity, String permissionName) {
        return entity == null || metadataPermission(entity.getId(), entity.getAclClass(), permissionName);
    }

    public boolean modifyIssuePermission(Long issueId) {
        Issue issue = issueManager.loadIssue(issueId);
        return issue.getAuthor().equalsIgnoreCase(authManager.getAuthorizedUser());
    }

    public boolean modifyCommentPermission(Long issueId, Long commentId) {
        IssueComment comment = issueManager.loadComment(issueId, commentId);
        return comment.getAuthor().equalsIgnoreCase(authManager.getAuthorizedUser());
    }

    public boolean issuePermission(Long issueId, String permissionName) {
        Issue issue = issueManager.loadIssue(issueId);
        return metadataPermission(issue.getEntity().getEntityId(), issue.getEntity().getEntityClass(), permissionName);
    }

    public void extendFilter(AclSecuredFilter filter) {
        List<Sid> sids = getSids();
        if (isAdmin(sids)) {
            return;
        }
        filter.setOwnershipFilter(authManager.getAuthorizedUser().toLowerCase());
        List<Long> allowedPipelinesList =
                pipelineApiService.loadAllPipelinesWithoutVersion()
                        .stream()
                        .map(BaseEntity::getId)
                        .collect(toList());
        filter.setAllowedPipelines(allowedPipelinesList);
    }

    public boolean nodePermission(NodeInstance node, String permissionName) {
        // not labeled nodes are available only for admins
        if (node.getPipelineRun() == null) {
            return false;
        }
        boolean allowed = runPermission(node.getPipelineRun(), permissionName);
        if (allowed) {
            node.setMask(node.getPipelineRun().getMask());
        }
        return allowed;
    }

    public boolean nodePermission(String nodeName, String permissionName) {
        NodeInstance nodeInstance = nodesManager.getNode(nodeName);
        // not labeled nodes are available only for admins
        if (nodeInstance.getPipelineRun() == null) {
            return false;
        }
        return runPermission(nodeInstance.getPipelineRun().getId(), permissionName);
    }

    public boolean nodeStopPermission(String nodeName, String permissionName) {
        NodeInstance nodeInstance = nodesManager.getNode(nodeName);
        // not labeled nodes are available only for admins
        if (nodeInstance.getPipelineRun() == null) {
            return false;
        }
        return runStatusPermission(nodeInstance.getPipelineRun().getId(), TaskStatus.STOPPED, permissionName);
    }

    public EntityPermissionVO loadEntityPermission(final AclClass entityClass, final Long id) {
        AbstractSecuredEntity entity = entityManager.loadEntityWithParents(entityClass, id);
        Assert.notNull(entity, messageHelper.getMessage(MessageConstants.ERROR_ENTITY_NOT_FOUND, id, entityClass));
        AbstractSecuredEntity aclEntity = getAclEntity(entity);
        Map<AbstractSecuredEntity, List<AclPermissionEntry>> allPermissions = getEntitiesPermissions(
                Collections.singleton(aclEntity));
        EntityPermission entityPermission = getEntityPermission(allPermissions, entity);
        return entityPermissionMapper.toEntityPermissionVO(entityPermission);
    }

    public EntityWithPermissionVO loadAllEntitiesPermissions(AclClass aclClass, Integer page, Integer pageSize,
                                                             boolean expandGroups, Integer filterMask) {
        EntityWithPermissionVO result = new EntityWithPermissionVO();
        Collection<? extends AbstractSecuredEntity> entities =
                entityManager.loadAllWithParents(aclClass, page, pageSize);
        Map<AbstractSecuredEntity, List<AclPermissionEntry>> allPermissions = getEntitiesPermissions(entities);
        result.setTotalCount(entityManager.loadTotalCount(aclClass));
        List<EntityPermission> permissions
                = entities.stream().distinct()
                .sorted(Comparator.comparingLong(BaseEntity::getId))
                .map(entity -> getEntityPermission(allPermissions, entity))
                .collect(toList());
        if (expandGroups) {
            expandGroups(permissions);
            if (filterMask != null) {
                permissions.forEach(entry -> {
                    Set<AclPermissionEntry> filtered = SetUtils.emptyIfNull(entry.getPermissions()).stream()
                            .filter(permission -> permissionsService.isMaskBitSet(permission.getMask(), filterMask))
                            .collect(toSet());
                    entry.setPermissions(filtered);
                });
            }
        }
        result.setEntityPermissions(permissions);
        return result;
    }

    private EntityPermission getEntityPermission(Map<AbstractSecuredEntity, List<AclPermissionEntry>> allPermissions,
                                                 AbstractSecuredEntity entity) {
        AbstractSecuredEntity aclEntity = getAclEntity(entity);
        Map<AclSid, Integer> mergedPermissions = getEntityPermissions(aclEntity, allPermissions);
        mergeWithParentPermissions(mergedPermissions, entity.getParent(), allPermissions);
        Set<AclPermissionEntry> merged = buildAclPermissionEntries(mergedPermissions);
        // clear parent, not to return full hierarchy
        entity.clearParent();
        EntityPermission entityPermission = new EntityPermission();
        entityPermission.setEntity(entity);
        entityPermission.setPermissions(merged);
        return entityPermission;
    }

    private AbstractSecuredEntity getAclEntity(final AbstractSecuredEntity entity) {
        // since METADATA_ENTITY is not ACL object
        return entity.getAclClass() == AclClass.METADATA_ENTITY ? entity.getParent() : entity;
    }

    public PipelinesWithPermissionsVO loadAllPipelinesWithPermissions(Integer pageNum, Integer pageSize) {
        //TODO: fully switch to common method loadAllEntitiesPermissions when client is ready
        EntityWithPermissionVO entityWithPermissionVO =
                loadAllEntitiesPermissions(AclClass.PIPELINE, pageNum, pageSize, false, null);
        PipelinesWithPermissionsVO result = new PipelinesWithPermissionsVO();
        result.setTotalCount(entityWithPermissionVO.getTotalCount());
        result.setPipelines(entityWithPermissionVO.getEntityPermissions().stream().map(e -> {
            PipelineWithPermissions pipelineWithPermissions =
                    pipelineWithPermissionsMapper.toPipelineWithPermissions((Pipeline)e.getEntity());
            pipelineWithPermissions.setPermissions(e.getPermissions());
            return pipelineWithPermissions;
        }).collect(toSet()));
        return result;
    }

    public boolean childrenFolderPermission(Long id, String permissionName) {
        Folder folder = folderManager.load(id);
        Folder initialFolder = folder.copy();
        AclPermission aclPermission = AclPermission.getAclPermissionByName(permissionName);
        filterTree(folder, aclPermission);
        return checkEntityTreeWasNotFilter(initialFolder, folder, aclPermission);
    }

    private boolean checkEntityTreeWasNotFilter(AbstractHierarchicalEntity initialEntity,
                                                AbstractHierarchicalEntity currentEntity,
                                                AclPermission aclPermission) {
        if (initialEntity.getChildren().size() != currentEntity.getChildren().size()
            || initialEntity.getLeaves().size() != currentEntity.getLeaves().size()
            || !permissionsService.isMaskBitSet(currentEntity.getMask(), aclPermission.getSimpleMask())) {
            return false;
        }

        return initialEntity.getChildren()
                .stream()
                .allMatch(initialChild ->
                        currentEntity.getChildren().stream()
                                .filter(currentChild -> currentChild.getAclClass() == initialChild.getAclClass() &&
                                        currentChild.getId().equals(initialChild.getId()))
                                .findFirst()
                                .map(currentChild ->
                                        checkEntityTreeWasNotFilter(initialChild, currentChild, aclPermission))
                                .orElse(false));
    }

    /**
     * Checks permission for {@link RunConfiguration}. In case of basic
     * {@link com.epam.pipeline.entity.configuration.RunConfigurationEntry} entries 'EXECUTE' permission is required
     * for {@link Pipeline} if it's specified or {@link Tool} if {@link Pipeline} is not specified for each entry.
     * @param configuration run configuration
     * @param permissionName the name of permission
     * @return true if permission is granted
     */
    public boolean hasConfigurationUpdatePermission(RunConfigurationVO configuration, String permissionName) {
        return permissionsHelper.isAllowed(permissionName, configuration.toEntity())
                && hasPermissionToConfiguration(configuration.getEntries(), "EXECUTE");
    }

    /**
     * Checks permission for list of {@link AbstractRunConfigurationEntry} entries. In case of basic
     * {@link com.epam.pipeline.entity.configuration.RunConfigurationEntry} entries method will check permission for
     * {@link Pipeline} if it's specified or {@link Tool} if {@link Pipeline} is not specified for each entry.
     * @param entries the list of configuration entries
     * @param permissionName the name of permission
     * @return true if permission is granted
     */
    public boolean hasPermissionToConfiguration(List<AbstractRunConfigurationEntry> entries, String permissionName) {
        return entries.stream().noneMatch(entry -> configurationProviderManager.hasNoPermission(entry, permissionName));
    }

    /**
     * Checks if at least one group from input groups is registered and refer to some entity.
     * @param groups the list of groups
     * @return true if at least one such group found
     */
    public boolean isGroupRegistered(final List<String> groups) {
        final Set<Long> sidIds = groups.stream()
                .map(group ->  aclService.getSidId(group, false))
                .filter(Objects::nonNull)
                .collect(toSet());
        if (CollectionUtils.isEmpty(sidIds)) {
            return false;
        }
        final Integer entriesCount = aclService.loadEntriesBySidsCount(sidIds);
        return entriesCount != null && entriesCount != 0;
    }

    private List<Sid> convertUserToSids(String user) {
        String principal = user.toUpperCase();
        UserContext eventOwner = userManager.loadUserContext(user.toUpperCase());
        Assert.notNull(eventOwner, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, principal));
        List<Sid> sids = new ArrayList<>();
        sids.add(new PrincipalSid(principal));
        sids.addAll(eventOwner.getAuthorities().stream()
                .map(GrantedAuthoritySid::new)
                .collect(toList()));
        return sids;
    }

    private List<Sid> getSids() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return sidRetrievalStrategy.getSids(authentication);
    }

    private boolean isAdmin(List<Sid> sids) {
        GrantedAuthoritySid admin = new GrantedAuthoritySid(DefaultRoles.ROLE_ADMIN.getName());
        return sids.stream().anyMatch(sid -> sid.equals(admin));
    }

    private void validateParameters(PermissionGrantVO grantVO) {
        Assert.notNull(grantVO.getId(),
                messageHelper.getMessage(MessageConstants.ERROR_PERMISSION_PARAM_REQUIRED, "ID"));
        Assert.notNull(grantVO.getUserName(), messageHelper
                .getMessage(MessageConstants.ERROR_PERMISSION_PARAM_REQUIRED, "UserName"));
        Assert.notNull(grantVO.getAclClass(), messageHelper
                .getMessage(MessageConstants.ERROR_PERMISSION_PARAM_REQUIRED, "ObjectClass"));
        Assert.notNull(grantVO.getMask(),
                messageHelper.getMessage(MessageConstants.ERROR_PERMISSION_PARAM_REQUIRED, "Mask"));
        permissionsService.validateMask(grantVO.getMask());
    }


    private int collectPermissions(int mask, Acl acl, List<Sid> sids,
            List<AclPermission> permissionToCollect, boolean includeInherited) {
        if (permissionsService.allPermissionsSet(mask, permissionToCollect)) {
            return mask;
        }
        int currentMask = mask;
        final List<AccessControlEntry> aces = acl.getEntries();
        for (Sid sid : sids) {
            // Attempt to find exact match for this permission mask and SID
            for (AccessControlEntry ace : aces) {
                if (ace.getSid().equals(sid)) {
                    Permission permission = ace.getPermission();
                    for (AclPermission p : permissionToCollect) {
                        if (!permissionsService.isPermissionSet(currentMask, p)) {
                            //try to set granting mask
                            currentMask = currentMask | (permission.getMask() & p.getMask());
                            if (!permissionsService.isPermissionSet(currentMask, p)) {
                                //try to set denying mask
                                currentMask =
                                        currentMask | (permission.getMask() & p.getDenyPermission()
                                                .getMask());
                            }
                        }
                    }
                }
            }
        }
        if (permissionsService.allPermissionsSet(currentMask, permissionToCollect)) {
            return currentMask;
        }
        // No matches have been found so far
        if (includeInherited && acl.isEntriesInheriting() && (acl.getParentAcl() != null)) {
            // We have a parent, so let them try to find a matching ACE
            return collectPermissions(currentMask, acl.getParentAcl(), sids, permissionToCollect,
                    includeInherited);
        } else {
            return currentMask;
        }
    }

    private AclSecuredEntry convertAclToEntryForUser(AbstractSecuredEntity entity, MutableAcl acl,
            Sid sid) {
        AclSid aclSid = new AclSid(sid);
        AclSecuredEntry entry = convertAclToEntry(entity, acl);
        List<AclPermissionEntry> filteredPermissions =
                entry.getPermissions().stream().filter(p -> p.getSid().equals(aclSid))
                        .collect(toList());
        entry.setPermissions(filteredPermissions);
        return entry;
    }

    private int findSidEntry(MutableAcl acl, Sid sid) {
        List<AccessControlEntry> entries = acl.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getSid().equals(sid)) {
                return i;
            }
        }
        return -1;
    }

    private AclSecuredEntry convertAclToEntry(AbstractSecuredEntity entity, MutableAcl acl) {

        AclSecuredEntry entry = new AclSecuredEntry(entity);
        acl.getEntries().forEach(aclEntry -> entry.addPermission(
                new AclPermissionEntry(aclEntry.getSid(), aclEntry.getPermission().getMask())));
        return entry;
    }

    private Integer retrieveMaskForSid(AbstractSecuredEntity entity, boolean merge,
            boolean includeInherited, List<Sid> sids) {
        Acl child = aclService.getAcl(entity);
        //case for Runs and Nodes, that are not registered as ACL entities
        //check ownership
        if (child == null && permissionsHelper.isOwner(entity)) {
            return merge ?
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK :
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL;
        }
        if (child == null && entity.getParent() == null) {
            LOGGER.debug("Object is not registered in ACL {} {}", entity.getAclClass(), entity.getId());
            return 0;
        }
        //get parent
        Acl acl = child == null ? aclService.getAcl(entity.getParent()) : child;
        if (sids.stream().anyMatch(sid -> acl.getOwner().equals(sid))) {
            return merge ?
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK :
                    AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL;
        }
        List<AclPermission> basicPermissions = permissionsService.getBasicPermissions();
        int extendedMask = collectPermissions(0, acl, sids, basicPermissions, includeInherited);
        return merge ? permissionsService.mergeMask(extendedMask, basicPermissions) : extendedMask;
    }

    private void processHierarchicalEntity(int parentMask, AbstractHierarchicalEntity entity,
            Map<AclClass, Set<Long>> entitiesToRemove, Permission permission, boolean root,
            List<Sid> sids) {
        int defaultMask = 0;
        int currentMask = entity.getId() != null ?
                permissionsService.mergeParentMask(retrieveMaskForSid(entity, false, root, sids),
                        parentMask) : defaultMask;
        entity.getChildren().forEach(
            leaf -> processHierarchicalEntity(currentMask, leaf, entitiesToRemove, permission,
                        false, sids));
        filterChildren(currentMask, entity.getLeaves(), entitiesToRemove, permission, sids);
        entity.filterLeaves(entitiesToRemove);
        entity.filterChildren(entitiesToRemove);
        boolean permissionGranted = permissionsService.isPermissionGranted(currentMask, permission);
        if (!permissionGranted) {
            entity.clearForReadOnlyView();
        }
        if (CollectionUtils.isEmpty(entity.getLeaves()) && CollectionUtils
                .isEmpty(entity.getChildren()) && !permissionGranted) {
            entitiesToRemove.putIfAbsent(entity.getAclClass(), new HashSet<>());
            entitiesToRemove.get(entity.getAclClass()).add(entity.getId());
        }
        entity.setMask(permissionsService.mergeMask(currentMask));
        if (entity instanceof DockerRegistry) {
            ((DockerRegistry)entity).setPrivateGroupAllowed(isCreatePrivateGroupAllowed(entity));
        }
    }

    private void filterChildren(int parentMask, List<? extends AbstractSecuredEntity> children,
            Map<AclClass, Set<Long>> entitiesToRemove, Permission permission, List<Sid> sids) {
        ListUtils.emptyIfNull(children).forEach(child -> {
            int mask = permissionsService
                    .mergeParentMask(getPermissionsMask(child, false, false, sids), parentMask);
            if (!permissionsService.isPermissionGranted(mask, permission)) {
                entitiesToRemove.putIfAbsent(child.getAclClass(), new HashSet<>());
                entitiesToRemove.get(child.getAclClass()).add(child.getId());
            }
            child.setMask(permissionsService.mergeMask(mask));
        });
    }

    private void clearWriteExecutePermissions(AbstractSecuredEntity entity) {
        int readBits = AclPermission.READ.getMask() | AclPermission.NO_READ.getMask();
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        List<AccessControlEntry> newAces = new ArrayList<>();
        List<AccessControlEntry> aces = acl.getEntries();
        for (int i = 0; i < aces.size(); i++) {
            AccessControlEntry ace = aces.get(i);
            if (permissionsService.isPermissionSet(ace.getPermission().getMask(),
                    (AclPermission) AclPermission.READ)) {
                Permission updated =
                        permissionFactory.buildFromMask(ace.getPermission().getMask() & readBits);
                AccessControlEntry newAce =
                        new AccessControlEntryImpl(ace.getId(), ace.getAcl(), ace.getSid(), updated,
                                true, false, false);
                newAces.add(newAce);
            }
        }
        clearAces(acl);
        for (int i = 0; i < newAces.size(); i++) {
            AccessControlEntry newAce = newAces.get(i);
            acl.insertAce(i, newAce.getPermission(), newAce.getSid(), true);
        }
        aclService.updateAcl(acl);
        if (entity instanceof AbstractHierarchicalEntity) {
            AbstractHierarchicalEntity tree = (AbstractHierarchicalEntity) entity;
            if (!CollectionUtils.isEmpty(tree.getChildren())) {
                tree.getChildren().forEach(this::clearWriteExecutePermissions);
            }
            if (!CollectionUtils.isEmpty(tree.getLeaves())) {
                tree.getLeaves().forEach(this::clearWriteExecutePermissions);
            }
        }
    }

    private void clearAces(MutableAcl acl) {
        while (!acl.getEntries().isEmpty()) {
            acl.deleteAce(0);
        }
    }


    private MutableAcl deleteAllAces(MutableAcl acl) {
        if (!CollectionUtils.isEmpty(acl.getEntries())) {
            clearAces(acl);
            acl = aclService.updateAcl(acl);
        }
        return acl;
    }

    private void mergePermissions(Map<AclSid, Integer> childPermissions, List<AclPermissionEntry> parentPermissions) {
        parentPermissions.forEach(aclPermissionEntry -> {
            childPermissions.computeIfPresent(aclPermissionEntry.getSid(), (acl, mask) -> permissionsService
                    .mergeParentMask(mask, aclPermissionEntry.getMask()));
            childPermissions.putIfAbsent(aclPermissionEntry.getSid(), aclPermissionEntry.getMask());
        });
    }

    private Set<AclPermissionEntry> buildAclPermissionEntries(Map<AclSid, Integer> permissions) {
        Set<AclPermissionEntry> result = new HashSet<>();
        permissions.forEach((acl, mask) -> result.add(new AclPermissionEntry(acl, mask)));
        return result;
    }

    private Map<AclSid, Integer> getEntityPermissions(
            AbstractSecuredEntity entity,
            Map<AbstractSecuredEntity, List<AclPermissionEntry>> allPermissions) {
        return allPermissions.get(entity).stream()
                .collect(toMap(AclPermissionEntry::getSid, AclPermissionEntry::getMask));
    }

    private void mergeWithParentPermissions(Map<AclSid, Integer> mergedPermissions, AbstractSecuredEntity parent,
                                            Map<AbstractSecuredEntity, List<AclPermissionEntry>> allPermissions) {
        AbstractSecuredEntity currentParent = parent;
        while (currentParent != null) {
            mergePermissions(mergedPermissions, allPermissions.get(currentParent));
            currentParent = currentParent.getParent();
        }
    }

    private Map<AbstractSecuredEntity, List<AclPermissionEntry>> getEntitiesPermissions(
            Collection<? extends AbstractSecuredEntity> entities) {
        Set<AbstractSecuredEntity> result = new HashSet<>(entities);
        entities.forEach(entity -> {
            AbstractSecuredEntity parent = entity.getParent();
            while (parent != null) {
                result.add(parent);
                parent = parent.getParent();
            }
        });
        if (CollectionUtils.isEmpty(result)) {
            return Collections.emptyMap();
        }
        return getPermissions(result);
    }

    private void expandGroups(List<EntityPermission> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return;
        }
        Map<String, Set<String>> groupToUsers = userManager.loadAllUsers().stream().map(user ->
                Stream.concat(
                        //groups
                        ListUtils.emptyIfNull(user.getGroups()).stream()
                                .map(authority -> new Pair<>(user.getUserName(), authority)),
                        //roles
                        ListUtils.emptyIfNull(user.getRoles()).stream()
                                .map(role -> new Pair<>(user.getUserName(), role.getName())))
                        .collect(toList()))
                .flatMap(Collection::stream)
                .collect(groupingBy(Pair::getSecond, mapping(Pair::getFirst, toSet())));
        permissions.forEach(permission -> {
            permission.setPermissions(expandGroupsAndMergePermissions(groupToUsers, permission));
        });

    }

    private Set<AclPermissionEntry> expandGroupsAndMergePermissions(Map<String, Set<String>> groupToUsers,
                                                                    EntityPermission permission) {
        Map<AclSid, List<SidAclEntry>> sidToEntries = SetUtils.emptyIfNull(permission.getPermissions())
                .stream()
                .map(aclEntry -> {
                    AclSid sid = aclEntry.getSid();
                    if (sid.isPrincipal()) {
                        return Collections.singletonList(new SidAclEntry(aclEntry, false));
                    }
                    return groupToUsers.getOrDefault(sid.getName(), Collections.emptySet())
                            .stream()
                            .map(username -> {
                                AclPermissionEntry aclPermissionEntry =
                                        new AclPermissionEntry(new AclSid(username, true), aclEntry.getMask());
                                return new SidAclEntry(aclPermissionEntry, true);
                            })
                            .collect(toList());
                })
                .flatMap(Collection::stream)
                .collect(groupingBy(sidEntry -> sidEntry.getEntry().getSid()));

        return sidToEntries.values().stream()
                .map(aces -> {
                    SidAclEntry userEntry = aces.stream()
                            .filter(e -> !e.isResolved()).findFirst()
                            .orElse(aces.get(0));
                    List<SidAclEntry> inheritedEntries = aces.stream().filter(ace -> !ace.equals(userEntry))
                            .collect(toList());
                    AclPermissionEntry entry = userEntry.getEntry();
                    if (CollectionUtils.isEmpty(inheritedEntries)) {
                        return entry;
                    }
                    int mask = entry.getMask();
                    for (SidAclEntry inheritedEntry : inheritedEntries) {
                        mask = permissionsService.mergeParentMask(mask, inheritedEntry.getEntry().getMask());
                    }
                    return new AclPermissionEntry(entry.getSid(), mask);
                })
                .collect(toSet());
    }

    private void updateEventsWithChildrenAndIssues(final AbstractSecuredEntity entity) {
        try {
            entityEventServiceManager.updateEventsWithChildrenAndIssues(entity.getAclClass(), entity.getId());
        } catch (Exception e) {
            LOGGER.error(String.format("An error occurred during event update for entity %s with ID %d",
                    entity.getAclClass(), entity.getId()), e);
        }
    }

    private boolean isSharedWithGroup(final List<RunSid> sshSharedSids, final PipelineUser currentUser) {
        final Set<String> userClaims = new HashSet<>();
        userClaims.addAll(ListUtils.emptyIfNull(currentUser.getRoles()).stream().map(Role::getName)
                .collect(toList()));
        userClaims.addAll(ListUtils.emptyIfNull(currentUser.getGroups()));
        return sshSharedSids.stream().anyMatch(sid -> Boolean.FALSE.equals(sid.getIsPrincipal()) &&
                userClaims.contains(sid.getName()));
    }

    private boolean isSharedWithPrincipal(final List<RunSid> sshSharedSids, final PipelineUser currentUser) {
        return sshSharedSids.stream().anyMatch(sid -> Boolean.TRUE.equals(sid.getIsPrincipal()) &&
                sid.getName().equalsIgnoreCase(currentUser.getUserName()));
    }

    @Data
    @RequiredArgsConstructor
    private static class SidAclEntry {
        private final AclPermissionEntry entry;
        private final boolean resolved;
    }
}
