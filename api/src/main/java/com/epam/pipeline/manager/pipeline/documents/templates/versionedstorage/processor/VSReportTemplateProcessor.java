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
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.text.SimpleDateFormat;
import java.util.List;

public interface VSReportTemplateProcessor {

    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    default void process(final XWPFParagraph paragraph, final String template, final Pipeline storage,
                         final GitParsedDiff diff, final GitDiffReportFilter reportFilter,
                         final List<String> customBinaryExtension) {
        if (paragraph == null) {
            return;
        }
        while (paragraph.getText().matches(getPlaceholderRegexp(template))) {
            replacePlaceholderWithData(paragraph, template, storage, diff, reportFilter, customBinaryExtension);
        }
    }

    default String getPlaceholderRegexp(final String template) {
        return ".*(?i)\\{" + template + "}.*";
    }

    default void cleanUpParagraph(final XWPFParagraph paragraph) {
        // We clean up all runs except one
        while (paragraph.getRuns().size() != 1) {
            paragraph.removeRun(0);
        }

        // Remove all text from this run, we can just remove whole run and create clean one
        // but we want to save run properties
        paragraph.getRuns().forEach(run -> {
            for (int pos = 0; pos < run.getCTR().sizeOfTArray(); pos++) {
                run.setText("", pos);
            }
        });
    }

    void replacePlaceholderWithData(XWPFParagraph paragraph, String template, Pipeline storage,
                                    GitParsedDiff diff, GitDiffReportFilter reportFilter,
                                    List<String> customBinaryExtension);


}
