package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;


import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

public final class ReportUtils {
    private ReportUtils() {}

    static void copyParagraphProperties(XWPFParagraph original, XWPFParagraph copy) {
        CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

    static void copyRunProperties(XWPFRun original, XWPFRun copy) {
        copy.setColor(original.getColor());
        copy.setFontFamily(original.getFontFamily());
        copy.setFontSize(original.getFontSize());
    }
}
