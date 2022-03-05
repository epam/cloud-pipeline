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

package com.epam.pipeline.manager.report.pool;

import com.amazonaws.util.StringInputStream;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.opencsv.CSVWriter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CsvNodePoolReportWriter extends AbstractNodePoolReportWriter {
    private final MessageHelper messageHelper;

    public CsvNodePoolReportWriter(final PreferenceManager preferenceManager,
                                   final NodePoolManager nodePoolManager,
                                   final MessageHelper messageHelper) {
        super(preferenceManager, nodePoolManager);
        this.messageHelper = messageHelper;
    }

    @Override
    public NodePoolReportType getType() {
        return NodePoolReportType.CSV;
    }

    @Override
    public InputStream writeToStream(final List<NodePoolUsageReport> report, final Long targetPool,
                                     final ChronoUnit interval) {
        try {
            return new StringInputStream(convertToCsvString(report, targetPool, interval));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_BAD_STATS_FILE_ENCODING), e);
        }
    }

    private String convertToCsvString(final List<NodePoolUsageReport> report, final Long targetPool,
                                      final ChronoUnit interval) {
        if (CollectionUtils.isEmpty(report)) {
            return StringUtils.EMPTY;
        }

        final NodePoolReportHeaderHelper headerHelper = getHeaderHelper();

        try (StringWriter stringWriter = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            final List<String[]> allLines = new NodePoolReportTableHelper(headerHelper)
                    .buildDataTable(report, interval, targetPool);
            csvWriter.writeAll(allLines);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
