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

package com.epam.pipeline.manager.pipeline.documents.templates.processors;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholder;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.ITemplateContext;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TableTemplateProcessor extends SplitParagraphTemplateProcessor {
    public TableTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public TableTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    @Override
    boolean insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data instanceof Table) {
            Table table = (Table)data;
            XWPFTable xwpfTable = splittedParagraph.getDocument().insertNewTbl(cursor);

            CTTblPr properties = xwpfTable.getCTTbl().getTblPr();
            if (properties == null) {
                properties = xwpfTable.getCTTbl().addNewTblPr();
            }
            CTJc jc = (properties.isSetJc() ? properties.getJc() : properties.addNewJc());
            jc.setVal(STJc.CENTER);

            CTTblBorders borders = properties.addNewTblBorders();
            borders.addNewBottom().setVal(STBorder.SINGLE);
            borders.addNewLeft().setVal(STBorder.SINGLE);
            borders.addNewRight().setVal(STBorder.SINGLE);
            borders.addNewTop().setVal(STBorder.SINGLE);

            borders.addNewInsideH().setVal(STBorder.SINGLE);
            borders.addNewInsideV().setVal(STBorder.SINGLE);

            for (int rowIndex = -1; rowIndex < table.getRows().size(); rowIndex++) {
                if (rowIndex == -1 && !table.isContainsHeaderRow()) {
                    continue;
                }
                XWPFTableRow xwpfTableRow = xwpfTable.insertNewTableRow(rowIndex + 1);
                for (int colIndex = -1; colIndex < table.getColumns().size(); colIndex++) {
                    if (colIndex == -1 && !table.isContainsHeaderColumn()) {
                        continue;
                    }
                    String text = "";
                    XWPFTableCell xwpfTableCell = xwpfTableRow.addNewTableCell();
                    XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
                    xwpfParagraph.setAlignment(ParagraphAlignment.CENTER);
                    xwpfParagraph.setVerticalAlignment(TextAlignment.CENTER);
                    XWPFRun xwpfRun = xwpfParagraph.createRun();
                    this.copyRunProperties(runTemplate, xwpfRun);
                    if (rowIndex == -1 && colIndex >= 0) {
                        text = table.getColumns().get(colIndex).getName();
                        xwpfRun.setBold(true);
                    } else if (colIndex == -1 && rowIndex >= 0) {
                        text = table.getRows().get(rowIndex).getName();
                        xwpfRun.setBold(true);
                    } else if (rowIndex >= 0 && colIndex >= 0) {
                        Object cellData = table.getData(rowIndex, colIndex);
                        text = cellData != null ? cellData.toString() : "";
                    }
                    xwpfRun.setText(text, 0);
                }
            }
            cursor.toNextSibling();
            return true;
        }
        return false;
    }
}
