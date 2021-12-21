/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.quota;

import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.QuotaEntity;
import org.springframework.data.repository.CrudRepository;

public interface QuotaRepository extends CrudRepository<QuotaEntity, Long> {

    QuotaEntity findByQuotaGroup(QuotaGroup quotaGroup);

    QuotaEntity findByQuotaGroupAndType(QuotaGroup quotaGroup, QuotaType type);

    QuotaEntity findByTypeAndSubjectAndQuotaGroup(QuotaType type, String subject, QuotaGroup quotaGroup);
}
