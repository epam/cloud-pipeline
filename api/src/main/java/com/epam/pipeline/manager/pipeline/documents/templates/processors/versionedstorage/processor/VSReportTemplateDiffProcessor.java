package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.entity.git.GitDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.CommitDiffsGrouping;
import io.reflectoring.diffparser.api.model.Hunk;
import io.reflectoring.diffparser.api.model.Line;
import io.reflectoring.diffparser.api.model.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor.ReportUtils.copyParagraphProperties;
import static com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor.ReportUtils.copyRunProperties;

public class VSReportTemplateDiffProcessor extends AbstractVSReportTemplateProcessor {

    private XWPFParagraph paragraph;

    public VSReportTemplateDiffProcessor(ReportDataExtractor dataProducer) {
        super(dataProducer);
    }

    @Override
    boolean find(XWPFParagraph paragraph, String template) {
        if (paragraph == null || StringUtils.isBlank(paragraph.getText())) {
            return false;
        }
        if (paragraph.getText().contains(template)) {
            this.paragraph = paragraph;
            return true;
        }
        return false;
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
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

    void insertData(XWPFParagraph paragraph, XWPFRun runTemplate, Object data) {
        if (data instanceof CommitDiffsGrouping) {
            CommitDiffsGrouping diffsGrouping = (CommitDiffsGrouping)data;
            if (!diffsGrouping.isIncludeDiff() || diffsGrouping.isArchive()) {
                return;
            }
            XWPFParagraph lastP = paragraph;
            lastP.setPageBreak(true);
            for (Map.Entry<String, List<GitDiffEntry>> entry : diffsGrouping.getDiffGrouping().entrySet()) {
                final String diffGroupKey = entry.getKey();
                final List<GitDiffEntry> diffEntries = entry.getValue();

                addHeader(lastP, runTemplate, diffsGrouping.getType(), diffGroupKey, diffEntries);
                for (GitDiffEntry diffEntry : diffEntries) {
                    lastP.setPageBreak(true);
                    lastP = addDescription(lastP, runTemplate, diffsGrouping.getType(), diffEntry);
                }
            }
        }
    }

    private void addHeader(XWPFParagraph paragraph, XWPFRun runTemplate,
                           CommitDiffsGrouping.GroupType type, String key,
                           List<GitDiffEntry> diffEntries) {
        if (type.equals(CommitDiffsGrouping.GroupType.BY_COMMIT)) {
            XWPFRun run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText("In revision ");

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(key.substring(0, 9));

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(" by ");

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(diffEntries.stream().findFirst()
                    .map(GitDiffEntry::getCommit)
                    .map(GitReaderRepositoryCommit::getAuthor).orElse(""));

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(" at ");

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(diffEntries.stream().findFirst()
                    .map(GitDiffEntry::getCommit)
                    .map(c -> ReportDataExtractor.DATE_FORMAT.format(c.getAuthorDate())).orElse(""));
        } else {
            XWPFRun run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText("Changes of file ");

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(key);

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(":");
        }
        paragraph.createRun().addBreak(BreakType.TEXT_WRAPPING);
    }

    private XWPFParagraph addDescription(XWPFParagraph paragraph, XWPFRun runTemplate,
                                         CommitDiffsGrouping.GroupType type, GitDiffEntry diffEntry) {
        if (type.equals(CommitDiffsGrouping.GroupType.BY_COMMIT)) {
            XWPFRun run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setBold(true);
            run.setText(diffEntry.getDiff().getToFileName());

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setText(" file changes:");
        } else {
            XWPFRun run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setText("Changes in revision: ");

            run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.setBold(true);
            run.setText(diffEntry.getCommit().getCommit().substring(0, 9));
        }

        paragraph.createRun().addBreak(BreakType.TEXT_WRAPPING);

        for (String headerLine : diffEntry.getDiff().getHeaderLines()) {
            if (StringUtils.isNotBlank(headerLine)) {
                XWPFRun run = paragraph.createRun();
                copyRunProperties(runTemplate, run);
                run.setText(headerLine);
                run.addBreak(BreakType.TEXT_WRAPPING);
            }
        }

        return generateDiffTable(paragraph, runTemplate, diffEntry);
    }

    private XWPFParagraph generateDiffTable(final XWPFParagraph paragraph,
                                            final XWPFRun runTemplate, final GitDiffEntry diffEntry) {
        if (diffEntry.getDiff().getHunks().isEmpty()) {
            return createNewParagraph(paragraph, paragraph.getCTP().newCursor());
        }

        XWPFTable xwpfTable = createTable(paragraph);

        for (Hunk hunk : diffEntry.getDiff().getHunks()) {
            final List<Line> lines = hunk.getLines();

            int fromFileCurrentLine = Optional.ofNullable(hunk.getFromFileRange()).map(Range::getLineStart).orElse(0);
            int toFileCurrentLine = Optional.ofNullable(hunk.getToFileRange()).map(Range::getLineStart).orElse(0);

            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                XWPFTableRow xwpfTableRow = xwpfTable.insertNewTableRow(i);

                for (int colIndex = 0; colIndex < 4; colIndex++) {
                    XWPFTableCell xwpfTableCell = xwpfTableRow.addNewTableCell();
                    while (!xwpfTableCell.getParagraphs().isEmpty()) {
                        xwpfTableCell.removeParagraph(0);
                    }
                    XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
                    XWPFRun xwpfRun = xwpfParagraph.createRun();
                    xwpfParagraph.setAlignment(ParagraphAlignment.LEFT);
                    copyRunProperties(runTemplate, xwpfRun);

                    String cellData = "";
                    String color = "ffffff";
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
                            cellData = "+";
                        } else if (line.getLineType().equals(Line.LineType.FROM)) {
                            cellData = "-";
                        }
                    } else if (colIndex == 3) {
                        cellData = line.getContent();
                    }

                    if (line.getLineType().equals(Line.LineType.TO)) {
                        color = "9ef08e";
                    } else if (line.getLineType().equals(Line.LineType.FROM)) {
                        color = "f08e8e";
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
        return createNewParagraph(paragraph, xwpfTable.getCTTbl().newCursor());
    }

    private XWPFTable createTable(XWPFParagraph paragraph) {
        XmlCursor cursor = paragraph.getCTP().newCursor();
        moveCursorToTheEndOfTheToken(cursor);
        XWPFTable xwpfTable = paragraph.getDocument().insertNewTbl(cursor);
        if (xwpfTable.getRow(0) != null) {
            xwpfTable.removeRow(0);
        }
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

        xwpfTable.getCTTbl().addNewTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(8704));
        return xwpfTable;
    }

    private XWPFParagraph createNewParagraph(XWPFParagraph paragraph, XmlCursor lastPosition) {
        moveCursorToTheEndOfTheToken(lastPosition);
        XWPFParagraph nextP = paragraph.getDocument().insertNewParagraph(lastPosition);
        copyParagraphProperties(paragraph, nextP);
        nextP.setPageBreak(false);
        return nextP;
    }

    private void moveCursorToTheEndOfTheToken(XmlCursor lastPosition) {
        lastPosition.toEndToken();
        while (lastPosition.toNextToken() != XmlCursor.TokenType.START) ;
    }
}
