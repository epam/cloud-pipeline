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
package com.epam.pipeline.elasticsearchagent.service.impl.converter;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCloudPipelineEntityLoader<T> implements EntityLoader<T> {

    private final CloudPipelineAPIClient apiClient;

    @Override
    public Optional<EntityContainer<T>> loadEntity(final Long id) throws EntityNotFoundException {
        try {
            return Optional.of(buildContainer(id));
        } catch (PipelineResponseException e) {
            log.error(e.getMessage(), e);
            log.debug("Expected error message: {}", buildNotFoundErrorMessage(id));
            if (e.getMessage().contains(buildNotFoundErrorMessage(id))) {
                throw new EntityNotFoundException(e);
            }
            return Optional.empty();
        }
    }

    protected abstract T fetchEntity(Long id);

    protected abstract String getOwner(T entity);

    protected abstract AclClass getAclClass(T entity);

    protected EntityContainer<T> buildContainer(final Long id) {
        final T entity = fetchEntity(id);
        return EntityContainer.<T>builder()
                .entity(entity)
                .owner(loadUser(getOwner(entity)))
                .metadata(loadMetadata(id, getAclClass(entity)))
                .permissions(loadPermissions(id, getAclClass(entity)))
                .build();
    }

    protected PermissionsContainer loadPermissions(final Long id, final AclClass entityClass) {
        PermissionsContainer permissionsContainer = new PermissionsContainer();
        if (entityClass == null) {
            return permissionsContainer;
        }
        EntityPermissionVO entityPermission = apiClient.loadPermissionsForEntity(id, entityClass);

        if (entityPermission != null) {
            String owner = entityPermission.getOwner();
            permissionsContainer.add(entityPermission.getPermissions(), owner);
        }

        return permissionsContainer;
    }

    protected PipelineUser loadUser(final String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        return apiClient.loadUserByName(username);
    }

    protected Map<String, String> loadMetadata(final Long id,
                                               final AclClass aclClass) {
        if (aclClass == null) {
            return Collections.emptyMap();
        }
        List<MetadataEntry> metadataEntries = apiClient
                .loadMetadataEntry(Collections.singletonList(new EntityVO(id, aclClass)));
        return prepareMetadataForEntity(metadataEntries);
    }

    protected Map<String, String> prepareMetadataForEntity(final List<MetadataEntry> metadataEntries) {
        Map<String, String> metadata = null;
        if (!CollectionUtils.isEmpty(metadataEntries) && metadataEntries.size() == 1) {
            metadata = Stream.of(MapUtils.emptyIfNull(metadataEntries.get(0).getData()))
                    .map(Map::entrySet)
                    .flatMap(Set::stream)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        pipeConfValueEntry -> {
                            if (pipeConfValueEntry == null || pipeConfValueEntry.getValue() == null) {
                                return null;
                            }
                            return pipeConfValueEntry.getValue().getValue();
                        }
                    ));
        }
        return MapUtils.emptyIfNull(metadata);
    }

    protected String buildNotFoundErrorMessage(final Long id) {
        return String.format("%d was not found", id);
    }
}
