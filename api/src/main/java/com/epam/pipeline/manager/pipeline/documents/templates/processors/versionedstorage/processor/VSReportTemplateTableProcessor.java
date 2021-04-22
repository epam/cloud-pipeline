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
                    xwpfTableCell.removeParagraph(0);
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
        }
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
