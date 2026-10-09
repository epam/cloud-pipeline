/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.repository.async;

import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.entity.async.AsyncTaskEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class AsyncTaskSpecification {

    private static final String OWNER = "owner";
    private static final String TYPE = "type";
    private static final String STATUS = "status";
    private static final String CREATED = "created";
    private static final String STATUS_CHANGED = "statusChanged";
    private static final String FINISHED = "finished";

    private AsyncTaskSpecification() {
        // no-op
    }

    public static Specification<AsyncTaskEntity> of(final AsyncTaskFilterVO filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(filter.getOwner())) {
                predicates.add(builder.equal(root.get(OWNER), filter.getOwner()));
            }
            if (filter.getType() != null) {
                predicates.add(builder.equal(root.get(TYPE), filter.getType()));
            }
            if (CollectionUtils.isNotEmpty(filter.getStatuses())) {
                predicates.add(root.get(STATUS).in(filter.getStatuses()));
            }
            if (filter.getCreatedFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(CREATED), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get(CREATED), filter.getCreatedTo()));
            }
            if (filter.getStatusChangedBefore() != null) {
                predicates.add(builder.lessThan(root.get(STATUS_CHANGED), filter.getStatusChangedBefore()));
            }
            if (filter.getFinishedBefore() != null) {
                predicates.add(builder.lessThan(root.get(FINISHED), filter.getFinishedBefore()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
