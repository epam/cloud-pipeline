/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.sync.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thymeleaf.util.StringUtils;

import java.util.List;


@Data
@EqualsAndHashCode(exclude = {"transferTriggers", "parentRule"})
@AllArgsConstructor
public class AutonomousSyncRule {

    private String source;
    private String destination;
    private String cron;
    private Boolean deleteSource;
    private List<TransferTrigger> transferTriggers;
    private Boolean checkSyncToken;
    private Boolean checkIllumina;
    private AutonomousSyncRule parentRule;

    public AutonomousSyncRule(final String source,
                              final String destination,
                              final String cron,
                              final Boolean deleteSource,
                              final List<TransferTrigger> transferTriggers,
                              final Boolean checkSyncToken) {
        this.source = source;
        this.destination = destination;
        this.cron = cron;
        this.deleteSource = deleteSource;
        this.checkSyncToken = checkSyncToken;
        this.transferTriggers = transferTriggers;
    }

    public boolean isSameSyncPaths(final AutonomousSyncRule anotherRule) {
        return StringUtils.equals(source, anotherRule.getSource())
               && StringUtils.equals(destination, anotherRule.getDestination());
    }
}
