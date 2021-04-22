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
        XWPFParagraph nextP = paragraph.getDocument().insertNewParagraph(paragraph.getCTP().newCursor());
        copyParagraphProperties(paragraph, nextP);
        XWPFRun newRun = nextP.createRun();
        copyRunProperties(paragraph.getRuns().get(0), newRun);
        while (!paragraph.getRuns().isEmpty()){
            paragraph.removeRun(0);
        }
        paragraph = nextP;
        insertData(paragraph, paragraph.getRuns().get(0), paragraph.getCTP().newCursor(), data);
    }

    void insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data instanceof CommitDiffsGrouping) {
            CommitDiffsGrouping diffsGrouping = (CommitDiffsGrouping)data;
            XWPFParagraph lastP = splittedParagraph;
            for (Map.Entry<String, List<GitDiffEntry>> entry : diffsGrouping.getDiffGrouping().entrySet()) {
                final String key = entry.getKey();
                final List<GitDiffEntry> diffEntries = entry.getValue();
                addHeader(lastP, runTemplate, diffsGrouping.getType(), key, diffEntries);
                for (GitDiffEntry diffEntry : diffEntries) {
                    addDescription(
                            lastP, runTemplate, diffsGrouping.getType(), key, diffEntry
                    );
                }
                lastP.setPageBreak(true);
                XWPFParagraph nextP = lastP.getDocument().insertNewParagraph(lastP.getCTP().newCursor());
                copyParagraphProperties(lastP, nextP);
                lastP = nextP;
            }
            cursor.toNextSibling();
        }
    }

    private void addHeader(XWPFParagraph header, XWPFRun runTemplate,
                           CommitDiffsGrouping.GroupType type, String key,
                           List<GitDiffEntry> diffEntries) {
        if (type.equals(CommitDiffsGrouping.GroupType.BY_COMMIT)) {
            XWPFRun run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText("In revision ");

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(key.substring(0, 9));

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(" by ");

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(diffEntries.stream().findFirst()
                    .map(GitDiffEntry::getCommit)
                    .map(GitReaderRepositoryCommit::getAuthor).orElse(""));

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(" at ");

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(diffEntries.stream().findFirst()
                    .map(GitDiffEntry::getCommit)
                    .map(c -> ReportDataExtractor.DATE_FORMAT.format(c.getAuthorDate())).orElse(""));
        } else {
            XWPFRun run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText("Changes of file ");

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setBold(true);
            run.setText(key);

            run = header.createRun();
            copyRunProperties(runTemplate, run);
            run.setFontSize(runTemplate.getFontSize() + 2);
            run.setText(":");
        }
        header.createRun().addBreak();
    }

    private void addDescription(XWPFParagraph paragraph, XWPFRun runTemplate,
                                CommitDiffsGrouping.GroupType type, String key,
                                GitDiffEntry diffEntry) {
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

        for (String headerLine : diffEntry.getDiff().getHeaderLines()) {
            XWPFRun run = paragraph.createRun();
            copyRunProperties(runTemplate, run);
            run.addBreak();
            run.setText(headerLine);
        }

        XmlCursor cursor = paragraph.getCTP().newCursor();
        cursor.toNextSibling();
        XWPFTable xwpfTable = paragraph.getDocument().insertNewTbl(cursor);
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

        xwpfTable.getCTTbl().addNewTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(512));
        xwpfTable.getCTTbl().getTblGrid().addNewGridCol().setW(BigInteger.valueOf(8704));

        for (Hunk hunk : diffEntry.getDiff().getHunks()) {
            final List<Line> lines = hunk.getLines();

            int fromFileCurrentLine = Optional.ofNullable(hunk.getFromFileRange()).map(Range::getLineStart).orElse(0);
            int toFileCurrentLine = Optional.ofNullable(hunk.getToFileRange()).map(Range::getLineStart).orElse(0);

            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                XWPFTableRow xwpfTableRow = xwpfTable.insertNewTableRow(i);

                for (int colIndex = 0; colIndex < 4; colIndex++) {
                    XWPFTableCell xwpfTableCell = xwpfTableRow.addNewTableCell();
                    xwpfTableCell.removeParagraph(0);
                    XWPFParagraph xwpfParagraph = xwpfTableCell.addParagraph();
                    XWPFRun xwpfRun = xwpfParagraph.createRun();
                    xwpfParagraph.setAlignment(ParagraphAlignment.LEFT);
                    this.copyRunProperties(runTemplate, xwpfRun);

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
    }

    void copyParagraphProperties(XWPFParagraph original, XWPFParagraph copy) {
        CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

    void copyRunProperties(XWPFRun original, XWPFRun copy) {
        CTRPr rPr = copy.getCTR().isSetRPr() ? copy.getCTR().getRPr() : copy.getCTR().addNewRPr();
        rPr.set(original.getCTR().getRPr());
    }


}
