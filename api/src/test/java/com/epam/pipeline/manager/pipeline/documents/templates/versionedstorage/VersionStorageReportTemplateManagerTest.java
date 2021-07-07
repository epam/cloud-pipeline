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

package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.report.VersionStorageReportFile;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.entity.git.report.GitDiffGroupType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

public class VersionStorageReportTemplateManagerTest extends AbstractManagerTest {

    public static final GitDiffReportFilter GIT_COMMITS_FILTER = GitDiffReportFilter
            .builder()
            .commitsFilter(
                    GitCommitsFilter
                        .builder()
                        .authors(Collections.singletonList("user1@test.com")
                        ).build()
            ).groupType(GitDiffGroupType.BY_COMMIT).build();


    public static final GitDiffReportFilter GIT_COMMITS_FILTER_2 =
            GitDiffReportFilter
                    .builder()
                    .commitsFilter(
                            GitCommitsFilter
                                    .builder()
                                    .authors(Collections.singletonList("user2@test.com"))
                                    .build()
                    ).groupType(GitDiffGroupType.BY_COMMIT).build();

    @MockBean
    private PipelineManager pipelineManager;

    @MockBean
    private GitManager gitManager;

    @SpyBean
    private PreferenceManager preferenceManager;

    @Autowired
    @InjectMocks
    private VersionStorageReportTemplateManager reportTemplateManager;

    @Before
    public void setup() throws IOException {
        Pipeline mockPipeline = new Pipeline();
        mockPipeline.setName("pipeline");
        mockPipeline.setId(1L);

        Mockito.when(
                pipelineManager.load(1L)
        ).thenReturn(mockPipeline);

        Mockito.when(
                preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_REPORT_TEMPLATE)
        ).thenReturn(getTestFile("vs_report_template.docx").getPath());

        Mockito.when(
                gitManager.logRepositoryCommitDiffs(
                        1L,
                        true,
                        GIT_COMMITS_FILTER.getCommitsFilter()
                )
        ).thenReturn(GitReaderDiff.builder()
                .filters(GIT_COMMITS_FILTER.getCommitsFilter())
                .entries(
                    Collections.singletonList(
                        new GitReaderDiffEntry(
                            GitReaderRepositoryCommit.builder()
                                    .author(GIT_COMMITS_FILTER.getCommitsFilter().getAuthors().get(0))
                                    .authorDate(DateUtils.now())
                                    .committerDate(DateUtils.now())
                                    .authorEmail(GIT_COMMITS_FILTER.getCommitsFilter().getAuthors().get(0))
                                    .commit("aaa111bbb222")
                                    .parentSHAs(Collections.emptyList())
                                    .build(),
                            "diff --git a/test.csv b/test.csv\n" +
                                    "new file mode 100644\n" +
                                    "index 0000000..8d7862c\n" +
                                    "--- /dev/null\n" +
                                    "+++ b/test.csv\n" +
                                    "@@ -0,0 +1,2 @@\n" +
                                    "+    1,2,3" +
                                    "+    4,5,6" +
                                    "diff --git a/src/test b/src/test\n" +
                                    "index 8366d1c..0000000\n" +
                                    "Binary files a/src/test and b/src/test differ"
                        )
                    )
                ).build()
        );

        Mockito.when(
                gitManager.logRepositoryCommitDiffs(
                        1L,
                        true,
                        GIT_COMMITS_FILTER_2.getCommitsFilter()
                )
        ).thenReturn(GitReaderDiff.builder()
                .filters(GIT_COMMITS_FILTER_2.getCommitsFilter())
                .entries(
                    Collections.singletonList(
                        new GitReaderDiffEntry(
                            GitReaderRepositoryCommit.builder()
                                    .author(GIT_COMMITS_FILTER_2.getCommitsFilter().getAuthors().get(0))
                                    .authorDate(DateUtils.now())
                                    .committerDate(DateUtils.now())
                                    .authorEmail(GIT_COMMITS_FILTER_2.getCommitsFilter().getAuthors().get(0))
                                    .commit("ccc111ddd222")
                                    .parentSHAs(Collections.emptyList())
                                    .build(),
                            "diff --git a/test.csv b/test.csv\n" +
                                    "new file mode 100644\n" +
                                    "index 0000000..8d7862c\n" +
                                    "--- /dev/null\n" +
                                    "+++ b/test.csv\n" +
                                    "@@ -0,0 +1,2 @@\n" +
                                    "+    1,2,3" +
                                    "+    4,5,6"
                        )
                    )
                ).build()
        );
    }

    @Test
    public void generationOfReportsWorksCorrectlyTest() throws IOException {
        final VersionStorageReportFile report = reportTemplateManager.generateReport(1L, GIT_COMMITS_FILTER);
        //check that Apache poi could read such report -> this docx file is valid
        XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(report.getContent()));
        Assert.assertFalse(document.getBodyElements().isEmpty());
        document.getParagraphs().stream().map(XWPFParagraph::getText).forEach(
            text -> Assert.assertFalse(text.matches(".*\\{.*}.*"))
        );
    }

    @Test
    public void fetchAndNormalizeDiffWorksWithBinaryAndText() {
        final GitParsedDiff gitDiff = reportTemplateManager.fetchAndNormalizeDiffs(1L, GIT_COMMITS_FILTER);
        Assert.assertEquals(2, gitDiff.getEntries().size());

        GitParsedDiffEntry first = gitDiff.getEntries().get(0);
        GitParsedDiffEntry second = gitDiff.getEntries().get(1);

        Assert.assertEquals("/dev/null", first.getDiff().getFromFileName());
        Assert.assertEquals("test.csv", first.getDiff().getToFileName());

        Assert.assertEquals("src/test", second.getDiff().getFromFileName());
        Assert.assertEquals("src/test", second.getDiff().getToFileName());

        Assert.assertEquals(GIT_COMMITS_FILTER.getCommitsFilter().getDateFrom(), gitDiff.getFilters().getDateFrom());
        Assert.assertEquals(GIT_COMMITS_FILTER.getCommitsFilter().getDateTo(), gitDiff.getFilters().getDateTo());
        Assert.assertEquals(GIT_COMMITS_FILTER.getCommitsFilter().getPath(), gitDiff.getFilters().getPath());
        Assert.assertEquals(GIT_COMMITS_FILTER.getCommitsFilter().getRef(), gitDiff.getFilters().getRef());
        Assert.assertArrayEquals(
            GIT_COMMITS_FILTER.getCommitsFilter().getAuthors().toArray(),
            gitDiff.getFilters().getAuthors().toArray()
        );
    }

    @Test
    public void fetchAndNormalizeDiffWorksWithText() {
        final GitParsedDiff gitDiff = reportTemplateManager.fetchAndNormalizeDiffs(1L, GIT_COMMITS_FILTER_2);
        Assert.assertEquals(1, gitDiff.getEntries().size());

        GitParsedDiffEntry first = gitDiff.getEntries().get(0);

        Assert.assertEquals("/dev/null", first.getDiff().getFromFileName());
        Assert.assertEquals("test.csv", first.getDiff().getToFileName());


        Assert.assertEquals(GIT_COMMITS_FILTER_2.getCommitsFilter().getDateFrom(), gitDiff.getFilters().getDateFrom());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getCommitsFilter().getDateTo(), gitDiff.getFilters().getDateTo());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getCommitsFilter().getPath(), gitDiff.getFilters().getPath());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getCommitsFilter().getRef(), gitDiff.getFilters().getRef());
        Assert.assertArrayEquals(
            GIT_COMMITS_FILTER_2.getCommitsFilter().getAuthors().toArray(),
            gitDiff.getFilters().getAuthors().toArray()
        );
    }

}