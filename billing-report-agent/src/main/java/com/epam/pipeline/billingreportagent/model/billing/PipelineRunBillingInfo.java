/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.model.billing;

import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineRunBillingInfo extends AbstractBillingInfo<PipelineRunWithType> {

    private Long usageMinutes;
    private Long pausedMinutes;

    @Builder
    public PipelineRunBillingInfo(final LocalDate date, final PipelineRunWithType run,
                                  final Long cost, final Long usageMinutes, final Long pausedMinutes) {
        super(date, run, cost, ResourceType.COMPUTE);
        this.usageMinutes = usageMinutes;
        this.pausedMinutes = pausedMinutes;
    }
}
