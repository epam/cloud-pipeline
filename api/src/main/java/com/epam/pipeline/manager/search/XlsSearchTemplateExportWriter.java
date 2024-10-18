/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.entity.search.SearchTemplateExportColumnData;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class XlsSearchTemplateExportWriter {

    public XlsSearchTemplateExportWriter() {
        // no-op
    }

    public byte[] write(final Map<String, List<SearchTemplateExportColumnData>> sheetData, final String templatePath)
            throws IOException, InvalidFormatException {
        try (Workbook wb = WorkbookFactory.create(new File(templatePath), null, true);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            sheetData.forEach((sheetName, sheetMapping) -> fillInSheet(wb, sheetMapping, sheetName));
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private Row getOrCreateRow(final Sheet sheet, final int index) {
        final Row row = sheet.getRow(index);
        return Objects.isNull(row) ? sheet.createRow(index) : row;
    }

    private void fillInSheet(final Workbook wb, final List<SearchTemplateExportColumnData> data,
                             final String sheetName) {
        final Sheet sheet = wb.getSheet(sheetName);
        data.forEach(columnData -> {
            final String[] values = columnData.getColumnValues();
            final int startRow = columnData.getRowStartIndex();
            for (int i = 0; i < values.length; i++) {
                final int rowIndex = i + startRow;
                final Row row = getOrCreateRow(sheet, rowIndex);
                final int columnIndex = CellReference.convertColStringToIndex(columnData.getColumnStringIndex());
                final Cell cell = row.createCell(columnIndex);
                cell.setCellValue(values[i]);
            }
        });
    }
}
