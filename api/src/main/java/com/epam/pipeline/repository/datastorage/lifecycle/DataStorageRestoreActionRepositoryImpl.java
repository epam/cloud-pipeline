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

import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionSearchFilter;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreStatus;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreActionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DataStorageRestoreActionRepositoryImpl implements DataStorageRestoreActionRepositoryCustomQueries {

    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String ANY_SIGN = "%";
    public static final String DATASTORAGE_ID = "datastorageId";

    final EntityManager em;

    public List<StorageRestoreActionEntity> filterBy(final StorageRestoreActionSearchFilter filter) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<StorageRestoreActionEntity> cq = cb.createQuery(StorageRestoreActionEntity.class);
        final Root<StorageRestoreActionEntity> restoreAction = cq.from(StorageRestoreActionEntity.class);
        final List<Predicate> predicates = new ArrayList<>();
        Assert.notNull(filter.getDatastorageId(), "datastorageId should be provided!");
        predicates.add(cb.equal(restoreAction.get(DATASTORAGE_ID), filter.getDatastorageId()));
        if (filter.getPath() != null) {
            if (filter.getSearchType() == null ||
                    filter.getSearchType() == StorageRestoreActionSearchFilter.SearchType.SEARCH_PARENT) {
                final Expression<String> pathFromDb = cb.concat(restoreAction.get(PATH), ANY_SIGN);
                predicates.add(cb.like(cb.literal(filter.getPath()), pathFromDb));
            } else if (filter.getSearchType() == StorageRestoreActionSearchFilter.SearchType.SEARCH_CHILD) {
                predicates.add(cb.like(restoreAction.get(PATH), filter.getPath() + ANY_SIGN));
            }
        }
        if (!CollectionUtils.isEmpty(filter.getStatuses())) {
            final CriteriaBuilder.In<StorageRestoreStatus> inStatuses = cb.in(restoreAction.get(STATUS));
            filter.getStatuses().forEach(inStatuses::value);
            predicates.add(inStatuses);
        }
        cq.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(cq).getResultList();
    }
}
