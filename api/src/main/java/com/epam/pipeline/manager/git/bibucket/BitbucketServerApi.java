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

import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommits;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTags;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
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

    @GET("rest/api/1.0/projects/{project}/repos/{repository}")
    Call<BitbucketRepository> getRepository(@Path(PROJECT) String project, @Path(REPOSITORY) String repository);

    @POST("rest/api/1.0/projects/{project}/repos")
    Call<BitbucketRepository> createRepository(@Path(PROJECT) String project,
                                               @Body BitbucketRepository bitbucketRepository);

    @Streaming
    @GET("rest/api/1.0/projects/{project}/repos/{repository}/raw/{path}")
    Call<ResponseBody> getFileContents(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                       @Path(value = PATH, encoded = true) String path, @Query(AT) String reference);

    @Multipart
    @PUT("rest/api/1.0/projects/{project}/repos/{repository}/browse/{path}")
    Call<ResponseBody> createFile(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                  @Path(value = PATH, encoded = true) String path, @Part MultipartBody.Part content,
                                  @Part MultipartBody.Part message);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/tags")
    Call<BitbucketTags> getTags(@Path(PROJECT) String project, @Path(REPOSITORY) String repository);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/commits")
    Call<BitbucketCommits> getCommits(@Path(PROJECT) String project, @Path(REPOSITORY) String repository);

    @GET("rest/api/1.0/projects/{project}/repos/{repository}/commits/{commitId}")
    Call<BitbucketCommit> getCommit(@Path(PROJECT) String project, @Path(REPOSITORY) String repository,
                                    @Path(COMMIT_ID) String commitId);
}
