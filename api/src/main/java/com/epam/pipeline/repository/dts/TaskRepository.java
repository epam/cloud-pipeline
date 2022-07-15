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

package com.epam.pipeline.repository.dts;

import com.epam.pipeline.entity.dts.TaskStatus;
import com.epam.pipeline.entity.dts.TransferTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface TaskRepository extends JpaRepository<TransferTaskEntity, Long>,
        JpaSpecificationExecutor<TransferTaskEntity> {
    @Query("SELECT t FROM TransferTaskEntity t WHERE (?1 is null or t.registryId = ?1) and " +
            "(cast(cast(?2 as text) as timestamp) is null or t.created >= cast(cast(?2 as text) as timestamp)) and " +
            "(cast(cast(?3 as text) as timestamp) is null or t.created <= cast(cast(?3 as text) as timestamp)) and " +
            "(cast(cast(?4 as text) as timestamp) is null or t.started >= cast(cast(?4 as text) as timestamp)) and " +
            "(cast(cast(?5 as text) as timestamp) is null or t.started <= cast(cast(?5 as text) as timestamp)) and " +
            "(cast(cast(?6 as text) as timestamp) is null or t.finished >= cast(cast(?6 as text) as timestamp)) and " +
            "(cast(cast(?7 as text) as timestamp) is null or t.finished <= cast(cast(?7 as text) as timestamp)) and " +
            "(?8 is null or t.status = ?8)")
    Page<TransferTaskEntity> filter(Long registryId,
                                    LocalDateTime createdFrom,
                                    LocalDateTime createdTo,
                                    LocalDateTime startedFrom,
                                    LocalDateTime startedTo,
                                    LocalDateTime finishedFrom,
                                    LocalDateTime finishedTo,
                                    TaskStatus status,
                                    Pageable pageable);
}
