/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.quota;

import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.AppliedQuotaEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

public final class AppliedQuotaSpecification {

    public static final String ACTION = "action";
    public static final String QUOTA = "quota";
    public static final String QUOTA_TYPE = "type";
    public static final String SUBJECT = "subject";
    public static final String QUOTA_GROUP = "quotaGroup";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String ID = "id";
    private AppliedQuotaSpecification() {
        //
    }

    public static Specification<AppliedQuotaEntity> userActiveQuotas(final PipelineUser user,
                                                                     final String billingCenter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            final Path<Object> quota = root.get(ACTION).get(QUOTA);
            final Path<Object> quotaType = quota.get(QUOTA_TYPE);
            final Path<Object> subject = quota.get(SUBJECT);

            final List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(quota.get(QUOTA_GROUP), QuotaGroup.GLOBAL));
            predicates.add(criteriaBuilder.equal(quotaType, QuotaType.OVERALL));
            predicates.add(criteriaBuilder.and(
                    criteriaBuilder.equal(quotaType, QuotaType.USER),
                    criteriaBuilder.equal(subject, user.getUserName().toUpperCase())
            ));
            predicates.add(criteriaBuilder.and(
                    criteriaBuilder.equal(quotaType, QuotaType.GROUP),
                    criteriaBuilder.isTrue(subject.in(user.getAuthorities()))
            ));
            if (StringUtils.isNotBlank(billingCenter)) {
                predicates.add(criteriaBuilder.and(
                        criteriaBuilder.equal(quotaType, QuotaType.BILLING_CENTER.name()),
                        criteriaBuilder.equal(subject, billingCenter)
                ));
            }
            return criteriaBuilder.and(isActive(root, criteriaBuilder),
                    criteriaBuilder.or(predicates.toArray(new Predicate[]{})));
        };
    }

    public static Specification<AppliedQuotaEntity> activeQuotas(final Long actionId) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            return criteriaBuilder.and(isActive(root, criteriaBuilder), criteriaBuilder.equal(
                    root.get(ACTION).get(ID), actionId));
        };
    }

    public static Predicate isActive(final Root<AppliedQuotaEntity> root,
                                     final CriteriaBuilder criteriaBuilder) {
        //TODO: fix for UTC
        return criteriaBuilder.between(criteriaBuilder.currentDate(),
                root.get(FROM), root.get(TO));
    }
}
