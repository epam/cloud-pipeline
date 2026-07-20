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
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsEventMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsEventRepository;
import com.epam.pipeline.vo.SecuredEntityVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.persistence.criteria.Predicate;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsEventService {

    private static final String FIELD_RULE_ID = "ruleId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_INCIDENT_TYPE = "incidentType";
    private static final String FIELD_ENTITY_CLASS = "entityClass";
    private static final String FIELD_ENTITY_ID = "entityId";
    private static final String FIELD_CREATED_DATE = "createdDate";

    private final PlatformUsageCreditsEventRepository repository;
    private final PlatformUsageCreditsEventMapper mapper;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    @Transactional
    public List<PlatformUsageCreditsUpdateEvent> save(final List<PlatformUsageCreditsUpdateEvent> events) {
        ListUtils.emptyIfNull(events).forEach(event -> {
            final PlatformUsageCreditsUpdateEventEntity entity = mapper.toEntity(event);
            entity.setCreatedDate(Optional.ofNullable(event.getCreatedDate()).orElseGet(DateUtils::nowUTC));
            repository.insertIfAbsent(
                    entity.getUserId(),
                    entity.getRuleId(),
                    entity.getEntityClass(),
                    entity.getEntityId(),
                    entity.getIncidentType().name(),
                    entity.getValue(),
                    entity.getMessage(),
                    Timestamp.valueOf(entity.getCreatedDate()));
        });
        return events;
    }

    public PagedResult<List<PlatformUsageCreditsUpdateEvent>> filter(
            final PlatformUsageCreditsEventFilterVO filter) {
        final Page<PlatformUsageCreditsUpdateEventEntity> page = repository.findAll(
                buildSpec(filter),
                new PageRequest(filter.getPage(), filter.getPageSize(),
                        new Sort(Sort.Direction.DESC, FIELD_CREATED_DATE)));
        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDto).collect(Collectors.toList()),
                (int) page.getTotalElements());
    }

    public PagedResult<List<PlatformUsageCreditsUpdateEvent>> findMy(final int page, final int pageSize) {
        final String username = authManager.getAuthorizedUser();
        final PipelineUser user = userManager.loadUserByName(username);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, username));
        return filter(PlatformUsageCreditsEventFilterVO.builder()
                .userIds(Collections.singletonList(user.getId()))
                .page(page)
                .pageSize(pageSize)
                .build());
    }

    private Specification<PlatformUsageCreditsUpdateEventEntity> buildSpec(
            final PlatformUsageCreditsEventFilterVO filter) {
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (filter.getRuleId() != null) {
                predicates.add(cb.equal(root.get(FIELD_RULE_ID), filter.getRuleId()));
            }
            if (!ListUtils.emptyIfNull(filter.getUserIds()).isEmpty()) {
                predicates.add(root.get(FIELD_USER_ID).in(filter.getUserIds()));
            }
            if (!ListUtils.emptyIfNull(filter.getIncidentTypes()).isEmpty()) {
                predicates.add(root.get(FIELD_INCIDENT_TYPE).in(filter.getIncidentTypes()));
            }
            if (!ListUtils.emptyIfNull(filter.getEntities()).isEmpty()) {
                predicates.add(root.get(FIELD_ENTITY_CLASS).in(filter.getEntities().stream()
                        .map(SecuredEntityVO::getEntityClass).collect(Collectors.toList())));
                predicates.add(root.get(FIELD_ENTITY_ID).in(filter.getEntities().stream()
                        .map(SecuredEntityVO::getEntityId).collect(Collectors.toList())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
