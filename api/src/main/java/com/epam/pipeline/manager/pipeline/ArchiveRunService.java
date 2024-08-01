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

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.dao.pipeline.ArchiveRunDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.run.RunServiceUrlDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private final MetadataDao metadataDao;
    private final UserManager userManager;
    private final RoleManager roleManager;
    private final ArchiveRunDao archiveRunDao;
    private final PipelineRunDao pipelineRunDao;
    private final RunLogDao runLogDao;
    private final RestartRunDao restartRunDao;
    private final RunServiceUrlDao runServiceUrlDao;
    private final RunStatusDao runStatusDao;
    private final StopServerlessRunDao stopServerlessRunDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void archiveRuns() {
        final String metadataKey = preferenceManager.getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_METADATA_KEY);
        final List<Long> terminalStates = Arrays.stream(TaskStatus.values())
                .filter(TaskStatus::isFinal)
                .map(TaskStatus::getId)
                .collect(toList());

        final Map<String, Date> ownersAndDates = findOwnersAndDates(metadataKey);
        if (MapUtils.isEmpty(ownersAndDates)) {
            log.debug("No run owners found to archive runs.");
            return;
        }

        final List<PipelineRun> runsToArchive = ListUtils.emptyIfNull(
                pipelineRunDao.loadRunsByOwnerAndEndDateBeforeAndStatusIn(ownersAndDates, terminalStates));

        if (CollectionUtils.isEmpty(runsToArchive)) {
            log.debug("No runs found to archive.");
            return;
        }
        log.debug("Transferring '{}' runs to archive.", runsToArchive.size());
        final List<Long> runIds = runsToArchive.stream().map(PipelineRun::getId).collect(toList());
        final List<PipelineRun> children = ListUtils.emptyIfNull(pipelineRunDao.loadRunsByParentRuns(runIds));
        runsToArchive.addAll(children);
        runIds.addAll(children.stream().map(PipelineRun::getId).collect(toList()));
        archiveRunDao.batchInsertArchiveRuns(runsToArchive);
        deleteRunsAndDependents(runIds);
    }

    private Integer metadataToDays(final Map<String, PipeConfValue> metadata, final String key,
                                   final EntityVO entry) {
        final PipeConfValue pipeConfValue = metadata.get(key);
        final String value = pipeConfValue.getValue();
        if (!NumberUtils.isDigits(value)) {
            log.error("Metadata value for key '{}' is not numeric for '{}'='{}'", key, entry.getEntityClass(),
                    entry.getEntityId());
            throw new IllegalStateException(String.format("Metadata value for key '%s' is not numeric.", key));
        }
        return Integer.parseInt(value);
    }

    private Date daysToDate(final Optional<Integer> optionalDays) {
        final Integer days = optionalDays.orElseThrow(IllegalArgumentException::new);
        return DateUtils.convertLocalDateTimeToDate(DateUtils.nowUTC().minusDays(days));
    }

    private void deleteRunsAndDependents(final List<Long> runIds) {
        pipelineRunDao.deleteRunSidsByRunIdIn(runIds);
        runLogDao.deleteTaskByRunIdsIn(runIds);
        restartRunDao.deleteRestartRunByIdsIn(runIds);
        runServiceUrlDao.deleteByRunIdsIn(runIds);
        runStatusDao.deleteRunStatusByRunIdsIn(runIds);
        stopServerlessRunDao.deleteByRunIdIn(runIds);
        pipelineRunDao.deleteRunByIdIn(runIds);
    }

    private Map<String, Date> findOwnersAndDates(final String metadataKey) {
        final Map<Long, List<PipelineUser>> usersByRole = Optional.ofNullable(
                roleManager.loadAllRoles(true).stream()).orElse(Stream.empty())
                .filter(role -> role instanceof ExtendedRole)
                .map(ExtendedRole.class::cast)
                .collect(toMap(ExtendedRole::getId, ExtendedRole::getUsers));
        final Map<Long, Integer> daysByRole = ListUtils.emptyIfNull(metadataDao
                        .searchMetadataEntriesByClassAndKey(AclClass.ROLE, metadataKey)).stream()
                .collect(toMap(entry -> entry.getEntity().getEntityId(), entry ->
                        metadataToDays(entry.getData(), metadataKey, entry.getEntity())));
        final Map<Long, Optional<Integer>> daysByUser = usersByRole.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(user -> Pair.create(user.getId(), daysByRole.get(entry.getKey()))))
                .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, reducing(Integer::min))));

        final List<MetadataEntry> usersMetadata = ListUtils.emptyIfNull(metadataDao
                .searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, metadataKey));
        usersMetadata.forEach(entry -> daysByUser.put(entry.getEntity().getEntityId(),
                Optional.of(metadataToDays(entry.getData(), metadataKey, entry.getEntity()))));

        final Map<Long, String> pipelineUsers = userManager.loadUsersById(new ArrayList<>(daysByUser.keySet())).stream()
                .collect(toMap(PipelineUser::getId, PipelineUser::getUserName));
        return daysByUser.entrySet().stream()
                .filter(entry -> pipelineUsers.containsKey(entry.getKey()))
                .collect(toMap(entry -> pipelineUsers.get(entry.getKey()).toLowerCase(), entry ->
                        daysToDate(entry.getValue())));
    }
}
