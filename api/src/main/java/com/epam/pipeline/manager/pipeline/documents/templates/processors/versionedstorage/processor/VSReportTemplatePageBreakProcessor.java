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

import com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.ReportDataExtractor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;


public class VSReportTemplatePageBreakProcessor extends AbstractVSReportTemplateProcessor {

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
