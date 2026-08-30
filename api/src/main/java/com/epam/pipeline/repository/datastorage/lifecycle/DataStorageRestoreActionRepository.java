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

import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DataStorageRestoreActionRepository extends JpaRepository<StorageRestoreActionEntity, Long>,
        DataStorageRestoreActionRepositoryCustomQueries {

    List<StorageRestoreActionEntity> findByDatastorageId(Long datastorageId);

    void deleteByDatastorageId(Long datastorageId);

    @Query("SELECT DISTINCT a.datastorageId FROM StorageRestoreActionEntity a")
    Iterable<Long> loadDistinctDatastorageIds();

}
