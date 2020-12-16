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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("checkstyle:MagicNumber")
public class XlsMonitoringStatsWriter extends AbstractMonitoringStatsWriter {

    private static final String SCALED_DATA_SHEET = "SCALED_DATA";
    private static final String DISK_DATA_SHEET = "DISKS_SUMMARY";
    private static final String RAW_DATA_SHEET = "DATA";
    private static final String CPU_CONVERSION_FORMULA = "DATA!%c%d/100";
    private static final String MEM_CONVERSION_FORMULA = "DATA!E%d*DATA!%c%d/100/1073741824";
    private static final String NET_CONVERSION_FORMULA = "DATA!%s/1048576";
    private static final String DISK_USED_FORMULA = "DATA!%s/1073741824*DATA!%s/100";
    private static final String DISK_FREE_FORMULA = "DATA!%s/1073741824*(100-DATA!%s)/100";
    private static final Character CPU_AVG_COLUMN = 'C';
    private static final Character CPU_MAX_COLUMN = 'D';
    private static final Character MEM_AVG_COLUMN = 'F';
    private static final Character MEM_MAX_COLUMN = 'G';
    private static final long BYTES_IN_GB = 1L << 30;
    private static final String DISK_NAME_TEMPLATE = "%s[%.2fGb]";
    private static final String NUMERIC_CELL_PRECISION_FORMAT = "0.00";

    private final String templatePath;
    private final MessageHelper messageHelper;

    public XlsMonitoringStatsWriter(final @Value("${monitoring.stats.export.xls.template}") String templatePath,
                                    final MessageHelper messageHelper) {
        this.templatePath = templatePath;
        this.messageHelper = messageHelper;
    }

    @Override
    public synchronized InputStream convertStatsToFile(final List<MonitoringStats> stats) {
        try (Workbook wb = WorkbookFactory.create(new File(templatePath), null, true);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            fillInRawData(wb, stats);
            fillInScaledData(wb, stats);
            fillInDiskStats(wb, stats);
            fillInEmptyCells(wb, stats);
            HSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            wb.write(bos);
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalStateException(messageHelper.getMessage(MessageConstants.ERROR_STATS_FILE_XLS_CONVERSION));
        }
    }

    private void fillInScaledData(final Workbook wb, final List<MonitoringStats> stats) {
        final int disksCount = (int) getDiskNamesStream(stats).count();
        final int rxColPos = COMMON_STATS_HEADER.size() + disksCount * 2;
        final int txColPos = rxColPos + 1;
        final String rxColChar = CellReference.convertNumToColString(rxColPos);
        final String txColChar = CellReference.convertNumToColString(txColPos);
        final Sheet scaledDataSheet = wb.getSheet(SCALED_DATA_SHEET);
        for (int i = 1; i <= stats.size(); i++) {
            final Row scaledDataRow = scaledDataSheet.createRow(i);
            scaledDataRow.createCell(0).setCellFormula(getCpuConversionFormula(CPU_AVG_COLUMN, i + 1));
            scaledDataRow.createCell(1).setCellFormula(getCpuConversionFormula(CPU_MAX_COLUMN, i + 1));
            scaledDataRow.createCell(2).setCellFormula(getMemConversionFormula(MEM_AVG_COLUMN, i + 1));
            scaledDataRow.createCell(3).setCellFormula(getMemConversionFormula(MEM_MAX_COLUMN, i + 1));
            scaledDataRow.createCell(4).setCellFormula(getInterfaceConversionFormula(rxColChar, i + 1));
            scaledDataRow.createCell(5).setCellFormula(getInterfaceConversionFormula(txColChar, i + 1));
        }
    }

    private void fillInRawData(final Workbook wb, final List<MonitoringStats> stats) {
        final List<String[]> statsTable = extractTable(stats);
        final Sheet rawDataSheet = wb.getSheet(RAW_DATA_SHEET);
        for (int i = 0; i < statsTable.size(); i++) {
            final Row row = rawDataSheet.createRow(i);
            final String[] statsRow = statsTable.get(i);
            for (int j = 0; j < statsRow.length; j++) {
                final Cell cell = row.createCell(j);
                if (i != 0 && j != 0) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellValue(StringUtils.isNotEmpty(statsRow[j])
                                      ? Double.parseDouble(statsRow[j])
                                      : Double.NaN);
                } else {
                    cell.setCellValue(statsRow[j]);
                }
            }
        }
    }

    private void fillInDiskStats(final Workbook wb, final List<MonitoringStats> stats) {
        final Sheet rawDataSheet = wb.getSheet(RAW_DATA_SHEET);
        final Sheet diskSheet = wb.getSheet(DISK_DATA_SHEET);
        final List<String> diskNames = getSortedDiskNamesStream(stats).collect(Collectors.toList());
        final CellStyle doublePrecisionStyle = wb.createCellStyle();
        doublePrecisionStyle.setDataFormat(wb.createDataFormat().getFormat(NUMERIC_CELL_PRECISION_FORMAT));
        for (int i = 0; i < diskNames.size(); i++) {
            for (int j = stats.size(); j > 0; j--) {
                final Row row = rawDataSheet.getRow(j);
                final Cell totalDiskCell = row.getCell(COMMON_STATS_HEADER.size() + i * 2);
                if (!totalDiskCell.getCellTypeEnum().equals(CellType.ERROR)) {
                    final double capacityBytes = totalDiskCell.getNumericCellValue();
                    final int rowIndex = j + 1;
                    final String totalDiskCellAddress =
                        CellReference.convertNumToColString(totalDiskCell.getColumnIndex()) + rowIndex;
                    final String usedDiskCellAddress =
                        CellReference.convertNumToColString(totalDiskCell.getColumnIndex() + 1) + rowIndex;
                    final double capacityGb = capacityBytes / BYTES_IN_GB;
                    final Row diskSummaryRow = diskSheet.createRow(i + 1);
                    final Cell diskNameCell = diskSummaryRow.createCell(0);
                    diskNameCell.setCellValue(String.format(DISK_NAME_TEMPLATE, diskNames.get(i), capacityGb));
                    final Cell diskUsedCell = diskSummaryRow.createCell(1);
                    diskUsedCell
                        .setCellFormula(String.format(DISK_USED_FORMULA, totalDiskCellAddress, usedDiskCellAddress));
                    diskUsedCell.setCellStyle(doublePrecisionStyle);
                    final Cell diskFreeCell = diskSummaryRow.createCell(2);
                    diskFreeCell
                        .setCellFormula(String.format(DISK_FREE_FORMULA, totalDiskCellAddress, usedDiskCellAddress));
                    diskFreeCell.setCellStyle(doublePrecisionStyle);
                    break;
                }
            }
        }
    }

    private void fillInEmptyCells(final Workbook wb, final List<MonitoringStats> stats) {
        final Sheet rawDataSheet = wb.getSheet(RAW_DATA_SHEET);
        for (int i = 1; i <= stats.size(); i++) {
            final Row row = rawDataSheet.getRow(i);
            final short lastCellNum = row.getLastCellNum();
            for (int j = 1; j < lastCellNum; j++) {
                final Cell cell = row.getCell(j);
                if (cell.getCellTypeEnum().equals(CellType.ERROR)) {
                    cell.setCellValue(0);
                }
            }
        }
    }

    private String getInterfaceConversionFormula(final String colChar, final int i) {
        return String.format(NET_CONVERSION_FORMULA, colChar + i);
    }

    private String getMemConversionFormula(final Character memAvgColumn, final int i) {
        return String.format(MEM_CONVERSION_FORMULA, i, memAvgColumn, i);
    }

    private String getCpuConversionFormula(final Character cpuAvgColumn, final int i) {
        return String.format(CPU_CONVERSION_FORMULA, cpuAvgColumn, i);
    }
}
