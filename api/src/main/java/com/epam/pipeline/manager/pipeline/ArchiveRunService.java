/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveRunService {

    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;
    private final MetadataDao metadataDao;
    private final UserManager userManager;
    private final RoleManager roleManager;
    private final ArchiveRunAsynchronousService archiveRunAsyncService;

    public void archiveRuns(final String identifier, final boolean principal, final Integer days) {
        final String metadataKey = preferenceManager.getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_METADATA_KEY);

        final Map<String, Date> ownersAndDates = principal
                ? findOwnersByUser(identifier, metadataKey, days)
                : findOwnersByRole(identifier, metadataKey, days);

        archiveRunsForOwners(ownersAndDates);
    }

    public void archiveRuns() {
        final String metadataKey = preferenceManager.getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_METADATA_KEY);

        final Map<String, Date> ownersAndDates = findAllOwnersAndDates(metadataKey);

        archiveRunsForOwners(ownersAndDates);
    }

    private void archiveRunsForOwners(final Map<String, Date> ownersAndDates) {
        if (MapUtils.isEmpty(ownersAndDates)) {
            log.debug("No run owners found to archive runs.");
            return;
        }

        final List<Long> terminalStates = Arrays.stream(TaskStatus.values())
                .filter(TaskStatus::isFinal)
                .map(TaskStatus::getId)
                .collect(toList());

        final Integer chunkSize = preferenceManager.getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_CHUNK_SIZE);

        archiveRunAsyncService.archiveRunsAsynchronous(ownersAndDates, terminalStates, chunkSize);
    }

    private Integer metadataToDays(final Map<String, PipeConfValue> metadata, final String key,
                                   final EntityVO entry) {
        final PipeConfValue pipeConfValue = metadata.get(key);
        final String value = pipeConfValue.getValue();
        if (!NumberUtils.isDigits(value)) {
            log.error("Metadata value for key '{}' is not numeric for '{}'='{}'", key, entry.getEntityClass(),
                    entry.getEntityId());
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_ARCHIVE_RUN_METADATA_NOT_NUMERIC, key));
        }
        return Integer.parseInt(value);
    }

    private Date daysToDate(final Optional<Integer> optionalDays) {
        final Integer days = optionalDays.orElseThrow(IllegalArgumentException::new);
        return DateUtils.convertLocalDateTimeToDate(DateUtils.nowUTC().minusDays(days));
    }

    private Map<String, Date> findAllOwnersAndDates(final String metadataKey) {
        final Map<Long, Optional<Integer>> daysByUser = findAllOwnersByRole(metadataKey);

        ListUtils.emptyIfNull(metadataDao.searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, metadataKey))
                .forEach(entry -> daysByUser.put(entry.getEntity().getEntityId(),
                        Optional.of(metadataToDays(entry.getData(), metadataKey, entry.getEntity()))));

        final Map<Long, String> owners = userManager.loadUsersById(new ArrayList<>(daysByUser.keySet())).stream()
                .collect(toMap(PipelineUser::getId, PipelineUser::getUserName));
        return daysByUser.entrySet().stream()
                .filter(entry -> owners.containsKey(entry.getKey()))
                .collect(toMap(entry -> owners.get(entry.getKey()).toLowerCase(), entry ->
                        daysToDate(entry.getValue())));
    }

    private Map<Long, Optional<Integer>> findAllOwnersByRole(final String metadataKey) {
        final Map<Long, List<PipelineUser>> usersByRole = Optional.ofNullable(
                roleManager.loadAllRoles(true).stream()).orElse(Stream.empty())
                .filter(role -> role instanceof ExtendedRole)
                .map(ExtendedRole.class::cast)
                .collect(toMap(ExtendedRole::getId, ExtendedRole::getUsers));
        final Map<Long, Integer> daysByRole = ListUtils.emptyIfNull(metadataDao
                        .searchMetadataEntriesByClassAndKey(AclClass.ROLE, metadataKey)).stream()
                .collect(toMap(entry -> entry.getEntity().getEntityId(), entry ->
                        metadataToDays(entry.getData(), metadataKey, entry.getEntity())));
        return usersByRole.entrySet().stream()
                .filter(entry -> daysByRole.containsKey(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()
                        .map(user -> Pair.create(user.getId(), daysByRole.get(entry.getKey()))))
                .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, reducing(Integer::min))));
    }

    private int findDaysInMetadata(final Long entityId, final AclClass entityClass, final String metadataKey) {
        final EntityVO entityVO = new EntityVO(entityId, entityClass);
        final MetadataEntry metadataEntry = metadataDao.loadMetadataItem(entityVO);
        Assert.state(Objects.nonNull(metadataEntry)
                && MapUtils.isNotEmpty(metadataEntry.getData())
                && metadataEntry.getData().containsKey(metadataKey),
                messageHelper.getMessage(MessageConstants.ERROR_ARCHIVE_RUN_METADATA_NOT_FOUND,
                        metadataKey, entityId, entityClass.name()));
        return metadataToDays(metadataEntry.getData(), metadataKey, entityVO);
    }

    private Map<String, Date> findOwnersByUser(final String userIdentifier, final String metadataKey,
                                               final Integer specifiedDays) {
        final PipelineUser user = userManager.loadByNameOrId(userIdentifier);
        final int days = Objects.nonNull(specifiedDays)
                ? specifiedDays :
                findDaysInMetadata(user.getId(), AclClass.PIPELINE_USER, metadataKey);
        return Collections.singletonMap(user.getUserName(), daysToDate(Optional.of(days)));
    }

    private Map<String, Date> findOwnersByRole(final String roleIdentifier, final String metadataKey,
                                               final Integer specifiedDays) {
        final Role role = roleManager.loadRoleByNameOrId(roleIdentifier);
        final int days = Objects.nonNull(specifiedDays)
                ? specifiedDays
                : findDaysInMetadata(role.getId(), AclClass.ROLE, metadataKey);
        final List<PipelineUser> users = ListUtils.emptyIfNull(roleManager.loadRoleWithUsers(role.getId()).getUsers());
        final List<Long> userIds = users.stream().map(PipelineUser::getId).collect(toList());
        final Map<Long, Integer> usersDays = Objects.nonNull(specifiedDays)
                ? Collections.emptyMap()
                : ListUtils.emptyIfNull(metadataDao
                .searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, metadataKey)).stream()
                .filter(entry -> userIds.contains(entry.getEntity().getEntityId()))
                .collect(toMap(entry -> entry.getEntity().getEntityId(), entry ->
                        metadataToDays(entry.getData(), metadataKey, entry.getEntity())));
        return users.stream().collect(toMap(PipelineUser::getUserName, user ->
                daysToDate(Optional.of(usersDays.getOrDefault(user.getId(), days)))));
    }
}
