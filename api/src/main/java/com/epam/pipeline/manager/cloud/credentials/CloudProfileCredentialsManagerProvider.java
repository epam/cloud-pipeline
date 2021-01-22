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

import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.repository.cloud.credentials.CloudProfileCredentialsRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.utils.CommonUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@SuppressWarnings("unchecked")
public class CloudProfileCredentialsManagerProvider {
    private final Map<CloudProvider, ? extends CloudProfileCredentialsManager> managers;
    private final CloudProfileCredentialsRepository repository;
    private final CloudProfileCredentialsMapper mapper;
    private final PipelineUserRepository userRepository;
    private final RoleRepository roleRepository;

    public CloudProfileCredentialsManagerProvider(final List<? extends CloudProfileCredentialsManager> managers,
                                                  final CloudProfileCredentialsRepository repository,
                                                  final CloudProfileCredentialsMapper mapper,
                                                  final PipelineUserRepository userRepository,
                                                  final RoleRepository roleRepository) {
        this.managers = CommonUtils.groupByCloudProvider(managers);
        this.repository = repository;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public CloudProfileCredentials create(final CloudProfileCredentials credentials) {
        return getManager(credentials.getCloudProvider()).create(credentials);
    }

    public CloudProfileCredentials get(final Long id) {
        return mapper.toDto(findEntity(id));
    }

    public List<? extends CloudProfileCredentials> getProfilesByUserOrRole(final Long id, final boolean principal) {
        if (principal) {
            final PipelineUser user = userRepository.findOne(id);
            if (Objects.isNull(user)) {
                return Collections.emptyList();
            }
            return toDtos(user.getCloudProfiles());
        }
        final Role role = roleRepository.findOne(id);
        if (Objects.isNull(role)) {
            return Collections.emptyList();
        }
        return toDtos(role.getCloudProfiles());
    }

    public CloudProfileCredentials update(final Long id, final CloudProfileCredentials credentials) {
        return getManager(credentials.getCloudProvider()).update(id, credentials);
    }

    public CloudProfileCredentials delete(final Long id) {
        final CloudProfileCredentialsEntity entity = findEntity(id);
        repository.delete(id);
        return mapper.toDto(entity);
    }

    public List<? extends CloudProfileCredentials> findAll() {
        return toDtos(repository.findAll());
    }

    public CloudProfileCredentials attachProfileToUserOrRole(final Long profileId, final Long sidId,
                                                             final boolean principal) {
        final CloudProfileCredentialsEntity profileCredentialsEntity = findEntity(profileId);
        if (principal) {
            final PipelineUser user = userRepository.findOne(sidId);
            Assert.notNull(user, "User not found");
            profileCredentialsEntity.getUsers().add(user);
            repository.save(profileCredentialsEntity);
            return mapper.toDto(profileCredentialsEntity);
        }
        final Role role = roleRepository.findOne(sidId);
        Assert.notNull(role, "Role not found");
        profileCredentialsEntity.getRoles().add(role);
        repository.save(profileCredentialsEntity);
        return mapper.toDto(profileCredentialsEntity);
    }

    private CloudProfileCredentialsManager getManager(final CloudProvider type) {
        return managers.get(type);
    }

    private CloudProfileCredentialsEntity findEntity(final Long id) {
        final CloudProfileCredentialsEntity entity = repository.findOne(id);
        Assert.notNull(entity, String.format("Profile credentials with id %s wasn't found.", id));
        return entity;
    }

    private List<? extends CloudProfileCredentials> toDtos(final Iterable<CloudProfileCredentialsEntity> entities) {
        return StreamSupport.stream(entities.spliterator(), false)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
}
