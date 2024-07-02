/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.quota.AppliedQuotaEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;


@SuppressWarnings({"PMD.MethodNamingConventions"})
public interface AppliedQuotaRepository extends CrudRepository<AppliedQuotaEntity, Long>,
        JpaSpecificationExecutor<AppliedQuotaEntity> {
    void deleteAllByAction_Quota_Id(Long quotaId);
    void deleteAllByAction_Id(Long actionId);
    void deleteByToBefore(LocalDate to);
}
