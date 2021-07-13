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

    private final ReportDataExtractor<Table> dataProducer;

    public void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                                           final GitParsedDiff diff, final GitDiffReportFilter reportFilter,
                                           final List<String> customBinaryExtension) {
        if (paragraph == null) {
            return;
        }
        final String replaceRegex = "(?i)\\{" + template + "}";
        final Pattern pattern = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(paragraph.getText().replace("\t", ""));
        if (matcher.find()) {
            Table data = dataProducer.extract(paragraph, storage, diff, reportFilter);
            cleanUpParagraph(paragraph);
            replacePlaceholderWithTable(
                paragraph, data,
                paragraph.getCTP().newCursor(), paragraph.getRuns().get(0)
            );
        }
    }

    private void replacePlaceholderWithTable(final XWPFParagraph paragraph,
                                             final Table data,
                                             final XmlCursor xmlCursor,
                                             final XWPFRun run) {
        insertData(paragraph, run, xmlCursor, data);
        xmlCursor.toNextToken();

        final XWPFParagraph newParagraph = paragraph.getDocument().insertNewParagraph(xmlCursor);
        copyParagraphProperties(paragraph, newParagraph);

        XWPFRun newRun = newParagraph.createRun();
        copyRunProperties(run, newRun);
    }

    private void insertData(final XWPFParagraph splittedParagraph, final XWPFRun runTemplate,
                    final XmlCursor cursor, final Table table) {
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
        copyRunProperties(original, copy, false);
    }

    private void copyRunProperties(final XWPFRun original, final XWPFRun copy, final boolean copyText) {
        final CTRPr rPr = copy.getCTR().isSetRPr() ? copy.getCTR().getRPr() : copy.getCTR().addNewRPr();
        rPr.set(original.getCTR().getRPr());
        if (copyText) {
            copy.setText(original.getText(0));
        }
    }

}
