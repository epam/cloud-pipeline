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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA {@link Specification} factory for {@link PlatformUsageCreditsUserBalanceEntity} queries.
 *
 * <p>All predicates are pushed down to the database — no post-load filtering is performed.
 * Use {@link #build(PlatformUsageCreditsUserBalanceFilterVO)} to obtain a combined spec, or call
 * the individual factory methods and compose them manually.
 *
 * <p>Example:
 * <pre>{@code
 * repository.findAll(
 *     PlatformUsageCreditsUserBalanceSpecification.build(filter),
 *     new PageRequest(filter.getPage(), filter.getPageSize()));
 * }</pre>
 */
public final class PlatformUsageCreditsUserBalanceSpecification {

    private static final String USER_ID = "userId";
    private static final String CURRENT_VALUE = "currentValue";

    private PlatformUsageCreditsUserBalanceSpecification() {
    }

    /**
     * Builds a combined specification from all filter fields.
     * All predicates are AND-ed at the database level.
     */
    public static Specification<PlatformUsageCreditsUserBalanceEntity> build(
            final PlatformUsageCreditsUserBalanceFilterVO filter) {
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (!CollectionUtils.isEmpty(filter.getUserIds())) {
                predicates.add(root.get(USER_ID).in(filter.getUserIds()));
            }
            if (filter.getValue() != null && StringUtils.isNotBlank(filter.getOperation())) {
                predicates.add(balancePredicate(root, cb, filter.getValue(), filter.getOperation()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate balancePredicate(final Root<PlatformUsageCreditsUserBalanceEntity> root,
                                              final CriteriaBuilder cb,
                                              final int value,
                                              final String operation) {
        switch (operation) {
            case "<": return cb.lessThan(root.get(CURRENT_VALUE), value);
            case ">": return cb.greaterThan(root.get(CURRENT_VALUE), value);
            case "=": return cb.equal(root.get(CURRENT_VALUE), value);
            default:  throw new IllegalArgumentException("Unsupported balance operation: " + operation);
        }
    }
}
