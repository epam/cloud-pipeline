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

package com.epam.pipeline.test.creator.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.QuotaGroup;
import com.epam.pipeline.dto.quota.QuotaType;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.user.PipelineUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface QuotaCreatorsUtils {
    String NAME = "user";
    Long VALUE = 100L;
    Integer THRESHOLD = 80;
    Long ID = 1L;

    static Quota quota(final List<Long> userIds) {
        return Quota.builder()
                .id(ID)
                .name(NAME)
                .type(QuotaType.USER)
                .quotaGroup(QuotaGroup.STORAGE)
                .value(VALUE)
                .informedUsers(userIds)
                .build();
    }

    static QuotaAction quotaAction() {
        return QuotaAction.builder()
                .id(ID)
                .threshold(THRESHOLD)
                .actions(Collections.singletonList(QuotaActionType.NOTIFY))
                .build();
    }

    static QuotaEntity quotaEntity(final List<PipelineUser> users) {
        final QuotaEntity quotaEntity = new QuotaEntity();
        quotaEntity.setId(ID);
        quotaEntity.setName(NAME);
        quotaEntity.setType(QuotaType.USER);
        quotaEntity.setQuotaGroup(QuotaGroup.STORAGE);
        quotaEntity.setValue(VALUE);
        quotaEntity.setInformedUsers(users);
        return quotaEntity;
    }

    static QuotaActionEntity quotaActionEntity(final QuotaEntity quotaEntity) {
        final QuotaActionEntity quotaActionEntity = new QuotaActionEntity();
        quotaActionEntity.setId(ID);
        quotaActionEntity.setQuota(quotaEntity);
        quotaActionEntity.setThreshold(THRESHOLD);
        final List<QuotaActionType> actions = new ArrayList<>();
        actions.add(QuotaActionType.NOTIFY);
        quotaActionEntity.setActions(actions);
        return quotaActionEntity;
    }
}
