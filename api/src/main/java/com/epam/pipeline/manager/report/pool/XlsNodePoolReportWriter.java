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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class XlsNodePoolReportWriter extends AbstractNodePoolReportWriter {
    private final String templatePath;
    private final MessageHelper messageHelper;

    public XlsNodePoolReportWriter(@Value("${reports.pool.export.xls.template}") final String templatePath,
                                   final NodePoolManager nodePoolManager,
                                   final MessageHelper messageHelper,
                                   final PreferenceManager preferenceManager) {
        super(preferenceManager, nodePoolManager);
        this.templatePath = templatePath;
        this.messageHelper = messageHelper;
    }

    @Override
    public NodePoolReportType getType() {
        return NodePoolReportType.XLS;
    }

    @Override
    public InputStream writeToStream(final List<NodePoolUsageReport> report, final Long targetPool,
                                     final ChronoUnit interval) {
        if (StringUtils.isBlank(templatePath)) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_STATS_EMPTY_XLS_TEMPLATE_PATH));
        }

        final NodePoolReportHeaderHelper headerHelper = getHeaderHelper();

        try (Workbook workbook = getTemplateWorkbook()) {
            fillPoolData(workbook, report, targetPool, interval, headerHelper);
            fillUtilizationData(workbook, report, interval, headerHelper);

            return writeWorkbookToStream(workbook);
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_STATS_FILE_XLS_CONVERSION), e);
        }
    }

    private InputStream writeWorkbookToStream(final Workbook workbook) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_STATS_FILE_XLS_CONVERSION), e);
        }
    }

    private Workbook getTemplateWorkbook() throws IOException, InvalidFormatException {
        if (templatePath.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            try (InputStream classPathResource = getClass()
                    .getResourceAsStream(templatePath.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()))) {
                return WorkbookFactory.create(classPathResource);
            }
        }
        return WorkbookFactory.create(ResourceUtils.getFile(templatePath), null, true);
    }

    private void fillPoolData(final Workbook workbook, final List<NodePoolUsageReport> report,
                              final Long targetPoolId, final ChronoUnit interval,
                              final NodePoolReportHeaderHelper headerHelper) {
        final Sheet dataSheet = workbook.getSheet(preferenceManager.getPreference(
                SystemPreferences.POOL_REPORT_DATA_SHEET_NAME));

        final List<String[]> table = new NodePoolReportTableHelper(headerHelper)
                .buildPoolDataTable(report, interval, targetPoolId);

        fillTableWithValues(dataSheet, table);
    }

    private void fillUtilizationData(final Workbook workbook, final List<NodePoolUsageReport> report,
                                     final ChronoUnit interval, final NodePoolReportHeaderHelper headerHelper) {
        final Sheet dataSheet = workbook.getSheet(preferenceManager.getPreference(
                SystemPreferences.POOL_REPORT_UTILIZATION_SHEET_NAME));
        final List<String[]> table = new NodePoolReportTableHelper(headerHelper)
                .buildUtilizationTable(report, interval);
        final String[] header = table.get(0);
        fillTableWithValues(dataSheet, table);
        hideRedundantCells(dataSheet, table, header);
    }

    private void hideRedundantCells(final Sheet dataSheet, final List<String[]> table, final String[] header) {
        final Integer maxRow = preferenceManager.getPreference(SystemPreferences.POOL_REPORT_UTILIZATION_TABLE_MAX_ROW);
        for (int i = table.size(); i < maxRow; i++) {
            final Row row = dataSheet.createRow(i);
            row.setZeroHeight(true);
        }

        final Integer maxColumn = preferenceManager.getPreference(
                SystemPreferences.POOL_REPORT_UTILIZATION_TABLE_MAX_COLUMN);
        for (int i = header.length; i < maxColumn; i++) {
            dataSheet.setColumnHidden(i, true);
        }
    }

    private void fillTableWithValues(final Sheet dataSheet, final List<String[]> table) {
        for (int i = 0; i < table.size(); i++) {
            final Row row = dataSheet.createRow(i);
            final String[] data = table.get(i);
            for (int j = 0; j < data.length; j++) {
                final Cell cell = row.createCell(j);
                if (i != 0 && j != 0) { // not header and not timestamp
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellValue(StringUtils.isNotEmpty(data[j]) ? Integer.parseInt(data[j]) : 0);
                } else {
                    cell.setCellValue(data[j]);
                }
            }
        }
    }
}
