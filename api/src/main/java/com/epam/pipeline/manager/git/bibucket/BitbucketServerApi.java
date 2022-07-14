/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.bibucket;

import com.epam.pipeline.entity.git.bitbucket.BitbucketAuthor;
import com.epam.pipeline.entity.git.bitbucket.BitbucketBranch;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketPagedResponse;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTagCreateRequest;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface BitbucketServerApi {

    String PATH = "path";
    String PROJECT = "project";
    String REPOSITORY = "repository";
    String AT = "at";
    String COMMIT_ID = "commitId";
    String USERNAME = "username";
    String TAG_NAME = "tagName";
    String LIMIT = "limit";
    String START = "start";
    String UNTIL = "until";

    @GET("rest/api/1.0/projects/{project}/repos/{repository}")
    Call<BitbucketRepository> getRepository(@Path(PROJECT) String project, @Path(REPOSITORY) String repository);

    @POST("rest/api/1.0/projects/{project}/repos")
    Call<BitbucketRepository> createRepository(@Path(PROJECT) String project,
                                               @Body BitbucketRepository bitbucketRepository);

    @PUT("rest/api/1.0/projects/{project}/repos/{repository}")
    Call<BitbucketRepository> updateRepository(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                               @Body BitbucketRepository bitbucketRepository);

    @DELETE("rest/api/1.0/projects/{project}/repos/{repository}")
    Call<BitbucketRepository> deleteRepository(@Path(PROJECT) String project, @Path(REPOSITORY) String repository);

    @GET("rest/api/1.0/users/{username}")
    Call<BitbucketAuthor> findUser(@Path(USERNAME) String username);

    @Streaming
    @GET("rest/api/1.0/projects/{project}/repos/{repository}/raw/{path}")
    Call<ResponseBody> getFileContents(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                       @Path(value = PATH, encoded = true) String path, @Query(AT) String reference);

    @Multipart
    @PUT("rest/api/1.0/projects/{project}/repos/{repository}/browse/{path}")
    Call<BitbucketCommit> createFile(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                     @Path(value = PATH, encoded = true) String path, @Part MultipartBody.Part content,
                                     @Part MultipartBody.Part message, @Part MultipartBody.Part sourceCommitId,
                                     @Part MultipartBody.Part branch);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/tags")
    Call<BitbucketPagedResponse<BitbucketTag>> getTags(@Path(PROJECT) String project,
                                                       @Path(REPOSITORY) String repository,
                                                       @Query(LIMIT) Integer limit, @Query(START) String start);

    @POST("rest/api/1.0/projects/{project}/repos/{repository}/tags")
    Call<BitbucketTag> createTag(@Path(PROJECT) String project,
                                 @Path(REPOSITORY) String repository,
                                 @Body BitbucketTagCreateRequest request);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/commits")
    Call<BitbucketPagedResponse<BitbucketCommit>> getCommits(@Path(PROJECT) String project,
                                                             @Path(REPOSITORY) String repository,
                                                             @Query(value = UNTIL, encoded = true) String ref,
                                                             @Query(LIMIT) Integer limit, @Query(START) Integer start);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/commits/{commitId}")
    Call<BitbucketCommit> getCommit(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                    @Path(COMMIT_ID) String commitId);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/tags/{tagName}")
    Call<BitbucketTag> getTag(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                              @Path(TAG_NAME) String tagName);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/files/{path}")
    Call<BitbucketPagedResponse<String>> getFiles(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                                  @Path(value = PATH, encoded = true) String path,
                                                  @Query(value = AT, encoded = true) String reference,
                                                  @Query(LIMIT) Integer limit, @Query(START) String start);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/branches")
    Call<BitbucketPagedResponse<BitbucketBranch>> getBranches(@Path(PROJECT) String project,
                                                              @Path(REPOSITORY) String repository,
                                                              @Query(LIMIT) Integer limit, @Query(START) String start);
}
