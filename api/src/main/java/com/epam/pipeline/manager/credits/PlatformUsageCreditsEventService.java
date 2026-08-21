/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsResetRequest;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsEventMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsEventRepository;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsEventSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsEventService {

    private static final String FIELD_CREATED_DATE = "createdDate";
    private static final int EXPORT_PAGE_SIZE = 1000;

    private final PlatformUsageCreditsEventRepository usageCreditsEventRepository;
    private final PlatformUsageCreditsEventMapper mapper;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;
    private final PlatformUsageCreditsUserBalanceService userBalanceService;

    /**
     * Applies balance update logic to each event, filters out zero-value results,
     * and persists the remaining events. Events without an {@code id} are always
     * treated as new; events with an {@code id} are deduplicated against the DB.
     * If {@code createdDate} is absent on an event, the current UTC time is used.
     *
     * @param events inbound update events from the monitoring service or admin API
     * @return persisted events, in the same order as the non-zero, non-duplicate input
     */
    @Transactional
    public List<PlatformUsageCreditsUpdateEvent> process(final List<PlatformUsageCreditsUpdateEvent> events) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> idsToCheck = events.stream()
                .map(PlatformUsageCreditsUpdateEvent::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        final Set<String> existingIds = idsToCheck.isEmpty()
                ? Collections.emptySet()
                : usageCreditsEventRepository.findExistingIds(idsToCheck);
        final List<PlatformUsageCreditsUpdateEvent> newEvents = events.stream()
                .filter(event -> !existingIds.contains(event.getId()))
                .map(userBalanceService::updateByEvent)
                .filter(event -> event.getValue() != 0)
                .peek(event -> {
                    if (Objects.isNull(event.getCreatedDate())) {
                        event.setCreatedDate(DateUtils.nowUTC());
                    }
                })
                .collect(Collectors.toList());
        if (newEvents.isEmpty()) {
            return Collections.emptyList();
        }
        usageCreditsEventRepository.save(newEvents.stream().map(mapper::toEntity).collect(Collectors.toList()));
        return newEvents;
    }

    /**
     * Returns a paginated, date-descending list of credit update events matching the filter.
     * Non-admin callers are silently restricted to their own events regardless of the {@code userIds}
     * field in the filter. {@code entities} and {@code withoutEntityLink} are mutually exclusive.
     *
     * @param filter query criteria and pagination settings
     * @return paged result containing matched events and total count
     * @throws IllegalArgumentException if both {@code entities} and {@code withoutEntityLink} are set
     */
    @Transactional(readOnly = true)
    public PagedResult<List<PlatformUsageCreditsUpdateEvent>> filter(final PlatformUsageCreditsEventFilterVO filter) {
        Assert.isTrue(!Boolean.TRUE.equals(filter.getWithoutEntityLink())
                        || ListUtils.emptyIfNull(filter.getEntities()).isEmpty(),
                "'entities' and 'withoutEntityLink' filters cannot be used simultaneously");
        final PlatformUsageCreditsEventFilterVO effectiveFilter = authManager.isAdmin()
                ? filter
                : restrictToCurrentUser(filter);
        Assert.isTrue(effectiveFilter.getPage() >= 1, "Page index must be >= 1");
        Assert.isTrue(effectiveFilter.getPageSize() > 0, "Page size must be > 0");
        final Page<PlatformUsageCreditsUpdateEventEntity> page = usageCreditsEventRepository.findAll(
                PlatformUsageCreditsEventSpecification.fromFilter(effectiveFilter),
                new PageRequest(effectiveFilter.getPage() - 1, effectiveFilter.getPageSize(),
                        new Sort(Sort.Direction.DESC, FIELD_CREATED_DATE)));
        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDto).collect(Collectors.toList()),
                (int) page.getTotalElements());
    }

    /**
     * Streams all credits events matching the filter as UTF-8 CSV directly to the given output stream.
     * The filter body is identical to {@link #filter}; the {@code page} and {@code pageSize}
     * fields are ignored — all matching rows are fetched page by page at {@value EXPORT_PAGE_SIZE}
     * records per page. Non-admin callers are restricted to their own events.
     *
     * <p>The output stream is flushed but not closed — the caller owns the stream lifecycle.
     *
     * @param filter       query criteria (pagination fields ignored)
     * @param outputStream destination stream; must be open and writable for the duration of this call
     */
    @Transactional(readOnly = true)
    public void export(final PlatformUsageCreditsEventFilterVO filter, final OutputStream outputStream) {
        final PlatformUsageCreditsEventExporter exporter = new PlatformUsageCreditsEventExporter();
        try (CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            csvWriter.writeNext(exporter.header(), false);
            int page = 1;
            List<PlatformUsageCreditsUpdateEvent> batch;
            do {
                final PlatformUsageCreditsEventFilterVO pageFilter =
                        filter.toBuilder().page(page++).pageSize(EXPORT_PAGE_SIZE).build();
                batch = filter(pageFilter).getElements();
                exporter.lines(batch).forEach(line -> csvWriter.writeNext(line, false));
            } while (batch.size() == EXPORT_PAGE_SIZE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export credits events to CSV", e);
        }
    }

    /**
     * Resets credits to the requested value for the given users and persists one
     * {@link com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction.ActionType#RESET RESET} event per user.
     * If {@code userIds} in the request is null or empty, the reset is applied to all users.
     *
     * @param resetRequest target value and optional list of user IDs to reset
     * @return persisted reset events, one per resolved user
     */
    @Transactional
    public List<PlatformUsageCreditsUpdateEvent> reset(final PlatformUsageCreditsResetRequest resetRequest) {
        userBalanceService.reset(resetRequest.getValue(), resetRequest.getUserIds());
        final Collection<PipelineUser> users = CollectionUtils.isEmpty(resetRequest.getUserIds())
                ? userManager.loadAllUsers()
                : userManager.loadUsersById(resetRequest.getUserIds());
        final List<PlatformUsageCreditsUpdateEventEntity> events = users.stream()
                .map(user -> PlatformUsageCreditsUpdateEventEntity.builder()
                        .userId(user.getId())
                        .incidentType(PlatformUsageCreditsUpdateAction.ActionType.RESET)
                        .value(resetRequest.getValue())
                        .createdDate(DateUtils.nowUTC())
                        .build())
                .collect(Collectors.toList());
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        return usageCreditsEventRepository.save(events).stream().map(mapper::toDto).collect(Collectors.toList());
    }

    private PlatformUsageCreditsEventFilterVO restrictToCurrentUser(final PlatformUsageCreditsEventFilterVO filter) {
        final String username = authManager.getAuthorizedUser();
        final PipelineUser user = userManager.loadUserByName(username);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, username));
        final List<Long> requestedUserIds = ListUtils.emptyIfNull(filter.getUserIds());
        if (!requestedUserIds.isEmpty()) {
            if (!requestedUserIds.contains(user.getId())) {
                throw new AccessDeniedException(
                        "Non-admin user '" + username + "' is not allowed to query events for other users");
            }
            if (requestedUserIds.size() > 1) {
                log.warn("Non-admin user '{}' requested events for userIds {}; restricting to [{}]",
                        username, requestedUserIds, user.getId());
            }
        }
        return filter.toBuilder()
                .userIds(Collections.singletonList(user.getId()))
                .build();
    }

}
