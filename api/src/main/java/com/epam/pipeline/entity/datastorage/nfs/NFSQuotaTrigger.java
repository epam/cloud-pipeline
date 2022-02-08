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

import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@Builder(toBuilder = true)
public class NFSQuotaTrigger {

    private final Long storageId;
    private final NFSQuotaNotificationEntry quota;
    private final List<NFSQuotaNotificationRecipient> recipients;
    private final LocalDateTime executionTime;
    private final NFSStorageMountStatus targetStatus;
    private final LocalDateTime targetStatusActivationTime;
    private final boolean notificationRequired;
}
