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

package com.epam.pipeline.vmmonitor.model.filesystem;

import com.epam.pipeline.config.Constants;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

@Getter
public class FileSystemUsageSummary {

    private final String path;
    private final String usedSpace;
    private final String totalSpace;
    private final Double consumptionPercentage;
    private final Double thresholdPercentage;

    public FileSystemUsageSummary(final String path, final Long usedSpaceBytes, final Long totalSpaceBytes,
                                  final Double threshold) {
        this.path = path;
        this.usedSpace = FileUtils.byteCountToDisplaySize(usedSpaceBytes);
        this.totalSpace = FileUtils.byteCountToDisplaySize(totalSpaceBytes);
        this.consumptionPercentage = Constants.HUNDRED_PERCENTS * usedSpaceBytes / totalSpaceBytes;
        this.thresholdPercentage = threshold;
    }

    public boolean exceedsThreshold() {
        return consumptionPercentage > thresholdPercentage;
    }
}
