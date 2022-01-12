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

package com.epam.pipeline.entity.datastorage.nfs;

import com.epam.pipeline.entity.datastorage.StorageQuotaAction;
import com.epam.pipeline.entity.datastorage.StorageQuotaType;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Data
public class NFSQuotaNotificationEntry {

    public static final NFSQuotaNotificationEntry NO_ACTIVE_QUOTAS_NOTIFICATION =
        new NFSQuotaNotificationEntry(0.0, StorageQuotaType.GIGABYTES, Collections.singleton(StorageQuotaAction.EMAIL));

    private final Double value;
    private final StorageQuotaType type;
    private final Set<StorageQuotaAction> actions;

    public String toThreshold() {
        return String.format("%.0f %s", value, type.getThresholdLabel());
    }
}
