package com.epam.pipeline.manager.pipeline.documents.templates.processors.versionedstorage;

import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.text.SimpleDateFormat;

public interface ReportDataExtractor {

    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    Object apply(final XWPFParagraph xwpfParagraph, final Pipeline storage, final GitDiff diff);

}
