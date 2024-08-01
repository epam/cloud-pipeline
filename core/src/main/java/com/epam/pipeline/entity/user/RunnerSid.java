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

package com.epam.pipeline.entity.user;

import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@Builder
public class RunnerSid {

    private String name;
    private boolean principal = true;

    @Enumerated(EnumType.STRING)
    private RunAccessType accessType = RunAccessType.ENDPOINT;
    private Boolean pipelinesAllowed;
    private Boolean toolsAllowed;
    @JsonIgnore
    private String pipelinesList;
    @JsonIgnore
    private String toolsList;

    // All args constructor added manually due to incompatibility between jackson and lombok annotations
    public RunnerSid(final String name,
                     final boolean principal,
                     final RunAccessType accessType,
                     final Boolean pipelinesAllowed,
                     final Boolean toolsAllowed,
                     final String pipelinesList,
                     final String toolsList) {
        this.name = name;
        this.principal = principal;
        this.accessType = accessType;
        this.pipelinesAllowed = pipelinesAllowed;
        this.toolsAllowed = toolsAllowed;
        this.pipelinesList = pipelinesList;
        this.toolsList = toolsList;
    }

    public List<Long> getPipelines() {
        return parse(pipelinesList);
    }

    public List<Long> getTools() {
        return parse(toolsList);
    }

    private List<Long> parse(String pipelinesList) {
        if (StringUtils.isBlank(pipelinesList)) {
            return Collections.emptyList();
        }
        return Arrays.stream(pipelinesList.split(","))
                .map(item -> StringUtils.defaultString(item, "").trim())
                .filter(NumberUtils::isDigits)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
