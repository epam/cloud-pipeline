/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.github;

import com.epam.pipeline.entity.git.github.GitHubCommitNode;
import com.epam.pipeline.entity.git.github.GitHubContent;
import com.epam.pipeline.entity.git.github.GitHubRef;
import com.epam.pipeline.entity.git.github.GitHubRelease;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.git.github.GitHubSource;
import com.epam.pipeline.entity.git.github.GitHubTag;
import com.epam.pipeline.entity.git.github.GitHubTagRequest;
import com.epam.pipeline.entity.git.github.GitHubTree;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface GitHubApi {

    String PATH = "path";
    String WORKSPACE = "workspace";
    String REPOSITORY = "repository";
    String COMMIT_ID = "commitId";
    String TAG_NAME = "tagName";
    String PAGE = "page";
    String PAGE_LENGTH = "per_page";


    @GET("repos/{workspace}/{repository}")
    Call<GitHubRepository> getRepository(@Path(WORKSPACE) String workspace,
                                         @Path(REPOSITORY) String repository);

    @POST("repos/{workspace}/{repository}")
    Call<GitHubRepository> createRepository(@Path(WORKSPACE) String workspace,
                                            @Path(REPOSITORY) String repository,
                                            @Body GitHubRepository bitbucketRepository);
    @PATCH("repos/{workspace}/{repository}")
    Call<GitHubRepository> updateRepository(@Path(WORKSPACE) String workspace,
                                            @Path(REPOSITORY) String repository,
                                            @Body GitHubRepository bitbucketRepository);

    @DELETE("repos/{workspace}/{repository}")
    Call<Boolean> deleteRepository(@Path(WORKSPACE) String workspace,
                                   @Path(REPOSITORY) String repository);

    @PUT("repos/{workspace}/{repository}/contents/{path}")
    Call<GitHubSource> createFile(@Path(WORKSPACE) String workspace,
                                  @Path(REPOSITORY) String repository,
                                  @Path(value = PATH, encoded = true) String path,
                                  @Body GitHubContent request);

    @HTTP(method = "DELETE", path = "repos/{workspace}/{repository}/contents/{path}", hasBody = true)
    Call<GitHubSource> deleteFile(@Path(WORKSPACE) String workspace,
                                  @Path(REPOSITORY) String repository,
                                  @Path(value = PATH, encoded = true) String path,
                                  @Body GitHubContent request);

    @GET("repos/{workspace}/{repository}/releases")
    Call<List<GitHubRelease>> getTags(@Path(WORKSPACE) String workspace,
                                      @Path(REPOSITORY) String repository,
                                      @Query(PAGE) Integer page,
                                      @Query(PAGE_LENGTH) Integer pageLength);

    @POST("repos/{workspace}/{repository}/git/tags")
    Call<GitHubTag> createTag(@Path(WORKSPACE) String workspace,
                              @Path(REPOSITORY) String repository,
                              @Body GitHubTagRequest tag);

    @POST("repos/{workspace}/{repository}/git/refs")
    Call<GitHubRef> createRef(@Path(WORKSPACE) String workspace,
                              @Path(REPOSITORY) String repository,
                              @Body GitHubRef ref);
    @POST("repos/{workspace}/{repository}/releases")
    Call<GitHubRelease> createRelease(@Path(WORKSPACE) String workspace,
                                      @Path(REPOSITORY) String repository,
                                      @Body GitHubRelease release);

    @GET("repos/{workspace}/{repository}/commits")
    Call<List<GitHubCommitNode>> getCommits(@Path(WORKSPACE) String workspace,
                                            @Path(REPOSITORY) String repository,
                                            @Query(PAGE) Integer page,
                                            @Query(PAGE_LENGTH) Integer pageLength);

    @GET("repos/{workspace}/{repository}/commits/{commitId}")
    Call<GitHubCommitNode> getCommit(@Path(WORKSPACE) String workspace,
                                    @Path(REPOSITORY) String repository,
                                    @Path(COMMIT_ID) String commitId);

    @GET("repos/{workspace}/{repository}/releases/tags/{tagName}")
    Call<GitHubRelease> getTag(@Path(WORKSPACE) String workspace,
                               @Path(REPOSITORY) String repository,
                               @Path(TAG_NAME) String tagName);
    @GET("repos/{workspace}/{repository}/contents/{path}")
    Call<List<GitHubContent>> getContents(@Path(WORKSPACE) String workspace,
                                          @Path(REPOSITORY) String repository,
                                          @Path(value = PATH) String path,
                                          @Query("ref") String commit);
    @GET("repos/{workspace}/{repository}/git/trees/{sha}")
    Call<GitHubTree> getTree(@Path(WORKSPACE) String workspace,
                             @Path(REPOSITORY) String repository,
                             @Path(value = "sha") String sha,
                             @Query("recursive") Boolean recursive);

    @GET("repos/{workspace}/{repository}/contents/{path}")
    Call<GitHubContent> getFile(@Path(WORKSPACE) String workspace,
                                @Path(REPOSITORY) String repository,
                                @Path(value = PATH) String path,
                                @Query("ref") String commit);

    @GET("repos/{workspace}/{repository}/branches")
    Call<List<GitHubRef>> getBranches(@Path(WORKSPACE) String workspace,
                                      @Path(REPOSITORY) String repository,
                                      @Query(PAGE) Integer page,
                                      @Query(PAGE_LENGTH) Integer pageLength);
}
