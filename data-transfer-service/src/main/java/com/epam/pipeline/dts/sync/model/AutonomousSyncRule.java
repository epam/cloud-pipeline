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

import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.thymeleaf.util.StringUtils;

import java.util.List;
import java.util.Objects;


@Value
public class AutonomousSyncRule {

    String source;
    String destination;
    String cron;
    Boolean deleteSource;
    List<TransferTrigger> transferTriggers;

    public boolean isSameSyncPaths(final AutonomousSyncRule anotherRule) {
        return StringUtils.equals(source, anotherRule.getSource())
               && StringUtils.equals(destination, anotherRule.getDestination());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AutonomousSyncRule that = (AutonomousSyncRule) o;
        return Objects.equals(source, that.source) &&
                Objects.equals(destination, that.destination) &&
                Objects.equals(cron, that.cron) &&
                Objects.equals(deleteSource, that.deleteSource) &&
                CollectionUtils.isEqualCollection(transferTriggers, that.transferTriggers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination, cron, deleteSource, transferTriggers);
    }
}
