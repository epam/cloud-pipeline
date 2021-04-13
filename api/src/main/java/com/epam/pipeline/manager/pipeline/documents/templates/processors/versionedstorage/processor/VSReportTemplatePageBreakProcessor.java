package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;


public class VSReportTemplatePageBreakProcessor extends AbstractVersionedTemplateProcessor {

    public VSReportTemplatePageBreakProcessor(ReportDataExtractor dataProducer) {
        super(dataProducer);
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
        if (this.xwpfRun == null) {
            return;
        }
        int globalStartIndex = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.getText(0);
            if (runText == null) {
                continue;
            }
            int globalEndIndex = globalStartIndex + runText.length();
            if (globalStartIndex > this.end || globalEndIndex < this.start) {
                globalStartIndex = globalEndIndex;
                continue;
            }
            int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
            int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
            if (this.xwpfRun.equals(run)) {
                runText = runText.substring(0, replaceFrom)
                        .concat("")
                        .concat(runText.substring(replaceTo));
                run.setText(runText, 0);
            } else {
                runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                run.setText(runText, 0);
            }
            globalStartIndex = globalEndIndex;
        }
        paragraph.setPageBreak(true);
    }

}
