package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.CommitDiffsGrouping;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.util.List;

public class VSReportTemplateMixedProcessor extends AbstractVersionedTemplateProcessor {

    private XWPFParagraph paragraph;

    public VSReportTemplateMixedProcessor(ReportDataExtractor dataProducer) {
        super(dataProducer);
    }

    @Override
    boolean find(XWPFParagraph paragraph, String template) {
        this.paragraph = paragraph;
        return paragraph.getText().contains(template);
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
        if (this.paragraph == null || this.paragraph != paragraph) {
            return;
        }
        XWPFParagraph currentParagraph = null;
        XmlCursor xmlCursor = paragraph.getCTP().newCursor();
        xmlCursor.toNextSibling();

    }

    void insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data instanceof CommitDiffsGrouping) {
            CommitDiffsGrouping diffsGrouping = (CommitDiffsGrouping)data;

            cursor.toNextSibling();
        }
    }

}
