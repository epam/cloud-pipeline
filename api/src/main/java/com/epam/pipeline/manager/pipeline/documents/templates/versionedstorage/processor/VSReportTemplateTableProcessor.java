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

package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor;

import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor.extractor.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class VSReportTemplateTableProcessor implements VSReportTemplateProcessor {

    public static final String EMPTY = "";

    final ReportDataExtractor<Table> dataProducer;

    public void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                                           final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        if (paragraph == null) {
            return;
        }
        final String replaceRegex = "(?i)\\{" + template + "}";
        final Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(paragraph.getText().replace("\t", ""));
        if (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            boolean dataInserted = false;
            boolean shouldMoveRun = false;
            int globalStartIndex = 0;
            int runToRemoveIndex = 0;
            XWPFParagraph currentParagraph = null;
            final List<XWPFRun> runs = paragraph.getRuns();
            final XmlCursor xmlCursor = paragraph.getCTP().newCursor();
            xmlCursor.toNextSibling();
            outer: for (XWPFRun run : runs) {
                for (int pos = 0; pos < run.getCTR().sizeOfTArray(); pos++) {
                    if (!shouldMoveRun) {
                        runToRemoveIndex++;
                    }
                    String runText = run.getText(pos);
                    if (runText == null) {
                        continue;
                    }
                    int globalEndIndex = globalStartIndex + runText.length();
                    if (globalStartIndex > end || globalEndIndex < start) {
                        globalStartIndex = globalEndIndex;
                        if (shouldMoveRun && currentParagraph != null) {
                            XWPFRun newRun = currentParagraph.createRun();
                            this.copyRunProperties(run, newRun, true);
                        }
                        continue;
                    }
                    final int replaceFrom = Math.max(globalStartIndex, start) - globalStartIndex;
                    final int replaceTo = Math.min(globalEndIndex, end) - globalStartIndex;
                    // Since it is possible that placeholder text can be split on several runs inside a paragraph
                    // we need to replace part of placeholder with data only once, so lets replace it as soon
                    // as we on appropriate position and save state of in in dataInserted
                    if (replaceTo - replaceFrom > 0 && !dataInserted) {
                        currentParagraph = replacePlaceholderAndSplitRun(
                                paragraph, dataProducer.extract(paragraph, storage, diff, reportFilter),
                                xmlCursor, run, pos, runText, replaceFrom, replaceTo
                        );
                        if (currentParagraph == null) {
                            break outer;
                        }
                        shouldMoveRun = true;
                        dataInserted = true;
                    } else {
                        runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                        run.setText(runText, pos);
                        if (shouldMoveRun && currentParagraph != null) {
                            XWPFRun newRun = currentParagraph.createRun();
                            this.copyRunProperties(run, newRun, true);
                        }
                    }
                    globalStartIndex = globalEndIndex;
                }
            }

            while (paragraph.getRuns().size() > runToRemoveIndex) {
                paragraph.removeRun(runToRemoveIndex);
            }
        }
    }

    private XWPFParagraph replacePlaceholderAndSplitRun(final XWPFParagraph paragraph,
                                                        final Table data,
                                                        final XmlCursor xmlCursor,
                                                        final XWPFRun run,
                                                        final int pos,
                                                        final String runText,
                                                        final int replaceFrom,
                                                        final int replaceTo) {
        final String beforePlaceholderText = runText.substring(0, replaceFrom);
        run.setText(beforePlaceholderText, pos);

        this.insertData(paragraph, run, xmlCursor, data);
        if (!xmlCursor.isStart()) {
            return null;
        }

        final XWPFParagraph newParagraph = paragraph.getDocument().insertNewParagraph(xmlCursor);
        this.copyParagraphProperties(paragraph, newParagraph);

        final String afterPlaceholderText = runText.substring(replaceTo);
        XWPFRun newRun = newParagraph.createRun();
        this.copyRunProperties(run, newRun);
        newRun.setText(afterPlaceholderText, pos);
        return newParagraph;
    }

    private void insertData(final XWPFParagraph splittedParagraph, final XWPFRun runTemplate,
                    final XmlCursor cursor, final Table table) {
        if (table.getRows().isEmpty()) {
            return;
        }
        final XWPFTable xwpfTable = splittedParagraph.getDocument().insertNewTbl(cursor);
        xwpfTable.removeRow(0);
        CTTblPr properties = xwpfTable.getCTTbl().getTblPr();
        if (properties == null) {
            properties = xwpfTable.getCTTbl().addNewTblPr();
        }
        final CTJc jc = (properties.isSetJc() ? properties.getJc() : properties.addNewJc());
        jc.setVal(STJc.CENTER);

        final CTTblBorders borders = properties.addNewTblBorders();
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewTop().setVal(STBorder.SINGLE);

        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);

        if (table.isContainsHeaderRow()) {
            final XWPFTableRow headerRow = xwpfTable.insertNewTableRow(0);
            IntStream.range(
                    0, table.getColumns().size()
            ).forEach(colIndex ->
                    setCellData(runTemplate, headerRow, true, () -> table.getColumns().get(colIndex).getName())
            );
        }
        IntStream.range(0, table.getRows().size()).forEach(rowIndex -> {
            final XWPFTableRow row = xwpfTable.insertNewTableRow(rowIndex + 1);
            IntStream.range(
                    0, table.getColumns().size()
            ).forEach(colIndex ->
                    setCellData(
                        runTemplate, row, false,
                        () -> Optional.ofNullable(table.getData(rowIndex, colIndex))
                                .map(Object::toString)
                                .orElse(EMPTY)
                    )
            );
        });
        cursor.toNextSibling();
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

    private void copyParagraphProperties(final XWPFParagraph original, final XWPFParagraph copy) {
        final CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

    private void copyRunProperties(final XWPFRun original, final XWPFRun copy) {
        this.copyRunProperties(original, copy, false);
    }

    private void copyRunProperties(final XWPFRun original, final XWPFRun copy, final boolean copyText) {
        final CTRPr rPr = copy.getCTR().isSetRPr() ? copy.getCTR().getRPr() : copy.getCTR().addNewRPr();
        rPr.set(original.getCTR().getRPr());
        if (copyText) {
            copy.setText(original.getText(0));
        }
    }

}
