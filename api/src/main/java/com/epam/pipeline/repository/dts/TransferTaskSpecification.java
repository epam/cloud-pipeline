/*
 * Copyright 20122 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.dts;

import com.epam.pipeline.controller.vo.dts.TransferTaskFilter;
import com.epam.pipeline.entity.dts.TransferTaskEntity;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class TransferTaskSpecification {

    public static final String REGISTRY_ID = "registryId";
    public static final String STATUS = "status";
    public static final String CREATED = "created";
    public static final String STARTED = "started";
    public static final String FINISHED = "finished";
    private TransferTaskSpecification() {
        //
    }

    public static Specification<TransferTaskEntity> filteredTasks(final TransferTaskFilter filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (filter.getRegistryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get(REGISTRY_ID), filter.getRegistryId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get(STATUS), filter.getStatus()));
            }
            if (filter.getCreatedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(CREATED), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(CREATED), filter.getCreatedTo()));
            }
            if (filter.getStartedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(STARTED), filter.getStartedFrom()));
            }
            if (filter.getStartedTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(STARTED), filter.getStartedTo()));
            }
            if (filter.getFinishedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(FINISHED), filter.getFinishedFrom()));
            }
            if (filter.getFinishedTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(FINISHED), filter.getFinishedTo()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
        };
    }
}
