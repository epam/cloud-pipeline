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
import com.epam.pipeline.manager.metadata.CategoricalAttributeManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    private final CategoricalAttributeManager categoricalAttributeManager;
    private final MetadataManager metadataManager;
    private final RoleManager roleManager;

    /**
     * Registers a new {@link PipelineUser}, {@link Role}, {@link MetadataEntry} for users
     * and {@link CategoricalAttribute} if allowed. Otherwise, log event and skip action.
     * @param createUser true if user shall be created if not exists
     * @param createGroup true if role shall be created if not exists
     * @param attributesToCreate the list of metadata keys that shall be created if not exists
     * @param file the input file with users
     * @return the list of events that happened during user processing
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public List<PipelineUserEvent> importUsersFromFile(final boolean createUser, final boolean createGroup,
                                                       final List<String> attributesToCreate,
                                                       final MultipartFile file) {
        final List<PipelineUserEvent> events = new ArrayList<>();
        final List<CategoricalAttribute> categoricalAttributes = ListUtils
                .emptyIfNull(categoricalAttributeManager.loadAll());
        final List<PipelineUserWithStoragePath> users =
                new UserImporter(events, categoricalAttributes, attributesToCreate).importUsers(file);
        categoricalAttributeManager.updateCategoricalAttributes(categoricalAttributes);

        users.forEach(user -> {
            try {
                events.addAll(ListUtils.emptyIfNull(processUser(user, createUser, createGroup, categoricalAttributes)));
            } catch (Exception e) {
                log.error(String.format("Failed to process user '%s'", user.getUserName()), e);
                events.add(PipelineUserEvent.error(user.getUserName(), e.getMessage()));
            }
        });
        return events;
    }

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
            events.info(String.format("Pipeline user '%s' doesn't exist and cannot be created.", userName));
            return null;
        }
        if (Objects.isNull(loadedUser)) {
            final PipelineUser user = userManager.createUser(userName, Collections.emptyList(), Collections.emptyList(),
                    pipelineUser.getAttributes(), null);
            events.info(String.format("User '%s' successfully created.", userName));
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
            events.info(String.format("Role '%s' doesn't exist and cannot be created.", role.getName()));
            return null;
        }
        final Role createdRole = roleManager.createRole(role.getName(), role.isPredefined(), role.isUserDefault(),
                role.getDefaultStorageId());
        events.info(String.format("Role '%s' successfully created.", role.getName()));
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
        events.info(String.format("Role '%s' successfully assigned to user '%s'",
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
        categoricalAttributes.computeIfPresent(keyToAdd, (attributeKey, attribute) -> {
            ListUtils.emptyIfNull(attribute.getValues()).stream()
                    .filter(attributeValue -> Objects.equals(attributeValue.getValue(), valueToAdd.getValue()))
                    .findFirst()
                    .ifPresent(attributeValue -> {
                        addDataToMetadata(metadata, events, keyToAdd, valueToAdd);
                        addLinksToMetadata(attributeValue.getLinks(), metadata, events, categoricalAttributes);
                    });
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
        events.info(String.format("A new metadata '%s'='%s' added to user '%s'", keyToAdd, valueToAdd.getValue(),
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
