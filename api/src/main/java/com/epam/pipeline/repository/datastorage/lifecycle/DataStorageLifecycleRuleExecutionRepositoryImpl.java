/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.datastorage.lifecycle;

import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecutionStatus;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleExecutionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DataStorageLifecycleRuleExecutionRepositoryImpl
         implements DataStorageLifecycleRuleRepositoryCustomQueries {


    final EntityManager em;

    @Override
    public Iterable<StorageLifecycleRuleExecutionEntity> findByRuleIdPathAndStatus(
            final Long ruleId, final String path, final StorageLifecycleRuleExecutionStatus status) {


        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<StorageLifecycleRuleExecutionEntity> cq =
                cb.createQuery(StorageLifecycleRuleExecutionEntity.class);

        final Root<StorageLifecycleRuleExecutionEntity> ruleExecution =
                cq.from(StorageLifecycleRuleExecutionEntity.class);
        final List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(ruleExecution.get("ruleId"), ruleId));
        if (path != null) {
            predicates.add(cb.equal(ruleExecution.get("path"), path));
        }
        if (status != null) {
            predicates.add(cb.equal(ruleExecution.get("status"), status));
        }
        cq.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(cq).getResultList();
    }
}
