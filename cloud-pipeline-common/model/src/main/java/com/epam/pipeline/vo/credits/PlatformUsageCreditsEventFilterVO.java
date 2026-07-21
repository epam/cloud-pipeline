/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vo.credits;

import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.vo.SecuredEntityVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUsageCreditsEventFilterVO {
    private List<SecuredEntityVO> entities;
    private Long ruleId;
    private List<Long> userIds;
    private List<PlatformUsageCreditsUpdateAction.ActionType> incidentTypes;
    private int page;
    private int pageSize;
}
