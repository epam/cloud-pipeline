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

package com.epam.pipeline.manager.cluster.writer;

import com.amazonaws.util.StringInputStream;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvMonitoringStatsWriter extends AbstractMonitoringStatsWriter{

    private final MessageHelper messageHelper;

    @Override
    public MonitoringReportType getReportType() {
        return MonitoringReportType.CSV;
    }

    @Override
    public InputStream convertStatsToFile(final List<MonitoringStats> stats) {
        try {
            return new StringInputStream(convertStatsToCsvString(stats));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(messageHelper.getMessage(MessageConstants.ERROR_BAD_STATS_FILE_ENCODING),
                                            e);
        }
    }

    public String convertStatsToCsvString(final List<MonitoringStats> stats) {
        if (CollectionUtils.isEmpty(stats)) {
            return StringUtils.EMPTY;
        }
        final StringWriter stringWriter = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(stringWriter);
        final List<String[]> allLines = extractTable(stats);
        csvWriter.writeAll(allLines);
        return stringWriter.toString();
    }
}
