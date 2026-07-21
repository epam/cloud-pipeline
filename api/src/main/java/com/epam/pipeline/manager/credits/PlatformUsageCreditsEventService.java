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
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.Timestamp;
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

    @Transactional
    public List<PlatformUsageCreditsUpdateRequest> process(final List<PlatformUsageCreditsUpdateRequest> requests) {
        return ListUtils.emptyIfNull(requests).stream()
                .map(this::applyBalanceUpdate)
                .filter(applied -> applied.getValue() != 0)
                .peek(applied -> {
                    final PlatformUsageCreditsUpdateEventEntity entity = mapper.toEntity(applied);
                    if (entity.getCreatedDate() == null) {
                        entity.setCreatedDate(DateUtils.nowUTC());
                    }
                    usageCreditsEventRepository.insertIfAbsent(
                            entity.getUserId(),
                            entity.getRuleId(),
                            entity.getEntityClass(),
                            entity.getEntityId(),
                            entity.getIncidentType().name(),
                            entity.getValue(),
                            entity.getMessage(),
                            Timestamp.valueOf(entity.getCreatedDate()));
                })
                .collect(Collectors.toList());
    }

    public PagedResult<List<PlatformUsageCreditsUpdateEvent>> filter(final PlatformUsageCreditsEventFilterVO filter) {
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
