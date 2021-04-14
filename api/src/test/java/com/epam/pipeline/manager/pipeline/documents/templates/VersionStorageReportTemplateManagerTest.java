package com.epam.pipeline.manager.pipeline.documents.templates;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.GitDiff;
import com.epam.pipeline.entity.git.GitDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;

public class VersionStorageReportTemplateManagerTest extends AbstractSpringTest {

    public static final GitCommitsFilter GIT_COMMITS_FILTER = GitCommitsFilter
            .builder()
            .authors(Collections.singletonList("user1@test.com")
            ).build();

    public static final GitCommitsFilter GIT_COMMITS_FILTER_2 = GitCommitsFilter
            .builder()
            .authors(Collections.singletonList("user2@test.com")
            ).build();

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
                        GIT_COMMITS_FILTER
                )
        ).thenReturn(GitReaderDiff.builder()
                .filters(GIT_COMMITS_FILTER)
                .entries(
                        Collections.singletonList(
                                GitReaderDiffEntry.builder()
                                        .commit(GitReaderRepositoryCommit.builder()
                                                .author(GIT_COMMITS_FILTER.getAuthors().get(0))
                                                .authorDate(DateUtils.now())
                                                .authorEmail(GIT_COMMITS_FILTER.getAuthors().get(0))
                                                .commit("aaa111bbb222")
                                                .parentSHAs(Collections.emptyList())
                                                .build())
                                        .diff("diff --git a/test.csv b/test.csv\n" +
                                                "new file mode 100644\n" +
                                                "index 0000000..8d7862c\n" +
                                                "--- /dev/null\n" +
                                                "+++ b/test.csv\n" +
                                                "@@ -0,0 +1,2 @@\n" +
                                                "+    1,2,3" +
                                                "+    4,5,6" +
                                                "diff --git a/src/test b/src/test\n" +
                                                "index 8366d1c..0000000\n" +
                                                "Binary files a/src/test and b/src/test differ")
                                        .build())
                ).build()
        );

        Mockito.when(
                gitManager.logRepositoryCommitDiffs(
                        1L,
                        true,
                        GIT_COMMITS_FILTER_2
                )
        ).thenReturn(GitReaderDiff.builder()
                .filters(GIT_COMMITS_FILTER_2)
                .entries(
                        Collections.singletonList(
                                GitReaderDiffEntry.builder()
                                        .commit(GitReaderRepositoryCommit.builder()
                                                .author(GIT_COMMITS_FILTER_2.getAuthors().get(0))
                                                .authorDate(DateUtils.now())
                                                .authorEmail(GIT_COMMITS_FILTER_2.getAuthors().get(0))
                                                .commit("ccc111ddd222")
                                                .parentSHAs(Collections.emptyList())
                                                .build())
                                        .diff("diff --git a/test.csv b/test.csv\n" +
                                                "new file mode 100644\n" +
                                                "index 0000000..8d7862c\n" +
                                                "--- /dev/null\n" +
                                                "+++ b/test.csv\n" +
                                                "@@ -0,0 +1,2 @@\n" +
                                                "+    1,2,3" +
                                                "+    4,5,6")
                                        .build())
                ).build()
        );
    }

    @Test
    public void fetchAndNormalizeDiffWorksWithBinaryAndText() {
        final GitDiff gitDiff = reportTemplateManager.fetchAndNormalizeDiffs(1L, GIT_COMMITS_FILTER);
        Assert.assertEquals(2, gitDiff.getEntries().size());

        GitDiffEntry first = gitDiff.getEntries().get(0);
        GitDiffEntry second = gitDiff.getEntries().get(1);

        Assert.assertEquals("/dev/null", first.getDiff().getFromFileName());
        Assert.assertEquals("test.csv", first.getDiff().getToFileName());

        Assert.assertEquals("src/test", second.getDiff().getFromFileName());
        Assert.assertEquals("src/test", second.getDiff().getToFileName());

        Assert.assertEquals(GIT_COMMITS_FILTER.getDateFrom(), gitDiff.getFilters().getDateFrom());
        Assert.assertEquals(GIT_COMMITS_FILTER.getDateTo(), gitDiff.getFilters().getDateTo());
        Assert.assertEquals(GIT_COMMITS_FILTER.getPath(), gitDiff.getFilters().getPath());
        Assert.assertEquals(GIT_COMMITS_FILTER.getRef(), gitDiff.getFilters().getRef());
        Assert.assertArrayEquals(GIT_COMMITS_FILTER.getAuthors().toArray(), gitDiff.getFilters().getAuthors().toArray());
    }

    @Test
    public void fetchAndNormalizeDiffWorksWithText() {
        final GitDiff gitDiff = reportTemplateManager.fetchAndNormalizeDiffs(1L, GIT_COMMITS_FILTER_2);
        Assert.assertEquals(1, gitDiff.getEntries().size());

        GitDiffEntry first = gitDiff.getEntries().get(0);

        Assert.assertEquals("/dev/null", first.getDiff().getFromFileName());
        Assert.assertEquals("test.csv", first.getDiff().getToFileName());


        Assert.assertEquals(GIT_COMMITS_FILTER_2.getDateFrom(), gitDiff.getFilters().getDateFrom());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getDateTo(), gitDiff.getFilters().getDateTo());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getPath(), gitDiff.getFilters().getPath());
        Assert.assertEquals(GIT_COMMITS_FILTER_2.getRef(), gitDiff.getFilters().getRef());
        Assert.assertArrayEquals(GIT_COMMITS_FILTER_2.getAuthors().toArray(), gitDiff.getFilters().getAuthors().toArray());
    }

}