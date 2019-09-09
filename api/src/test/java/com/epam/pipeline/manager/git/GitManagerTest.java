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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitFile;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.entity.git.GitlabUser;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.exception.RuntimeIOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.manager.git.GitManager.DRAFT_PREFIX;
import static com.epam.pipeline.manager.git.GitManager.GIT_MASTER_REPOSITORY;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static java.util.Comparator.reverseOrder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class GitManagerTest extends AbstractManagerTest {
    private static final String TEST_REVISION = "v1.0.0";
    private static final int ROOT_USER_ID = 42;
    private static final String ROOT_USER_NAME = "root";
    private static final String REPOSITORY_NAME = "repository";
    private static final String PROJECT_PATH = urlEncoded(ROOT_USER_NAME + "/" + REPOSITORY_NAME);
    private static final String REPOSITORY_COMMITS = "/repository/commits";
    private static final String REPOSITORY_FILES = "/repository/files";
    private static final String REPOSITORY_TAGS = "/repository/tags";
    private static final String REPOSITORY_TREE = "/repository/tree";
    private static final String REF_NAME = "ref_name";
    private static final String PATH = "path";
    private static final String RECURSIVE = "recursive";
    private static final String FILE_PATH = "file_path";
    private static final String REF = "ref";
    private static final String TAG_NAME = "tag_name";
    private static final String DOCS = "docs";
    private static final String README_FILE = "README.md";
    private static final String BLOB_TYPE = "blob";
    private static final String FILE_CONTENT = "some content";
    private static final GitlabUser USER = GitlabUser.builder().id(1L).username("root").build();
    private static final GitToken USER_TOKEN = GitToken.builder().id(1L).token("token-123").expires(new Date()).build();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private GitRepositoryUrl gitHost;

    @Mock
    private PipelineManager pipelineManagerMock;

    @SpyBean
    private PreferenceManager preferenceManager;

    @Mock
    private CmdExecutor executor;

    @Value("${working.directory}")
    private String workingDirPath;

    @Autowired
    @InjectMocks
    private GitManager gitManager;

    @Before
    public void describePreferenceManager() {
        when(preferenceManager.getPreference(SystemPreferences.GIT_HOST)).thenReturn(gitHost.asString());
        when(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID)).thenReturn(ROOT_USER_ID);
        when(preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME)).thenReturn(ROOT_USER_NAME);
    }

    @Before
    public void describeCmdExecutor() {
        Whitebox.setInternalState(gitManager, "cmdExecutor", executor);
        when(executor.executeCommand(anyString(), any(), any(), anyBoolean())).thenReturn("inconsiderable output");
    }

    @Before
    public void describePipelineManager() {
        final Pipeline pipeline = testingPipeline();
        when(pipelineManagerMock.load(pipeline.getId())).thenReturn(pipeline);
        when(pipelineManagerMock.load(eq(pipeline.getId()), anyBoolean())).thenReturn(pipeline);
    }

    @Before
    public void createWorkingDirectory() throws IOException {
        if (Files.notExists(Paths.get(workingDirPath))) {
            Files.createDirectory(Paths.get(workingDirPath));
        }
    }

    @After
    public void removeWorkingDirectory() throws IOException {
        Files.walk(Paths.get(workingDirPath))
             .map(Path::toFile)
             .sorted(reverseOrder())
             .forEach(File::delete);
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this.getClass());
        gitHost = GitRepositoryUrl.from("http://localhost:" + wireMockRule.port());
        givenThat(
                get(urlPathEqualTo("/api/v3/users"))
                        .willReturn(okJson(with(singletonList(USER))))
        );
        givenThat(
                post(urlPathEqualTo("/api/v3/users/1/impersonation_tokens"))
                        .willReturn(okJson(with(USER_TOKEN)))
        );
    }

    @Test
    public void shouldReturnTrueWhenProjectExists() {
        final GitProject project = new GitProject();
        project.setRepoUrl(gitHost.withNamespace(ROOT_USER_NAME).withProject(REPOSITORY_NAME).asString());
        givenThat(
            get(urlPathEqualTo(api()))
                .willReturn(okJson(with(project)))
        );

        final boolean projectExists = gitManager.checkProjectExists(REPOSITORY_NAME);
        assertTrue(projectExists);
    }


    @Test
    public void shouldReturnFalseWhenProjectDoesNotExists() {
        givenThat(
            get(urlPathEqualTo(api()))
                .willReturn(notFound())
        );

        final boolean projectExists = gitManager.checkProjectExists(REPOSITORY_NAME);
        assertFalse(projectExists);
    }

    @Test
    public void shouldNotFailWhenCreateFile() throws GitClientException {
        mockFileContentRequest(DOCS + "/created_file.txt", GIT_MASTER_REPOSITORY, FILE_CONTENT);
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final Pipeline pipeline = testingPipeline();
        final GitCommitEntry resultingCommit = gitManager.updateFile(
            pipeline,
            DOCS + "/created_file.txt",
            FILE_CONTENT,
            "last commit id doesn't matter",
            "Create file"
        );
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldDeleteFile() throws GitClientException {
        final Revision revision = new Revision("Initial commit", "", new Date(), "doesn't matter");
        final Pipeline pipeline = testingPipeline();
        pipeline.setCurrentVersion(revision);
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final GitCommitEntry resultingCommit = gitManager.deleteFile(
            pipeline, DOCS + "/" + README_FILE, pipeline.getCurrentVersion().getCommitId(), "Delete file"
        );
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void getConfigFile() {
        final Pipeline pipeline = testingPipeline();
        final String sha = "somecommitsha";
        final Revision revision = new Revision("Initial commit", "", new Date(), DRAFT_PREFIX + sha);
        pipeline.setCurrentVersion(revision);
        final GitCommitEntry initialCommit = new GitCommitEntry();
        initialCommit.setMessage("New pipeline initial commit");
        initialCommit.setCreatedAt("2017-07-25T13:13:11Z");
        initialCommit.setId(sha);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_COMMITS + "/" + initialCommit.getId())))
                .willReturn(okJson(with(initialCommit)))
        );
        final GitTagEntry tag = new GitTagEntry();
        tag.setName(TEST_REVISION);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TAGS + "/" + tag.getName())))
                .willReturn(okJson(with(tag)))
        );
        final File file = gitManager.getConfigFile(pipeline, pipeline.getCurrentVersion().getCommitId());
        assertThat(file.getParentFile().exists(), is(true));
    }

    @Test
    public void shouldFetchRevision() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final long pageSize = 1;
        final List<GitTagEntry> tags = Collections.emptyList();
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TAGS)))
                .willReturn(okJson(with(tags)))
        );
        final GitCommitEntry initialCommit = new GitCommitEntry();
        initialCommit.setMessage("New pipeline initial commit");
        initialCommit.setCreatedAt("2017-07-25T13:13:11Z");
        final List<GitCommitEntry> commits = singletonList(initialCommit);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_COMMITS)))
                .willReturn(okJson(with(commits)))
        );
        final List<Revision> revisions = gitManager.getPipelineRevisions(pipeline, pageSize);
        assertFalse(revisions.isEmpty());
    }

    @Test
    public void shouldFetchPipelineDocs() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final GitRepositoryEntry bla = new GitRepositoryEntry();
        bla.setName(README_FILE);
        bla.setType(BLOB_TYPE);
        final List<GitRepositoryEntry> tree = singletonList(bla);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TREE)))
                .withQueryParam(REF, equalTo(TEST_REVISION))
                .withQueryParam(PATH, equalTo(DOCS + "/"))
                .withQueryParam(RECURSIVE, equalTo(String.valueOf(false)))
                .willReturn(okJson(with(tree)))
        );
        final GitTagEntry tag = new GitTagEntry();
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TAGS + "/" + TEST_REVISION)))
                .willReturn(okJson(with(tag)))
        );
        final List<GitRepositoryEntry> repoEntries = gitManager.getPipelineDocs(pipeline.getId(), TEST_REVISION);
        final boolean noEntries = repoEntries.isEmpty();
        final boolean docsOnly = repoEntries.stream()
                                            .filter(e -> !e.getName().startsWith("."))
                                            .allMatch(e -> e.getName().endsWith(".md"));
        assertFalse(noEntries);
        assertTrue(docsOnly);
    }

    @Test
    public void shouldFetchPipelineSourceFile() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final String fileName = "src/test.py";
        mockFileContentRequest(fileName, TEST_REVISION, FILE_CONTENT);
        final byte[] fileContents = gitManager.getPipelineFileContents(pipeline, TEST_REVISION, fileName);
        final String text = new String(fileContents);
        assertTrue(StringUtils.isNotBlank(text));
    }

    @Test
    public void getPipelineRevision() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final GitTagEntry tag = new GitTagEntry();
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TAGS + "/" + TEST_REVISION)))
                .willReturn(okJson(with(tag)))
        );
        final GitTagEntry revision = gitManager.loadRevision(pipeline, TEST_REVISION);
        assertNotNull(revision);
    }

    @Test
    public void shouldLoadRevisionWhenItIsDraft() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final GitTagEntry tag = new GitTagEntry();
        final String sha = "somecommitsha";
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_COMMITS + "/" + sha)))
                .willReturn(okJson(with(tag)))
        );
        final GitTagEntry revision = gitManager.loadRevision(pipeline, DRAFT_PREFIX + sha);
        assertNotNull(revision);
    }

    @Test
    public void shouldRenameFile() throws GitClientException, UnsupportedEncodingException {
        final Pipeline pipeline = testingPipeline();
        final UploadFileMetadata file = new UploadFileMetadata();
        final byte[] readmeContent = "Some inconsiderable content".getBytes("UTF-8");
        file.setFileName(README_FILE);
        file.setFileSize(readmeContent.length / 1024 + " Kb");
        file.setFileType("text/markdown; charset=UTF-8");
        file.setBytes(readmeContent);
        final GitCommitEntry expectedCommit = new GitCommitEntry();
        expectedCommit.setMessage("Rename the file");
        expectedCommit.setCreatedAt("2017-07-25T13:13:11Z");
        givenThat(
            post(urlPathEqualTo(api(REPOSITORY_COMMITS)))
                .willReturn(okJson(with(expectedCommit)))
        );
        mockFileContentRequest(DOCS + "/" + README_FILE, GIT_MASTER_REPOSITORY, FILE_CONTENT);
        final GitCommitEntry resultingCommit = gitManager.uploadFiles(
            pipeline, DOCS, singletonList(file), pipeline.getCurrentVersion().getCommitId(), "Rename the file"
        );
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldRemoveFolder() throws GitClientException {
        final GitRepositoryEntry repositoryEntry = new GitRepositoryEntry();
        repositoryEntry.setName(README_FILE);
        repositoryEntry.setType(BLOB_TYPE);
        final List<GitRepositoryEntry> tree = singletonList(repositoryEntry);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TREE)))
                .withQueryParam(REF, equalTo(GIT_MASTER_REPOSITORY))
                .withQueryParam(PATH, equalTo(DOCS))
                .withQueryParam(RECURSIVE, equalTo(String.valueOf(true)))
                .willReturn(okJson(with(tree)))
        );
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final Pipeline pipeline = testingPipeline();
        final GitCommitEntry resultingCommit = gitManager.removeFolder(
            pipeline, DOCS, pipeline.getCurrentVersion().getCommitId(), "Remove the folder"
        );
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldUpdateFiles() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final String lastCommit = pipeline.getCurrentVersion().getCommitId();
        final PipelineSourceItemVO bla = new PipelineSourceItemVO();
        bla.setLastCommitId(lastCommit);
        bla.setContents(FILE_CONTENT);
        bla.setComment("Update some file");
        bla.setPath(DOCS + "/" + README_FILE);
        bla.setPreviousPath(DOCS + "/" + README_FILE);
        final PipelineSourceItemsVO sourceItemVOList = new PipelineSourceItemsVO();
        sourceItemVOList.setLastCommitId(lastCommit);
        sourceItemVOList.setItems(singletonList(bla));
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final GitCommitEntry resultingCommit = gitManager.updateFiles(pipeline, sourceItemVOList);
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldFetchConfigFileContent() throws GitClientException {
        mockGitCommitRequest();
        final GitTagEntry tag = new GitTagEntry();
        tag.setName(TEST_REVISION);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TAGS + "/" + tag.getName())))
                .willReturn(okJson(with(tag)))
        );
        mockFileContentRequest("config.json", tag.getName(), FILE_CONTENT);
        final Pipeline pipeline = testingPipeline();
        final String fileContent = gitManager.getConfigFileContent(pipeline, pipeline.getCurrentVersion().getName());
        assertThat(fileContent, not(isEmptyString()));
    }

    @Test
    @Ignore
    public void shouldRenameFolder() throws GitClientException {
        final String blaFilePath = DOCS + "/" + README_FILE;
        final Pipeline pipeline = testingPipeline();
        final PipelineSourceItemVO folder = new PipelineSourceItemVO();
        folder.setPreviousPath(DOCS);
        folder.setPath("doc");
        folder.setLastCommitId(pipeline.getCurrentVersion().getCommitId());
        final GitRepositoryEntry bla = new GitRepositoryEntry();
        bla.setName(README_FILE);
        bla.setType(BLOB_TYPE);
        bla.setPath(blaFilePath);
        final List<GitRepositoryEntry> tree = singletonList(bla);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TREE)))
                .withQueryParam(REF_NAME, equalTo(GIT_MASTER_REPOSITORY))
                .withQueryParam(PATH, equalTo(DOCS))
                .willReturn(okJson(with(tree)))
        );
        mockFileContentRequest(DOCS + File.separator + ".gitkeep", GIT_MASTER_REPOSITORY, FILE_CONTENT);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_FILES)))
                .withQueryParam(FILE_PATH, equalTo("doc" + File.separator + ".gitkeep"))
                .withQueryParam(REF, equalTo(GIT_MASTER_REPOSITORY))
                .willReturn(notFound())
        );
        mockFileContentRequest(blaFilePath, GIT_MASTER_REPOSITORY, FILE_CONTENT);
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final GitCommitEntry resultingCommit = gitManager.createOrRenameFolder(pipeline.getId(), folder);
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldCreateFolder() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final PipelineSourceItemVO folder = new PipelineSourceItemVO();
        folder.setPath(DOCS);
        folder.setLastCommitId(pipeline.getCurrentVersion().getCommitId());
        final GitRepositoryEntry bla = new GitRepositoryEntry();
        bla.setName(README_FILE);
        bla.setType(BLOB_TYPE);
        bla.setPath(DOCS + "/" + README_FILE);
        final List<GitRepositoryEntry> tree = singletonList(bla);
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_TREE)))
                .withQueryParam(REF_NAME, equalTo(GIT_MASTER_REPOSITORY))
                .withQueryParam(PATH, equalTo(DOCS))
                .willReturn(okJson(with(tree)))
        );
        mockFileContentRequest(DOCS + File.separator + ".gitkeep", GIT_MASTER_REPOSITORY, FILE_CONTENT);
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final GitCommitEntry resultingCommit = gitManager.createOrRenameFolder(pipeline.getId(), folder);
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    @Ignore
    public void shouldModifyFile() throws GitClientException {
        final GitCommitEntry expectedCommit = mockGitCommitRequest();
        final String filePath = DOCS + "/" + README_FILE;
        mockFileContentRequest(filePath, GIT_MASTER_REPOSITORY, FILE_CONTENT);
        final Pipeline pipeline = testingPipeline();
        final String lastCommit = pipeline.getCurrentVersion().getCommitId();
        final PipelineSourceItemVO file = new PipelineSourceItemVO();
        file.setLastCommitId(lastCommit);
        file.setContents(FILE_CONTENT);
        file.setComment("Update some file");
        file.setPath(filePath);
        file.setPreviousPath(filePath);
        final GitCommitEntry resultingCommit = gitManager.modifyFile(pipeline, file);
        assertThat(resultingCommit, is(expectedCommit));
    }

    @Test
    public void shouldCreatePipelineRevision() throws GitClientException {
        final String sha = "anothercommitsha";
        final GitCommitEntry commit = new GitCommitEntry();
        commit.setId(sha);
        commit.setAuthoredDate("2017-07-26T13:13:11Z");
        final GitTagEntry tag = new GitTagEntry();
        final String tagName = "v1.0.1";
        tag.setName(tagName);
        tag.setCommit(commit);
        givenThat(
            post(urlPathEqualTo(api(REPOSITORY_TAGS)))
                .withQueryParam(REF, equalTo(sha))
                .withQueryParam(TAG_NAME, equalTo(tagName))
                .willReturn(created().withHeader(CONTENT_TYPE, "application/json").withBody(with(tag)))
        );
        final Pipeline pipeline = testingPipeline();
        final Revision revision = gitManager.createPipelineRevision(
            pipeline, tagName, sha, "Message", "Release description"
        );
        assertThat(revision.getName(), is(tag.getName()));
        assertThat(revision.getMessage(), is(tag.getMessage()));
    }

    @Test
    public void shouldGetCommits() throws GitClientException {
        final Pipeline pipeline = testingPipeline();
        final GitCommitEntry expectedCommit = new GitCommitEntry();
        givenThat(
            get(urlPathEqualTo(api(REPOSITORY_COMMITS)))
                .withQueryParam(REF_NAME, equalTo(pipeline.getCurrentVersion().getCommitId()))
                .willReturn(okJson(with(singletonList(expectedCommit))))
        );
        final List<GitCommitEntry> commits = gitManager.getCommits(
            pipeline, pipeline.getCurrentVersion().getCommitId()
        );
        assertThat(commits, contains(expectedCommit));
    }

    /**
     * We suppress checkstyle warning since this method is for creating dummy data that actually doesn't matter.
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    private Pipeline testingPipeline() {
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(1L);
        pipeline.setRepository(gitHost.withNamespace(ROOT_USER_NAME).withProject(REPOSITORY_NAME).asString());
        final Date date = Date.from(ZonedDateTime.of(2018, 6, 28, 14, 30, 0, 0, UTC).toInstant());
        final Revision revision = new Revision(TEST_REVISION, "Initial commit", date, "somecommitsha");
        pipeline.setCurrentVersion(revision);
        return pipeline;
    }

    /**
     * @return JSON representation of the passed {@code object}.
     */
    @SneakyThrows
    private static String with(final Object object) {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(object);
    }

    /**
     * An exception is actually never thrown since we know that "UTF-8" is a valid encoding.
     */
    @SneakyThrows
    private static String urlEncoded(final String string) {
        return URLEncoder.encode(string, "UTF-8");
    }

    private static String api() {
        return "/api/v3/projects/" + PROJECT_PATH;
    }

    private static String api(final String url) {
        return api() + url;
    }

    private void mockFileContentRequest(final String filePath, final String ref, final String content) {
        final GitFile gitFile = new GitFile();
        gitFile.setContent(Base64.getEncoder().encodeToString(content.getBytes()));
        givenThat(
                get(urlPathEqualTo(api(REPOSITORY_FILES + "/" + encodeUrlPath(filePath))))
                        .withQueryParam(REF, equalTo(ref))
                        .willReturn(okJson(with(gitFile)))
        );
    }

    private String encodeUrlPath(final String filePath) {
        try {
            return URLEncoder.encode(filePath, "UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeIOException(e.getMessage());
        }
    }

    private GitCommitEntry mockGitCommitRequest() {
        final GitCommitEntry expectedCommit = new GitCommitEntry();
        givenThat(
                post(urlPathEqualTo(api(REPOSITORY_COMMITS)))
                        .willReturn(okJson(with(expectedCommit)))
        );
        return expectedCommit;
    }
}
