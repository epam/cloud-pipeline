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
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsEventService {

    private static final String FIELD_CREATED_DATE = "createdDate";

    private final PlatformUsageCreditsEventRepository usageCreditsEventRepository;
    private final PlatformUsageCreditsEventMapper mapper;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    /**
     * Applies balance update logic to each request, filters out zero-value results,
     * and persists the remaining events. If {@code createdDate} is absent on a request,
     * the current UTC time is used.
     *
     * @param requests inbound update requests from the monitoring service
     * @return persisted events, in the same order as the non-zero requests
     */
    @Transactional
    public List<PlatformUsageCreditsUpdateEvent> process(final List<PlatformUsageCreditsUpdateRequest> requests) {
        final List<PlatformUsageCreditsUpdateRequest> applied = ListUtils.emptyIfNull(requests).stream()
                .map(this::applyBalanceUpdate)
                .filter(request -> request.getValue() != 0)
                .collect(Collectors.toList());
        final List<PlatformUsageCreditsUpdateEventEntity> entities = applied.stream()
                .map(request -> {
                    final PlatformUsageCreditsUpdateEventEntity entity = mapper.toEntity(request);
                    if (entity.getCreatedDate() == null) {
                        entity.setCreatedDate(DateUtils.nowUTC());
                    }
                    return entity;
                })
                .collect(Collectors.toList());
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return usageCreditsEventRepository.save(entities).stream().map(mapper::toDto).collect(Collectors.toList());
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
    public PagedResult<List<PlatformUsageCreditsUpdateEvent>> filter(final PlatformUsageCreditsEventFilterVO filter) {
        Assert.isTrue(!Boolean.TRUE.equals(filter.getWithoutEntityLink())
                        || ListUtils.emptyIfNull(filter.getEntities()).isEmpty(),
                "'entities' and 'withoutEntityLink' filters cannot be used simultaneously");
        final PlatformUsageCreditsEventFilterVO effectiveFilter = authManager.isAdmin()
                ? filter
                : restrictToCurrentUser(filter);
        final Page<PlatformUsageCreditsUpdateEventEntity> page = usageCreditsEventRepository.findAll(
                PlatformUsageCreditsEventSpecification.fromFilter(effectiveFilter),
                new PageRequest(effectiveFilter.getPage(), effectiveFilter.getPageSize(),
                        new Sort(Sort.Direction.DESC, FIELD_CREATED_DATE)));
        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDto).collect(Collectors.toList()),
                (int) page.getTotalElements());
    }

    /**
     * Resets credits to the requested value for the given users and persists one
     * {@link com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction.ActionType#RESET RESET} event per user.
     * If {@code userIds} in the request is null or empty, the reset is applied to all users.
     * Users that cannot be found by the supplied IDs are silently skipped.
     *
     * @param resetRequest target value and optional list of user IDs to reset
     * @return persisted reset events, one per resolved user
     */
    @Transactional
    public List<PlatformUsageCreditsUpdateEvent> reset(final PlatformUsageCreditsResetRequest resetRequest) {
        applyCreditReset(resetRequest.getValue(), resetRequest.getUserIds());
        final Collection<PipelineUser> users = CollectionUtils.isEmpty(resetRequest.getUserIds())
                ? userManager.loadAllUsers()
                : userManager.loadUsersById(resetRequest.getUserIds());
        final List<PlatformUsageCreditsUpdateEventEntity> events = users.stream()
                .map(user -> PlatformUsageCreditsUpdateEventEntity.builder()
                        .userId(user.getId())
                        .incidentType(PlatformUsageCreditsUpdateAction.ActionType.RESET)
                        .value(resetRequest.getValue())
                        .build())
                .collect(Collectors.toList());
        return usageCreditsEventRepository.save(events).stream().map(mapper::toDto).collect(Collectors.toList());
    }

    // TODO: implement balance update logic
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void applyCreditReset(final int value, final List<Long> users) {

    }

    // TODO: implement balance update logic
    private PlatformUsageCreditsUpdateRequest applyBalanceUpdate(final PlatformUsageCreditsUpdateRequest request) {
        return request;
    }

    private PlatformUsageCreditsEventFilterVO restrictToCurrentUser(final PlatformUsageCreditsEventFilterVO filter) {
        final String username = authManager.getAuthorizedUser();
        final PipelineUser user = userManager.loadUserByName(username);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, username));
        final List<Long> requestedUserIds = filter.getUserIds();
        if (!ListUtils.emptyIfNull(requestedUserIds).isEmpty()
                && !(requestedUserIds.size() == 1 && requestedUserIds.get(0).equals(user.getId()))) {
            log.warn("Non-admin user '{}' requested events for userIds {}; overriding to [{}]",
                    username, requestedUserIds, user.getId());
        }
        return PlatformUsageCreditsEventFilterVO.builder()
                .entities(filter.getEntities())
                .ruleId(filter.getRuleId())
                .incidentTypes(filter.getIncidentTypes())
                .userIds(Collections.singletonList(user.getId()))
                .page(filter.getPage())
                .pageSize(filter.getPageSize())
                .build();
    }

}
