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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final Character CPU_AVG_COLUMN = 'C';
    private static final Character CPU_MAX_COLUMN = 'D';
    private static final Character MEM_AVG_COLUMN = 'F';
    private static final Character MEM_MAX_COLUMN = 'G';
    private static final long BYTES_IN_GB = 1L << 30;
    private static final int DISK_PRECISION = 2;
    private static final String DISK_NAME_TEMPLATE = "%s[%.2fGb]";

    private final String templatePath;
    private final MessageHelper messageHelper;

    public XlsMonitoringStatsWriter(final @Value("${monitoring.stats.export.xls.template}") String templatePath,
                                    final MessageHelper messageHelper) {
        this.templatePath = templatePath;
        this.messageHelper = messageHelper;
    }

    @Override
    public InputStream convertStatsToFile(final List<MonitoringStats> stats) {
        try (Workbook wb = WorkbookFactory.create(new File(templatePath));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            fillInRawData(wb, stats);
            fillInScaledData(wb, stats);
            fillInDiskStats(wb, stats);
            HSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            wb.write(bos);
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalStateException(messageHelper.getMessage(MessageConstants.ERROR_STATS_FILE_XLS_CONVERSION));
        }
    }

    private void fillInScaledData(final Workbook wb, final List<MonitoringStats> stats) {
        final long disksCount = stats.stream()
            .map(MonitoringStats::getDisksUsage)
            .map(MonitoringStats.DisksUsage::getStatsByDevices)
            .map(Map::keySet)
            .flatMap(Collection::stream)
            .distinct()
            .count();
        final int rxColPos = (int) (7 + disksCount * 2);
        final int txColPos = rxColPos + 1;
        final String rxColChar = CellReference.convertNumToColString(rxColPos);
        final String txColChar = CellReference.convertNumToColString(txColPos);
        final Sheet scaledDataSheet = wb.getSheet(SCALED_DATA_SHEET);
        for (int i = 0; i < stats.size(); i++) {
            final Row scaledDataRow = scaledDataSheet.createRow(i + 1);
            scaledDataRow.createCell(0).setCellFormula(getCpuConversionFormula(CPU_AVG_COLUMN, i));
            scaledDataRow.createCell(1).setCellFormula(getCpuConversionFormula(CPU_MAX_COLUMN, i));
            scaledDataRow.createCell(2).setCellFormula(getMemConversionFormula(MEM_AVG_COLUMN, i));
            scaledDataRow.createCell(3).setCellFormula(getMemConversionFormula(MEM_MAX_COLUMN, i));
            scaledDataRow.createCell(4).setCellFormula(getInterfaceConversionFormula(rxColChar, i));
            scaledDataRow.createCell(5).setCellFormula(getInterfaceConversionFormula(txColChar, i));
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
                    if (StringUtils.isNotEmpty(statsRow[j])) {
                        cell.setCellValue(Double.parseDouble(statsRow[j]));
                    }
                } else {
                    cell.setCellValue(statsRow[j]);
                }
            }
        }
    }

    private void fillInDiskStats(final Workbook wb, final List<MonitoringStats> stats) {
        final Sheet diskSheet = wb.getSheet(DISK_DATA_SHEET);
        final Map<String, MonitoringStats.DisksUsage.DiskStats> disksSummary = getLatestStatsForEachDisk(stats);
        final AtomicInteger rowIndent = new AtomicInteger(0);
        disksSummary.forEach((name, usageStats) -> {
            final double capacityGb = usageStats.getCapacity() * 1.0 / BYTES_IN_GB;
            final double usedGb = usageStats.getUsableSpace() * 1.0 / BYTES_IN_GB;
            final double freeGb = capacityGb - usedGb;

            final Row diskSummaryRow = diskSheet.createRow(1 + rowIndent.getAndAdd(1));
            final Cell diskNameCell = diskSummaryRow.createCell(0);
            diskNameCell.setCellValue(String.format(DISK_NAME_TEMPLATE, name, capacityGb));
            final Cell diskUsedCell = diskSummaryRow.createCell(1);
            diskUsedCell.setCellValue(scaleDiskValue(usedGb));
            final Cell diskFreeCell = diskSummaryRow.createCell(2);
            diskFreeCell.setCellValue(scaleDiskValue(freeGb));
        });
    }

    private Map<String, MonitoringStats.DisksUsage.DiskStats> getLatestStatsForEachDisk(
        final List<MonitoringStats> stats) {
        return stats.stream()
            .flatMap(stat -> stat.getDisksUsage()
                .getStatsByDevices()
                .entrySet()
                .stream()
                .map(e -> Pair.of(stat.getStartTime(), e)))
            .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
            .collect(Collectors.toMap(pair -> pair.getValue().getKey(),
                pair -> pair.getValue().getValue(),
                (s1, s2) -> s2));
    }

    private double scaleDiskValue(final double valueGb) {
        return BigDecimal.valueOf(valueGb).setScale(DISK_PRECISION, RoundingMode.HALF_UP).doubleValue();
    }

    private String getInterfaceConversionFormula(final String colChar, final int i) {
        return String.format(NET_CONVERSION_FORMULA, colChar + (i + 2));
    }

    private String getMemConversionFormula(final Character memAvgColumn, final int i) {
        return String.format(MEM_CONVERSION_FORMULA, i + 2, memAvgColumn, i + 2);
    }

    private String getCpuConversionFormula(final Character cpuAvgColumn, final int i) {
        return String.format(CPU_CONVERSION_FORMULA, cpuAvgColumn, i + 2);
    }
}
