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

import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.report.GitDiffGroupType;
import com.epam.pipeline.entity.git.report.GitDiffGrouping;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage.processor.extractor.ReportDataExtractor;
import com.epam.pipeline.manager.utils.DiffUtils;
import io.reflectoring.diffparser.api.model.Hunk;
import io.reflectoring.diffparser.api.model.Line;
import io.reflectoring.diffparser.api.model.Range;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class VSReportTemplateDiffProcessor implements VSReportTemplateProcessor {

    private static final String COLOR_WHITE = "ffffff";
    private static final String COLOR_GREEN = "9ef08e";
    private static final String COLOR_RED = "f08e8e";
    private static final String EMPTY = "";
    private static final String ADDITION_SIGN = "+";
    private static final String DELETION_SIGN = "-";
    private static final int SMALL_COLUMN_SIZE = 512;
    private static final int CONTENT_COLUMN_SIZE = 8960;
    private static final int HEADER_FONT_DELTA = 4;
    private static final int REPORT_DIFF_TABLE_COL_SIZE = 4;
    private static final int FILE_FROM_LINE_INDEX_COLUMN = 0;
    private static final int FILE_TO_LINE_INDEX_COLUMN = 1;
    private static final int CHANGES_TYPE_COLUMN = 2;
    private static final int CONTENT_COLUMN = 3;
    public static final String END_OF_FILE = "No newline at end of file";

    private final ReportDataExtractor<GitDiffGrouping> dataProducer;

    public void replacePlaceholderWithData(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                                           final GitParsedDiff diff, final GitDiffReportFilter reportFilter) {
        if (StringUtils.isBlank(paragraph.getText()) || !paragraph.getText().contains(template)) {
            return;
        }
        cleanUpParagraph(paragraph);
        insertData(paragraph, paragraph.getRuns().get(0), dataProducer.extract(paragraph, storage, diff, reportFilter));
    }

    void insertData(final XWPFParagraph paragraph, final XWPFRun runTemplate, final GitDiffGrouping diffsGrouping) {
        if (!diffsGrouping.isIncludeDiff() || diffsGrouping.isArchive()) {
            return;
        }

        final String fontFamily = runTemplate.getFontFamily();
        final int fontSize = runTemplate.getFontSize();
        XWPFParagraph lastParagraph = paragraph;
        lastParagraph.setPageBreak(true);
        for (Map.Entry<String, List<GitParsedDiffEntry>> entry : getSortedDiffGroups(diffsGrouping)) {
            lastParagraph.setPageBreak(true);
            final String diffGroupKey = entry.getKey();
            final List<GitParsedDiffEntry> diffEntries = entry.getValue().stream()
                    .sorted(resolveDiffGroupComparator(diffsGrouping))
                    .collect(Collectors.toList());

            addHeader(lastParagraph, fontFamily, fontSize, diffsGrouping.getType(), diffGroupKey, diffEntries);
            for (int i = 0; i < diffEntries.size(); i++) {
                final GitParsedDiffEntry diffEntry = diffEntries.get(i);
                lastParagraph = addDescription(lastParagraph, fontFamily, fontSize, diffsGrouping.getType(), diffEntry);
                if (!isLastEntryInGroup(diffEntries.size(), i)) {
                    addIndent(lastParagraph);
                }
            }
        }
    }

    private boolean isLastEntryInGroup(final int entriesSize, final int index) {
        return entriesSize - 1 <= index;
    }

    private List<Map.Entry<String, List<GitParsedDiffEntry>>> getSortedDiffGroups(
            final GitDiffGrouping diffsGrouping) {
        return diffsGrouping.getDiffGrouping().entrySet()
                .stream()
                .sorted(diffsGrouping.getType() == GitDiffGroupType.BY_FILE
                        ? Map.Entry.comparingByKey()
                        : Comparator.comparing(this::retrieveDateOfDiffGroup)
                ).collect(Collectors.toList());
    }

    private Date retrieveDateOfDiffGroup(final Map.Entry<String, List<GitParsedDiffEntry>> diffGroup) {
        return Optional.ofNullable(diffGroup.getValue())
                .map(e -> e.get(0))
                .map(GitParsedDiffEntry::getCommit)
                .map(GitReaderRepositoryCommit::getAuthorDate)
                .orElse(Date.from(Instant.EPOCH));
    }

    private Comparator<GitParsedDiffEntry> resolveDiffGroupComparator(final GitDiffGrouping diffsGrouping) {
        return diffsGrouping.getType() == GitDiffGroupType.BY_COMMIT
                ? Comparator.comparing(d -> DiffUtils.getChangedFileName(d.getDiff()))
                : Comparator.comparing(d -> d.getCommit().getAuthorDate());
    }

    private void addHeader(final XWPFParagraph paragraph, final String fontFamily, final int fontSize,
                           final GitDiffGroupType type, final String groupingKey,
                           final List<GitParsedDiffEntry> diffEntries) {

        if (type.equals(GitDiffGroupType.BY_COMMIT)) {
            insertTextData(paragraph, "In revision ", false, fontFamily,
                    fontSize + HEADER_FONT_DELTA, false);
            insertTextData(paragraph, groupingKey.substring(0, 9), true, fontFamily,
                    fontSize + HEADER_FONT_DELTA, false);
            insertTextData(paragraph, " by ", false, fontFamily, fontSize + HEADER_FONT_DELTA, false);

            insertTextData(paragraph,
                    diffEntries.stream().findFirst()
                        .map(GitParsedDiffEntry::getCommit)
                        .map(GitReaderRepositoryCommit::getAuthor).orElse(""),
                    true, fontFamily, fontSize + HEADER_FONT_DELTA,
                    false);

            insertTextData(paragraph, " at ", false, fontFamily, fontSize + HEADER_FONT_DELTA, false);

            insertTextData(paragraph,
                    diffEntries.stream().findFirst()
                        .map(GitParsedDiffEntry::getCommit)
                        .map(c -> DATE_FORMAT.format(c.getAuthorDate())).orElse(""),
                    false, fontFamily, fontSize + HEADER_FONT_DELTA, false);
        } else {
            insertTextData(paragraph, "Changes of file ", false, fontFamily,
                    fontSize + HEADER_FONT_DELTA, false);
            insertTextData(paragraph, groupingKey, true, fontFamily, fontSize + HEADER_FONT_DELTA, false);
            insertTextData(paragraph, ":", false, fontFamily, fontSize + HEADER_FONT_DELTA, false);
        }
        addIndent(paragraph);

    }

    private XWPFParagraph addDescription(final XWPFParagraph paragraph, final String fontFamily, final int fontSize,
                                         final GitDiffGroupType type, final GitParsedDiffEntry diffEntry) {
        if (type.equals(GitDiffGroupType.BY_COMMIT)) {
            final String file = DiffUtils.getChangedFileName(diffEntry.getDiff());
            insertTextData(paragraph, file, true, fontFamily, fontSize, false);
            insertTextData(paragraph, " file changes:", false, fontFamily, fontSize, true);
        } else {
            insertTextData(paragraph, "Changes in revision: ", false, fontFamily, fontSize, false);
            insertTextData(
                    paragraph,
                    diffEntry.getCommit().getCommit().substring(0, 9),
                    true, fontFamily, fontSize,
                    false);

            insertTextData(paragraph, " by ", false, fontFamily, fontSize + HEADER_FONT_DELTA, false);
            insertTextData(paragraph,
                    diffEntry.getCommit().getAuthor(),
                    true, fontFamily, fontSize + HEADER_FONT_DELTA,
                    false);

            insertTextData(paragraph, " at ", false, fontFamily, fontSize, false);

            insertTextData(paragraph,
                    DATE_FORMAT.format(diffEntry.getCommit().getAuthorDate()),
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

    private void addIndent(final XWPFParagraph paragraph) {
        final XWPFRun run = paragraph.createRun();
        run.addBreak(BreakType.TEXT_WRAPPING);
        run.addBreak(BreakType.TEXT_WRAPPING);
    }

    private XWPFParagraph generateDiffTable(final XWPFParagraph paragraph, final String fontFamily,
                                            int fontSize, final GitParsedDiffEntry diffEntry) {
        XmlCursor cursor = paragraph.getCTP().newCursor();

        if (!diffEntry.getDiff().getHunks().isEmpty()) {
            final XWPFTable xwpfTable = createTable(paragraph);
            // since hunks could be not sort by line number - sort by ourself
            diffEntry.getDiff().getHunks()
                    .sort((h1, h2) -> Math.max(h2.getFromFileRange().getLineStart(), h2.getToFileRange().getLineStart())
                            - Math.max(h1.getFromFileRange().getLineStart(), h1.getToFileRange().getLineStart()));
            for (final Hunk hunk : diffEntry.getDiff().getHunks()) {
                fillTableWithDiffHunk(fontFamily, fontSize, xwpfTable, hunk);
            }
            cursor = xwpfTable.getCTTbl().newCursor();
        }

        return createNewParagraph(paragraph, cursor);
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

        int rowCount = 0;
        for (Line line : lines) {
            if (line.getContent().contains(END_OF_FILE)) {
                continue;
            }
            final XWPFTableRow xwpfTableRow = xwpfTable.insertNewTableRow(rowCount++);

            for (int colIndex = FILE_FROM_LINE_INDEX_COLUMN; colIndex < REPORT_DIFF_TABLE_COL_SIZE; colIndex++) {
                final XWPFTableCell xwpfTableCell = xwpfTableRow.addNewTableCell();
                final XWPFRun xwpfRun = createCellRun(fontFamily, fontSize, xwpfTableCell);

                String cellData = EMPTY;
                if (colIndex == FILE_FROM_LINE_INDEX_COLUMN && lineExistsInFile(line, Line.LineType.FROM)) {
                    cellData = String.valueOf(fromFileCurrentLine);
                    fromFileCurrentLine++;
                } else if (colIndex == FILE_TO_LINE_INDEX_COLUMN && lineExistsInFile(line, Line.LineType.TO)) {
                    cellData = String.valueOf(toFileCurrentLine);
                    toFileCurrentLine++;
                } else if (colIndex == CHANGES_TYPE_COLUMN) {
                    if (line.getLineType().equals(Line.LineType.TO)) {
                        cellData = ADDITION_SIGN;
                    } else if (line.getLineType().equals(Line.LineType.FROM)) {
                        cellData = DELETION_SIGN;
                    }
                } else if (colIndex == CONTENT_COLUMN) {
                    cellData = line.getContent();
                }

                // We set size for all columns except last one to constantly small to provide
                // more space for content column
                if (colIndex < CONTENT_COLUMN) {
                    configureColumnWidth(xwpfTableRow, colIndex, xwpfTableCell);
                }

                xwpfRun.setText(cellData, 0);
                xwpfRun.setFontSize(xwpfRun.getFontSize() - 2);
                xwpfTableCell.setColor(getLineColor(line));
            }
        }
    }

    private boolean lineExistsInFile(final Line line, final Line.LineType lineType) {
        return line.getLineType().equals(lineType)
                || line.getLineType().equals(Line.LineType.NEUTRAL);
    }

    private String getLineColor(final Line line) {
        switch (line.getLineType()) {
            case TO:
                return COLOR_GREEN;
            case FROM:
                return COLOR_RED;
            default:
                return COLOR_WHITE;
        }
    }

    private void configureColumnWidth(final XWPFTableRow xwpfTableRow, final int colIndex,
                                      final XWPFTableCell xwpfTableCell) {
        final CTTcPr ctTcPr = xwpfTableCell.getCTTc().addNewTcPr();
        final CTTblWidth cellWidth = ctTcPr.addNewTcW();
        cellWidth.setType(xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getTcW().getType());
        cellWidth.setW(BigInteger.valueOf(4L));
        if (xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getGridSpan() != null) {
            ctTcPr.setGridSpan(xwpfTableRow.getCell(colIndex).getCTTc().getTcPr().getGridSpan());
        }
    }

    private XWPFRun createCellRun(final String fontFamily, final int fontSize, final XWPFTableCell xwpfTableCell) {
        while (!xwpfTableCell.getParagraphs().isEmpty()) {
            xwpfTableCell.removeParagraph(0);
        }
        final XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
        final  XWPFRun xwpfRun = xwpfParagraph.createRun();
        xwpfParagraph.setAlignment(ParagraphAlignment.LEFT);
        xwpfRun.setFontFamily(fontFamily);
        xwpfRun.setFontSize(fontSize);
        return xwpfRun;
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
        final XWPFParagraph nextParagraph = paragraph.getDocument().insertNewParagraph(lastPosition);
        copyParagraphProperties(paragraph, nextParagraph);
        nextParagraph.setPageBreak(false);
        return nextParagraph;
    }

    private void moveCursorToTheEndOfTheToken(final XmlCursor xmlCursor) {
        xmlCursor.toEndToken();
        XmlCursor.TokenType nextToken = xmlCursor.toNextToken();
        while (nextToken != XmlCursor.TokenType.START) {
            nextToken = xmlCursor.toNextToken();
        }
    }

    private void copyParagraphProperties(final XWPFParagraph original, final XWPFParagraph copy) {
        (copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr()).set(original.getCTP().getPPr());
    }

}
