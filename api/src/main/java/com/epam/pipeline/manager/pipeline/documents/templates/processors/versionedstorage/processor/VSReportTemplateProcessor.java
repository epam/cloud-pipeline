package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage.processor;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffReportFilter;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public interface VSReportTemplateProcessor {
    void process(XWPFParagraph paragraph, String template, Pipeline storage, GitDiff diff,
                 GitDiffReportFilter reportFilter);
}
