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
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.CategoricalAttributeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersFileImportManager {
    private final UserImportManager userImportManager;
    private final CategoricalAttributeManager categoricalAttributeManager;

    /**
     * Registers a new {@link PipelineUser}, {@link Role} and {@link MetadataEntry} for users
     * if allowed. Otherwise, log event and skip action.
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
        final List<CategoricalAttribute> categoricalAttributes = ListUtils
                .emptyIfNull(categoricalAttributeManager.loadAll());
        final List<PipelineUserEvent> events = new ArrayList<>();
        final List<PipelineUserWithStoragePath> users =
                new UserImporter(categoricalAttributes, attributesToCreate, events).importUsers(file);
        if (CollectionUtils.isNotEmpty(categoricalAttributes)) {
            categoricalAttributeManager.updateCategoricalAttributes(categoricalAttributes);
        }

        events.addAll(users.stream()
                .flatMap(user -> {
                    try {
                        return ListUtils.emptyIfNull(userImportManager
                                .processUser(user, createUser, createGroup, categoricalAttributes)).stream();
                    } catch (Exception e) {
                        log.error(String.format("Failed to process user '%s'", user.getUserName()), e);
                        return Stream.of(PipelineUserEvent.error(user.getUserName(), e.getMessage()));
                    }
                })
                .collect(Collectors.toList()));
        return events;
    }
}
