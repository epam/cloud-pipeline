/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.PipelineUserEventsList;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.MetadataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserImportManager {
    private final UserManager userManager;
    private final MessageHelper messageHelper;
    private final MetadataManager metadataManager;
    private final RoleManager roleManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineUserEvent> processUser(final PipelineUserWithStoragePath pipelineUserWithMetadata,
                                               final boolean createUser, final boolean createGroup,
                                               final List<CategoricalAttribute> categoricalAttributes) {
        final PipelineUserEventsList events = new PipelineUserEventsList(
                pipelineUserWithMetadata.getPipelineUser().getUserName());

        final PipelineUser pipelineUser = getOrCreateUser(pipelineUserWithMetadata.getPipelineUser(),
                createUser, events);

        if (Objects.isNull(pipelineUser)) {
            return events.getEvents();
        }

        pipelineUserWithMetadata.getRoles().forEach(role -> processRole(role, pipelineUser, createGroup, events));

        addMetadataToUser(pipelineUser, pipelineUserWithMetadata.getMetadata(), events,
                categoricalAttributes.stream()
                        .collect(Collectors.toMap(CategoricalAttribute::getKey, Function.identity())));

        return events.getEvents();
    }

    private PipelineUser getOrCreateUser(final PipelineUser pipelineUser, final boolean createUser,
                                         final PipelineUserEventsList events) {
        final String userName = pipelineUser.getUserName();
        final PipelineUser loadedUser = userManager.loadUserByName(userName);
        if (!createUser && Objects.isNull(loadedUser)) {
            events.info(messageHelper.getMessage(MessageConstants.EVENT_USER_CREATION_NOT_ALLOWED, userName));
            return null;
        }
        if (Objects.isNull(loadedUser)) {
            final PipelineUser user = userManager.create(userName, Collections.emptyList(), Collections.emptyList(),
                    pipelineUser.getAttributes(), null);
            events.info(messageHelper.getMessage(MessageConstants.EVENT_USER_CREATED, userName));
            return user;
        }
        return loadedUser;
    }

    private boolean roleAssignedToUser(final String roleNameToAdd, final List<Role> userRoles) {
        return ListUtils.emptyIfNull(userRoles).stream()
                .anyMatch(userRole -> userRole.getName().equalsIgnoreCase(roleNameToAdd));
    }

    private Role createRoleIfAllowed(final Role role, final boolean createRole, final PipelineUserEventsList events) {
        if (!createRole) {
            events.info(messageHelper.getMessage(MessageConstants.EVENT_ROLE_CREATION_NOT_ALLOWED, role.getName()));
            return null;
        }
        final Role createdRole = roleManager.createRole(role.getName(), role.isPredefined(), role.isUserDefault(),
                role.getDefaultStorageId());
        events.info(messageHelper.getMessage(MessageConstants.EVENT_ROLE_CREATED, role.getName()));
        return createdRole;
    }

    private Role getOrCreateRole(final Role role, final boolean createRoles, final PipelineUserEventsList events) {
        return roleManager.findRoleByName(role.getName())
                .orElseGet(() -> createRoleIfAllowed(role, createRoles, events));
    }

    private void processRole(final Role roleToAdd, final PipelineUser user, final boolean createRoles,
                             final PipelineUserEventsList events) {
        if (roleAssignedToUser(roleToAdd.getName(), user.getRoles())) {
            return;
        }
        final Role role = getOrCreateRole(roleToAdd, createRoles, events);
        if (Objects.isNull(role)) {
            return;
        }
        roleManager.assignRole(role.getId(), Collections.singletonList(user.getId()));
        events.info(messageHelper.getMessage(MessageConstants.EVENT_ROLE_ASSIGNED,
                role.getName(), user.getUserName()));
    }

    private Map<String, PipeConfValue> getCurrentMetadata(final Long userId) {
        final MetadataEntry currentMetadataEntry = metadataManager.loadMetadataItem(userId, AclClass.PIPELINE_USER);
        return Objects.isNull(currentMetadataEntry)
                ? new HashMap<>()
                : MapUtils.emptyIfNull(currentMetadataEntry.getData());
    }

    private void addMetadataToUser(final PipelineUser pipelineUser,
                                   final Map<String, PipeConfValue> metadataToImport,
                                   final PipelineUserEventsList events,
                                   final Map<String, CategoricalAttribute> categoricalAttributes) {
        final Map<String, PipeConfValue> currentMetadata = getCurrentMetadata(pipelineUser.getId());
        metadataToImport
                .forEach((key, value) -> fillMetadata(categoricalAttributes, currentMetadata, events, key, value));
        metadataManager.updateEntityMetadata(currentMetadata, pipelineUser.getId(), AclClass.PIPELINE_USER);
    }

    private void fillMetadata(final Map<String, CategoricalAttribute> categoricalAttributes,
                              final Map<String, PipeConfValue> metadata,
                              final PipelineUserEventsList events,
                              final String keyToAdd, final PipeConfValue valueToAdd) {
        addDataToMetadata(metadata, events, keyToAdd, valueToAdd);
        categoricalAttributes.computeIfPresent(keyToAdd, (attributeKey, attribute) -> {
            ListUtils.emptyIfNull(attribute.getValues()).stream()
                    .filter(attributeValue -> Objects.equals(attributeValue.getValue(), valueToAdd.getValue()))
                    .findFirst()
                    .ifPresent(attributeValue -> addLinksToMetadata(attributeValue.getLinks(),
                            metadata, events, categoricalAttributes));
            return attribute;
        });
    }

    private void addDataToMetadata(final Map<String, PipeConfValue> metadata,
                                   final PipelineUserEventsList events,
                                   final String keyToAdd, final PipeConfValue valueToAdd) {
        if (pairAlreadyExists(metadata, keyToAdd, valueToAdd)) {
            return;
        }
        metadata.put(keyToAdd, valueToAdd);
        events.info(messageHelper.getMessage(MessageConstants.EVENT_METADATA_ASSIGNED, keyToAdd, valueToAdd.getValue(),
                events.getUserName()));
    }

    private void addLinksToMetadata(final List<CategoricalAttributeValue> links,
                                    final Map<String, PipeConfValue> metadata,
                                    final PipelineUserEventsList events,
                                    final Map<String, CategoricalAttribute> categoricalAttributes) {
        if (CollectionUtils.isEmpty(links)) {
            return;
        }
        links.forEach(link -> {
            final String targetKey = link.getKey();
            final String targetValue = link.getValue();

            addDataToMetadata(metadata, events, targetKey, new PipeConfValue(null, targetValue));
            categoricalAttributes.computeIfPresent(targetKey, (attributeKey, attribute) -> {
                findLink(attribute, targetValue).ifPresent(attributeValue ->
                        addLinksToMetadata(attributeValue.getLinks(), metadata, events, categoricalAttributes));
                return attribute;
            });
        });
    }

    private Optional<CategoricalAttributeValue> findLink(final CategoricalAttribute attribute,
                                                         final String targetValue) {
        return ListUtils.emptyIfNull(attribute.getValues()).stream()
                .filter(attributeValue -> Objects.equals(attributeValue.getValue(), targetValue))
                .findFirst();
    }

    private boolean pairAlreadyExists(final Map<String, PipeConfValue> metadata, final String key,
                                      final PipeConfValue value) {
        return metadata.containsKey(key) && Objects.equals(metadata.get(key).getValue(), value.getValue());
    }
}
