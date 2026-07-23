/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.dto.credits;

import com.epam.pipeline.vo.SecuredEntityVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUsageCreditsEventFilterVO {

    private Long ruleId;
    private List<Long> userIds;
    private List<PlatformUsageCreditsUpdateAction.ActionType> incidentTypes;
    /** Can't be used together with {@code entities}. */
    private Boolean withoutEntityLink;
    /** Can't be used together with {@code withoutEntityLink}. */
    private List<SecuredEntityVO> entities;
    private LocalDateTime from;
    private LocalDateTime to;
    private int page;
    private int pageSize;
}
