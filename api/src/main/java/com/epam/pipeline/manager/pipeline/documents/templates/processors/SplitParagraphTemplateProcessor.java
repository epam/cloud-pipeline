/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.documents.templates.processors;

import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholder;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.ITemplateContext;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class SplitParagraphTemplateProcessor extends DefaultTemplateProcessor {

    public SplitParagraphTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Method method) {
        super(placeholder, templateContext, method);
    }

    public SplitParagraphTemplateProcessor(Placeholder placeholder, ITemplateContext templateContext, Field field) {
        super(placeholder, templateContext, field);
    }

    boolean insertData(XWPFParagraph splittedParagraph, XWPFRun runTemplate, XmlCursor cursor, Object data) {
        if (data == null) {
            return false;
        }
        XWPFParagraph dataParagraph = splittedParagraph.getDocument().insertNewParagraph(cursor);
        this.copyParagraphProperties(splittedParagraph, dataParagraph);
        XWPFRun newRun = dataParagraph.createRun();
        this.copyRunProperties(runTemplate, newRun);
        newRun.setText(data.toString(), 0);
        cursor.toNextToken();
        return true;
    }

    @Override
    void replacePlaceholderWithData(XWPFParagraph paragraph, Object data) {
        if (this.xwpfRun == null || paragraph == null) {
            return;
        }
        int globalStartIndex = 0;
        boolean shouldMoveRun = false;
        int runToRemoveIndex = 0;
        XWPFParagraph currentParagraph = null;
        final List<XWPFRun> runs = paragraph.getRuns();
        XmlCursor xmlCursor = paragraph.getCTP().newCursor();
        xmlCursor.toNextSibling();
        for (XWPFRun run : runs) {
            if (!shouldMoveRun) {
                runToRemoveIndex++;
            }
            String runText = run.getText(0);
            if (runText == null) {
                continue;
            }
            int globalEndIndex = globalStartIndex + runText.length();
            if (globalStartIndex > this.end || globalEndIndex < this.start) {
                globalStartIndex = globalEndIndex;
                if (shouldMoveRun && currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun, true);
                }
                continue;
            }
            int replaceFrom = Math.max(globalStartIndex, this.start) - globalStartIndex;
            int replaceTo = Math.min(globalEndIndex, this.end) - globalStartIndex;
            if (this.xwpfRun.equals(run)) {
                String beforePlaceholderText = runText.substring(0, replaceFrom);
                run.setText(beforePlaceholderText, 0);

                this.insertData(paragraph, run, xmlCursor, data);

                if (!xmlCursor.isStart()) {
                    break;
                }

                currentParagraph = paragraph.getDocument().insertNewParagraph(xmlCursor);
                this.copyParagraphProperties(paragraph, currentParagraph);

                String afterPlaceholderText = runText.substring(replaceTo);
                shouldMoveRun = true;
                if (currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun);
                    newRun.setText(afterPlaceholderText, 0);
                }
            } else {
                runText = runText.substring(0, replaceFrom).concat(runText.substring(replaceTo));
                run.setText(runText, 0);
                if (shouldMoveRun && currentParagraph != null) {
                    XWPFRun newRun = currentParagraph.createRun();
                    this.copyRunProperties(run, newRun, true);
                }
            }
            globalStartIndex = globalEndIndex;
        }

        while (paragraph.getRuns().size() > runToRemoveIndex) {
            paragraph.removeRun(runToRemoveIndex);
        }
    }

    void copyParagraphProperties(XWPFParagraph original, XWPFParagraph copy) {
        CTPPr pPr = copy.getCTP().isSetPPr() ? copy.getCTP().getPPr() : copy.getCTP().addNewPPr();
        pPr.set(original.getCTP().getPPr());
    }

    void copyRunProperties(XWPFRun original, XWPFRun copy) {
        this.copyRunProperties(original, copy, false);
    }

    void copyRunProperties(XWPFRun original, XWPFRun copy, boolean copyText) {
        CTRPr rPr = copy.getCTR().isSetRPr() ? copy.getCTR().getRPr() : copy.getCTR().addNewRPr();
        rPr.set(original.getCTR().getRPr());
        if (copyText) {
            copy.setText(original.getText(0));
        }
    }
}
