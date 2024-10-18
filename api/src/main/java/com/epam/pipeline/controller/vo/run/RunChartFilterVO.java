/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.vo.run;

import com.epam.pipeline.entity.filter.AclSecuredFilter;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class RunChartFilterVO implements AclSecuredFilter {
    private List<String> owners;
    private List<String> dockerImages;
    private List<String> instanceTypes;
    private List<String> tags;
    private List<TaskStatus> statuses;

    //these filter is used for ACL filtering
    @JsonIgnore
    private List<Long> allowedPipelines;
    @JsonIgnore
    private String ownershipFilter;
}
