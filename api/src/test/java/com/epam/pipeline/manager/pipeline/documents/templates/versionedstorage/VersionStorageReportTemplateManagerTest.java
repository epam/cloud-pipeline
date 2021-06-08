package com.epam.pipeline.manager.pipeline.documents.templates.versionedstorage;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.entity.git.report.GitDiffGroupType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;

public class VersionStorageReportTemplateManagerTest extends AbstractSpringTest {

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

    @Autowired
    private VersionStorageReportTemplateManager reportTemplateManager;

    @Before
    public void setup() {
        Pipeline mockPipeline = new Pipeline();
        mockPipeline.setName("pipeline");
        mockPipeline.setId(1L);

        Mockito.when(
                pipelineManager.load(1L)
        ).thenReturn(mockPipeline);

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
        Assert.assertArrayEquals(GIT_COMMITS_FILTER.getCommitsFilter().getAuthors().toArray(), gitDiff.getFilters().getAuthors().toArray());
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
        Assert.assertArrayEquals(GIT_COMMITS_FILTER_2.getCommitsFilter().getAuthors().toArray(), gitDiff.getFilters().getAuthors().toArray());
    }

}