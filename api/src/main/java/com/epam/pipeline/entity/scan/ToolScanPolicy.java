/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.scan;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Describes policy to determinate possibilities to run a {@link com.epam.pipeline.entity.pipeline.Tool}
 * */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ToolScanPolicy {

    /**
     * If true only scanned {@link com.epam.pipeline.entity.pipeline.Tool} can be run
     * */
    private boolean denyNotScanned;

    /**
     * Determinate how much critical vulnerabilities tool can have to be lunched
     * */
    private int maxCriticalVulnerabilities;

    /**
     * Determinate how much high vulnerabilities tool can have to be lunched
     * */
    private int maxHighVulnerabilities;

    /**
     * Determinate how much medium vulnerabilities tool can have to be lunched
     * */
    private int maxMediumVulnerabilities;

}
