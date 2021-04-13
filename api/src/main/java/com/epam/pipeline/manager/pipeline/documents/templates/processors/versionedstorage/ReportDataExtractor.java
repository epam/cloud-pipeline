package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public interface ReportDataExtractor {

    Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage, final GitDiff diff);

}
