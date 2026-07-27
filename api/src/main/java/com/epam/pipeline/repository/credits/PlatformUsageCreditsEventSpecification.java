/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.credits;

import com.epam.pipeline.dto.credits.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import com.epam.pipeline.vo.SecuredEntityVO;
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PlatformUsageCreditsEventSpecification {

    public static final String FIELD_RULE_ID = "ruleId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_INCIDENT_TYPE = "incidentType";
    public static final String FIELD_ENTITY_CLASS = "entityClass";
    public static final String FIELD_ENTITY_ID = "entityId";
    public static final String FIELD_CREATED_DATE = "createdDate";

    private PlatformUsageCreditsEventSpecification() {
        //
    }

    public static Specification<PlatformUsageCreditsUpdateEventEntity> fromFilter(
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
                final Predicate[] pairs = filter.getEntities().stream()
                        .map(e -> cb.and(
                                cb.equal(root.get(FIELD_ENTITY_CLASS), e.getEntityClass()),
                                cb.equal(root.get(FIELD_ENTITY_ID), e.getEntityId())))
                        .toArray(Predicate[]::new);
                predicates.add(cb.or(pairs));
            }
            if (Boolean.TRUE.equals(filter.getWithoutEntityLink())) {
                predicates.add(cb.isNull(root.get(FIELD_ENTITY_CLASS)));
            }
            final LocalDateTime from = filter.getFrom();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_CREATED_DATE), from));
            }
            final LocalDateTime to = filter.getTo();
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(FIELD_CREATED_DATE), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
