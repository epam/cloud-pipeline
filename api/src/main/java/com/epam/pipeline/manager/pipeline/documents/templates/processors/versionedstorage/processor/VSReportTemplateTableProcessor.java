/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class VSReportTemplateTableProcessor extends AbstractVSReportTemplateProcessor {

    public VSReportTemplateTableProcessor(ReportDataExtractor dataProducer) {
        super(dataProducer);
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
        if (this.xwpfRun == null || paragraph == null) {
            return;
        }
        int globalStartIndex = 0;
        boolean shouldMoveRun = false;
        int runToRemoveIndex = 0;
        XWPFParagraph currentParagraph = null;
        final List<XWPFRun> runs = paragraph.getRuns();
        XmlCursor xmlCursor = paragraph.getCTP().newCursor();
        xmlCursor.toNextSibling();
        for (XWPFRun run : runs) {
            if (!shouldMoveRun) {
                runToRemoveIndex++;
            }
            String runText = run.getText(0);
            if (runText == null) {
                continue;
            }
            int globalEndIndex = globalStartIndex + runText.length();
            if (globalStartIndex > this.end || globalEndIndex < this.start) {
                globalStartIndex = globalEndIndex;
                if (shouldMoveRun && currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun, true);
                }
                continue;
            }
            int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
            int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
            if (this.xwpfRun.equals(run)) {
                String beforePlaceholderText = runText.substring(0, replaceFrom);
                run.setText(beforePlaceholderText, 0);

                this.insertData(paragraph, run, xmlCursor, data);

                if (!xmlCursor.isStart()) {
                    break;
                }

                currentParagraph = paragraph.getDocument().insertNewParagraph(xmlCursor);
                this.copyParagraphProperties(paragraph, currentParagraph);

                String afterPlaceholderText = runText.substring(replaceTo);
                shouldMoveRun = true;
                if (currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun);
                    newRun.setText(afterPlaceholderText, 0);
                }
            } else {
                runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                run.setText(runText, 0);
                if (shouldMoveRun && currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun, true);
                }
            }
            globalStartIndex = globalEndIndex;
        }

        while (paragraph.getRuns().size() > runToRemoveIndex) {
            paragraph.removeRun(runToRemoveIndex);
        }
    }

    void insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data instanceof Table) {
            Table table = (Table)data;
            XWPFTable xwpfTable = splittedParagraph.getDocument().insertNewTbl(cursor);
            xwpfTable.removeRow(0);
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

            if (table.isContainsHeaderRow()) {
                final XWPFTableRow headerRow = xwpfTable.insertNewTableRow(0);
                IntStream.range(
                        0, table.getColumns().size() - 1
                ).forEach(colIndex ->
                        setCellData(runTemplate, headerRow, true, () -> table.getColumns().get(colIndex).getName())
                );
            }
            IntStream.range(1, table.getRows().size() - 1).forEach(rowIndex -> {
                final XWPFTableRow row = xwpfTable.insertNewTableRow(rowIndex);
                IntStream.range(
                        0, table.getColumns().size() - 1
                ).forEach(colIndex ->
                        setCellData(
                            runTemplate, row, false,
                            () -> {
                                final Object cellData = table.getData(rowIndex, colIndex);
                                return cellData != null ? cellData.toString() : "";
                            }
                        )
                );
            });
            cursor.toNextSibling();
        }
    }

    private void setCellData(final XWPFRun runTemplate, final XWPFTableRow headerRow,
                             final boolean bold, final Supplier<String> data) {
        final XWPFTableCell xwpfTableCell = headerRow.addNewTableCell();
        xwpfTableCell.removeParagraph(0);
        final XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
        xwpfParagraph.setAlignment(ParagraphAlignment.CENTER);
        xwpfParagraph.setVerticalAlignment(TextAlignment.CENTER);
        final XWPFRun xwpfRun = xwpfParagraph.createRun();
        copyRunProperties(runTemplate, xwpfRun);
        xwpfRun.setBold(bold);
        xwpfRun.setText(data.get(), 0);
    }

    void copyParagraphProperties(XWPFParagraph original, XWPFParagraph copy) {
        CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

    void copyRunProperties(XWPFRun original, XWPFRun copy) {
        this.copyRunProperties(original, copy, false);
    }

    void copyRunProperties(XWPFRun original, XWPFRun copy, boolean copyText) {
        CTRPr rPr = copy.getCTR().isSetRPr() ? copy.getCTR().getRPr() : copy.getCTR().addNewRPr();
        rPr.set(original.getCTR().getRPr());
        if (copyText) {
            copy.setText(original.getText(0));
        }
    }

}
