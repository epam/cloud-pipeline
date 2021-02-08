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

package com.epam.pipeline.manager.cloud.credentials;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.cloud.credentials.AbstractCloudProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.repository.cloud.credentials.CloudProfileCredentialsRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@SuppressWarnings("unchecked")
public class CloudProfileCredentialsManagerProvider {
    private final Map<CloudProvider, ? extends CloudProfileCredentialsManager> managers;
    private final CloudProfileCredentialsRepository repository;
    private final CloudProfileCredentialsMapper mapper;
    private final PipelineUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CloudRegionManager cloudRegionManager;
    private final MessageHelper messageHelper;

    public CloudProfileCredentialsManagerProvider(final List<? extends CloudProfileCredentialsManager> managers,
                                                  final CloudProfileCredentialsRepository repository,
                                                  final CloudProfileCredentialsMapper mapper,
                                                  final PipelineUserRepository userRepository,
                                                  final RoleRepository roleRepository,
                                                  final CloudRegionManager cloudRegionManager,
                                                  final MessageHelper messageHelper) {
        this.managers = CommonUtils.groupByCloudProvider(managers);
        this.repository = repository;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.cloudRegionManager = cloudRegionManager;
        this.messageHelper = messageHelper;
    }

    public AbstractCloudProfileCredentials create(final AbstractCloudProfileCredentials credentials) {
        return getManager(credentials.getCloudProvider()).create(credentials);
    }

    public AbstractCloudProfileCredentials get(final Long id) {
        return mapper.toDto(findEntity(id));
    }

    public List<? extends AbstractCloudProfileCredentials> getAssignedProfiles(final Long id, final boolean principal) {
        if (principal) {
            return findUserProfiles(id);
        }
        final Role role = findRoleEntity(id);
        return toDtos(role.getCloudProfiles());
    }

    public AbstractCloudProfileCredentials update(final Long id, final AbstractCloudProfileCredentials credentials) {
        return getManager(credentials.getCloudProvider()).update(id, credentials);
    }

    @Transactional
    public AbstractCloudProfileCredentials delete(final Long id) {
        final CloudProfileCredentialsEntity entity = findEntity(id);
        Assert.state(CollectionUtils.isEmpty(entity.getUsers())
                        && CollectionUtils.isEmpty(entity.getRoles()),
                messageHelper.getMessage(MessageConstants.ERROR_PROFILE_HAS_LINKS, id));
        repository.delete(id);
        return mapper.toDto(entity);
    }

    public List<? extends AbstractCloudProfileCredentials> findAll(final Long userId) {
        if (Objects.nonNull(userId)) {
            return findAllForUser(userId);
        }
        return toDtos(repository.findAll());
    }

    @Transactional
    public List<? extends AbstractCloudProfileCredentials> assignProfiles(final Long sidId, final boolean principal,
                                                                          final Set<Long> profileIds,
                                                                          final Long defaultProfileId) {
        if (CollectionUtils.isNotEmpty(profileIds) && Objects.nonNull(defaultProfileId)) {
            profileIds.add(defaultProfileId);
        }
        final List<CloudProfileCredentialsEntity> profiles = SetUtils.emptyIfNull(profileIds).stream()
                .map(this::findEntity)
                .collect(Collectors.toList());
        if (principal) {
            return toDtos(assignProfilesToUser(profiles, sidId, defaultProfileId).getCloudProfiles());
        }
        return toDtos(assignProfilesToRole(sidId, defaultProfileId, profiles).getCloudProfiles());
    }

    public TemporaryCredentials generateProfileCredentials(final Long profileId, final Long regionId) {
        final AbstractCloudProfileCredentials credentials = get(profileId);
        return managers.get(credentials.getCloudProvider())
                .generateProfileCredentials(credentials, getRegion(regionId));
    }

    public boolean hasAssignedUserOrRole(final Long profileId, final Long userId, final List<Role> roles) {
        final CloudProfileCredentialsEntity entity = findEntity(profileId);
        return hasAssignedUser(entity.getUsers(), userId) || hasAssignedRole(entity.getRoles(), roles);
    }

    private boolean hasAssignedUser(final List<PipelineUser> assignedUsers, final Long targetUserId) {
        return ListUtils.emptyIfNull(assignedUsers).stream()
                .anyMatch(user -> Objects.equals(user.getId(), targetUserId));
    }

    private boolean hasAssignedRole(final List<Role> assignedRoles, final List<Role> targetRoles) {
        final List<Long> targetRoleIds = ListUtils.emptyIfNull(targetRoles).stream()
                .map(Role::getId)
                .collect(Collectors.toList());
        return ListUtils.emptyIfNull(assignedRoles).stream()
                .map(Role::getId)
                .anyMatch(targetRoleIds::contains);
    }

    private CloudProfileCredentialsManager getManager(final CloudProvider type) {
        return managers.get(type);
    }

    private CloudProfileCredentialsEntity findEntity(final Long id) {
        final CloudProfileCredentialsEntity entity = repository.findOne(id);
        Assert.notNull(entity, messageHelper.getMessage(MessageConstants.ERROR_PROFILE_ID_NOT_FOUND, id));
        return entity;
    }

    private List<? extends AbstractCloudProfileCredentials> toDtos(
            final Iterable<CloudProfileCredentialsEntity> entities) {
        return StreamSupport.stream(entities.spliterator(), false)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private AbstractCloudRegion getRegion(final Long regionId) {
        if (Objects.isNull(regionId)) {
            return null;
        }
        return cloudRegionManager.load(regionId);
    }

    private PipelineUser assignProfilesToUser(final List<CloudProfileCredentialsEntity> profiles, final Long userId,
                                              final Long defaultProfileId) {
        final PipelineUser userEntity = findUserEntity(userId);
        userEntity.setDefaultProfileId(defaultProfileId);
        userEntity.setCloudProfiles(profiles);
        userRepository.save(userEntity);
        return userEntity;
    }

    private Role assignProfilesToRole(final Long sidId, final Long defaultProfileId,
                                     final List<CloudProfileCredentialsEntity> profiles) {
        final Role roleEntity = findRoleEntity(sidId);
        roleEntity.setDefaultProfileId(defaultProfileId);
        roleEntity.setCloudProfiles(profiles);
        roleRepository.save(roleEntity);
        return roleEntity;
    }

    private PipelineUser findUserEntity(final Long userId) {
        final PipelineUser userEntity = userRepository.findOne(userId);
        Assert.notNull(userEntity, messageHelper.getMessage(MessageConstants.ERROR_USER_ID_NOT_FOUND, userId));
        return userEntity;
    }

    private Role findRoleEntity(final Long roleId) {
        final Role roleEntity = roleRepository.findOne(roleId);
        Assert.notNull(roleEntity, messageHelper.getMessage(MessageConstants.ERROR_ROLE_ID_NOT_FOUND, roleId));
        return roleEntity;
    }

    private List<? extends AbstractCloudProfileCredentials> findUserProfiles(final Long id) {
        final PipelineUser user = findUserEntity(id);
        return toDtos(user.getCloudProfiles());
    }

    private List<? extends AbstractCloudProfileCredentials> findAllForUser(final Long id) {
        final PipelineUser user = findUserEntity(id);
        final List<? extends AbstractCloudProfileCredentials> userProfiles = toDtos(user.getCloudProfiles());
        final List<Role> roles = user.getRoles();
        final List<? extends AbstractCloudProfileCredentials> rolesProfiles = roles.stream()
                .flatMap(role -> toDtos(role.getCloudProfiles()).stream())
                .collect(Collectors.toList());
        return Stream.concat(userProfiles.stream(), rolesProfiles.stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
