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

import com.epam.pipeline.entity.git.GitDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGrouping;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.GitDiffGroupType;
import io.reflectoring.diffparser.api.model.Hunk;
import io.reflectoring.diffparser.api.model.Line;
import io.reflectoring.diffparser.api.model.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VSReportTemplateDiffProcessor extends AbstractVSReportTemplateProcessor {

    private static final String COLOR_WHITE = "ffffff";
    private static final String COLOR_GREEN = "9ef08e";
    private static final String COLOR_RED = "f08e8e";
    private static final String EMPTY = "";
    private static final String ADDITION_SIGN = "+";
    private static final String DELETION_SIGN = "-";
    public static final int SMALL_COLUMN_SIZE = 512;
    public static final int CONTENT_COLUMN_SIZE = 8960;

    private XWPFParagraph paragraph;

    public VSReportTemplateDiffProcessor(final ReportDataExtractor dataProducer) {
        super(dataProducer);
    }

    @Override
    boolean find(final XWPFParagraph paragraph, final String template) {
        if (paragraph == null || StringUtils.isBlank(paragraph.getText())) {
            return false;
        }
        if (paragraph.getText().contains(template)) {
            this.paragraph = paragraph;
            return true;
        }
        return false;
    }

    void replacePlaceholderWithData(final XWPFParagraph paragraph, final Object data) {
        if (this.paragraph == null || this.paragraph != paragraph) {
            return;
        }
        while (paragraph.getRuns().size() != 1) {
            paragraph.removeRun(0);
        }
        for (int pos = 0; pos < paragraph.getRuns().get(0).getCTR().sizeOfTArray(); pos++) {
            paragraph.getRuns().get(0).setText("", pos);
        }
        insertData(paragraph, paragraph.getRuns().get(0), data);
    }

    void insertData(final XWPFParagraph paragraph, final XWPFRun runTemplate, final Object data) {
        if (data instanceof GitDiffGrouping) {
            final GitDiffGrouping diffsGrouping = (GitDiffGrouping)data;
            if (!diffsGrouping.isIncludeDiff() || diffsGrouping.isArchive()) {
                return;
            }

            final String fontFamily = runTemplate.getFontFamily();
            final int fontSize = runTemplate.getFontSize();
            XWPFParagraph lastP = paragraph;
            lastP.setPageBreak(true);
            for (Map.Entry<String, List<GitDiffEntry>> entry : diffsGrouping.getDiffGrouping().entrySet()) {
                final String diffGroupKey = entry.getKey();
                final List<GitDiffEntry> diffEntries = entry.getValue().stream()
                        .sorted(Comparator.comparing(d -> d.getCommit().getAuthorDate()))
                        .collect(Collectors.toList());

                addHeader(lastP, fontFamily, fontSize, diffsGrouping.getType(), diffGroupKey, diffEntries);
                for (GitDiffEntry diffEntry : diffEntries) {
                    lastP.setPageBreak(true);
                    lastP = addDescription(lastP, fontFamily, fontSize, diffsGrouping.getType(), diffEntry);
                }
            }
        }
    }

    private void addHeader(final XWPFParagraph paragraph, final String fontFamily, final int fontSize,
                           final GitDiffGroupType type, final String groupingKey,
                           final List<GitDiffEntry> diffEntries) {

        if (type.equals(GitDiffGroupType.BY_COMMIT)) {
            insertTextData(paragraph, "In revision ", false, fontFamily, fontSize + 4, false);
            insertTextData(paragraph, groupingKey.substring(0, 9), true, fontFamily, fontSize + 4, false);
            insertTextData(paragraph, " by ", false, fontFamily, fontSize + 4, false);

            insertTextData(paragraph,
                    diffEntries.stream().findFirst()
                        .map(GitDiffEntry::getCommit)
                        .map(GitReaderRepositoryCommit::getAuthor).orElse(""),
                    true, fontFamily, fontSize + 4,
                    false);

            insertTextData(paragraph, " at ", false, fontFamily, fontSize + 4, false);

            insertTextData(paragraph,
                    diffEntries.stream().findFirst()
                        .map(GitDiffEntry::getCommit)
                        .map(c -> ReportDataExtractor.DATE_FORMAT.format(c.getAuthorDate())).orElse(""),
                    false, fontFamily, fontSize + 4, false
            );
        } else {
            insertTextData(paragraph, "Changes of file ", false, fontFamily, fontSize + 4, false);
            insertTextData(paragraph, groupingKey, true, fontFamily, fontSize + 4, false);
            insertTextData(paragraph, ":", false, fontFamily, fontSize + 4, false);
        }
        paragraph.createRun().addBreak(BreakType.TEXT_WRAPPING);
    }

    private XWPFParagraph addDescription(final XWPFParagraph paragraph, final String fontFamily, final int fontSize,
                                         final GitDiffGroupType type, final GitDiffEntry diffEntry) {
        if (type.equals(GitDiffGroupType.BY_COMMIT)) {
            final String file = diffEntry.getDiff().getFromFileName().contains("/dev/null")
                    ? diffEntry.getDiff().getToFileName()
                    : diffEntry.getDiff().getFromFileName();
            insertTextData(paragraph, file, true, fontFamily, fontSize, false);
            insertTextData(paragraph, " file changes:", false, fontFamily, fontSize, true);
        } else {
            insertTextData(paragraph, "Changes in revision: ", false, fontFamily, fontSize, false);
            insertTextData(
                    paragraph,
                    diffEntry.getCommit().getCommit().substring(0, 9),
                    true, fontFamily, fontSize,
                    false);

            insertTextData(paragraph, " at ", false, fontFamily, fontSize, false);

            insertTextData(paragraph,
                    ReportDataExtractor.DATE_FORMAT.format(diffEntry.getCommit().getAuthorDate()),
                    true, fontFamily, fontSize, true
            );
        }
        for (String headerLine : diffEntry.getDiff().getHeaderLines()) {
            if (StringUtils.isNotBlank(headerLine)) {
                insertTextData(paragraph, headerLine, false, fontFamily, fontSize, true);
            }
        }

        return generateDiffTable(paragraph, fontFamily, fontSize, diffEntry);
    }

    private XWPFParagraph generateDiffTable(final XWPFParagraph paragraph, final String fontFamily,
                                            int fontSize, final GitDiffEntry diffEntry) {
        if (diffEntry.getDiff().getHunks().isEmpty()) {
            return createNewParagraph(paragraph, paragraph.getCTP().newCursor());
        }

        final XWPFTable xwpfTable = createTable(paragraph);

        for (final Hunk hunk : diffEntry.getDiff().getHunks()) {
            fillTableWithDiffHunk(fontFamily, fontSize, xwpfTable, hunk);
        }
        return createNewParagraph(paragraph, xwpfTable.getCTTbl().newCursor());
    }

    private void insertTextData(final XWPFParagraph paragraph, final String text, final boolean bold,
                                final String fontFamily, final int fontSize, final boolean lineBreak) {
        final XWPFRun run = paragraph.createRun();
        run.setFontFamily(fontFamily);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setText(text);
        if (lineBreak) {
            run.addBreak(BreakType.TEXT_WRAPPING);
        }
    }

    private void fillTableWithDiffHunk(final String fontFamily, final int fontSize,
                                       final XWPFTable xwpfTable, final Hunk hunk) {
        final List<Line> lines = hunk.getLines();

        int fromFileCurrentLine = Optional.ofNullable(hunk.getFromFileRange()).map(Range::getLineStart).orElse(0);
        int toFileCurrentLine = Optional.ofNullable(hunk.getToFileRange()).map(Range::getLineStart).orElse(0);

        for (int i = 0; i < lines.size(); i++) {
            final Line line = lines.get(i);
            final XWPFTableRow xwpfTableRow = xwpfTable.insertNewTableRow(i);

            for (int colIndex = 0; colIndex < 4; colIndex++) {
                final XWPFTableCell xwpfTableCell = xwpfTableRow.addNewTableCell();
                while (!xwpfTableCell.getParagraphs().isEmpty()) {
                    xwpfTableCell.removeParagraph(0);
                }

                final XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
                final  XWPFRun xwpfRun = xwpfParagraph.createRun();
                xwpfParagraph.setAlignment(ParagraphAlignment.LEFT);
                xwpfRun.setFontFamily(fontFamily);
                xwpfRun.setFontSize(fontSize);

                String cellData = EMPTY;
                String color = COLOR_WHITE;
                if (colIndex == 0 && (line.getLineType().equals(Line.LineType.FROM)
                        || line.getLineType().equals(Line.LineType.NEUTRAL))) {
                     cellData = String.valueOf(fromFileCurrentLine);
                     fromFileCurrentLine++;
                } else if (colIndex == 1 && (line.getLineType().equals(Line.LineType.TO)
                        || line.getLineType().equals(Line.LineType.NEUTRAL))) {
                    cellData = String.valueOf(toFileCurrentLine);
                    toFileCurrentLine++;
                } else if (colIndex == 2) {
                    if (line.getLineType().equals(Line.LineType.TO)) {
                        cellData = ADDITION_SIGN;
                    } else if (line.getLineType().equals(Line.LineType.FROM)) {
                        cellData = DELETION_SIGN;
                    }
                } else if (colIndex == 3) {
                    cellData = line.getContent();
                }

                if (line.getLineType().equals(Line.LineType.TO)) {
                    color = COLOR_GREEN;
                } else if (line.getLineType().equals(Line.LineType.FROM)) {
                    color = COLOR_RED;
                }

                if (colIndex < 3) {
                    CTTcPr ctTcPr = xwpfTableCell.getCTTc().addNewTcPr();
                    CTTblWidth cellWidth = ctTcPr.addNewTcW();
                    cellWidth.setType(xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getTcW().getType());
                    cellWidth.setW(BigInteger.valueOf(4L));
                    if (xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getGridSpan() != null) {
                        ctTcPr.setGridSpan(xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getGridSpan());
                    }
                }

                xwpfRun.setText(cellData, 0);
                xwpfRun.setFontSize(xwpfRun.getFontSize() - 2);
                xwpfTableCell.setColor(color);
            }
        }
    }

    private XWPFTable createTable(final XWPFParagraph paragraph) {
        final XmlCursor cursor = paragraph.getCTP().newCursor();
        moveCursorToTheEndOfTheToken(cursor);
        final XWPFTable xwpfTable = paragraph.getDocument().insertNewTbl(cursor);
        if (xwpfTable.getRow(0) != null) {
            xwpfTable.removeRow(0);
        }
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

        xwpfTable.getCTTbl().addNewTblGrid().addNewGridCol().setW(BigInteger.valueOf(SMALL_COLUMN_SIZE));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(SMALL_COLUMN_SIZE));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(SMALL_COLUMN_SIZE));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(CONTENT_COLUMN_SIZE));
        return xwpfTable;
    }

    private XWPFParagraph createNewParagraph(final XWPFParagraph paragraph, final XmlCursor lastPosition) {
        moveCursorToTheEndOfTheToken(lastPosition);
        XWPFParagraph nextP = paragraph.getDocument().insertNewParagraph(lastPosition);
        copyParagraphProperties(paragraph, nextP);
        nextP.setPageBreak(false);
        return nextP;
    }

    private void moveCursorToTheEndOfTheToken(final XmlCursor xmlCursor) {
        xmlCursor.toEndToken();
        XmlCursor.TokenType nextToken = xmlCursor.toNextToken();
        while (nextToken != XmlCursor.TokenType.START) {
            nextToken = xmlCursor.toNextToken();
        }
    }

    private void copyParagraphProperties(final XWPFParagraph original, final XWPFParagraph copy) {
        CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

}
