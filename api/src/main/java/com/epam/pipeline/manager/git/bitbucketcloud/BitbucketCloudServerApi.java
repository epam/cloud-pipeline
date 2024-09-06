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

package com.epam.pipeline.manager.git.bitbucketcloud;

import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudCommit;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudPagedResponse;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRef;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRepository;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudSource;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudUser;
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

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface BitbucketCloudServerApi {

    String PATH = "path";
    String WORKSPACE = "workspace";
    String API_VERSION = "api_version";
    String REPOSITORY = "repository";
    String COMMIT = "commit";
    String COMMIT_ID = "commitId";
    String USERNAME = "username";
    String TAG_NAME = "tagName";
    String MAX_DEPTH = "max_depth";
    String PAGE = "page";
    String PAGE_LENGTH = "pagelen";
    String BRANCH = "branch";


    @GET("{api_version}/repositories/{workspace}/{repository}")
    Call<BitbucketCloudRepository> getRepository(@Path(API_VERSION) String apiVersion,
                                                 @Path(WORKSPACE) String workspace,
                                                 @Path(REPOSITORY) String repository);

    @POST("{api_version}/repositories/{workspace}/{repository}")
    Call<BitbucketCloudRepository> createRepository(@Path(API_VERSION) String apiVersion,
                                                    @Path(WORKSPACE) String workspace,
                                                    @Path(REPOSITORY) String repository,
                                                    @Body BitbucketCloudRepository bitbucketRepository);

    @PUT("{api_version}/repositories/{workspace}/{repository}")
    Call<BitbucketCloudRepository> updateRepository(@Path(API_VERSION) String apiVersion,
                                                    @Path(WORKSPACE) String workspace,
                                                    @Path(REPOSITORY) String repository,
                                                    @Body BitbucketCloudRepository bitbucketRepository);

    @DELETE("{api_version}/repositories/{workspace}/{repository}")
    Call<Boolean> deleteRepository(@Path(API_VERSION) String apiVersion,
                                   @Path(WORKSPACE) String workspace,
                                   @Path(REPOSITORY) String repository);

    @GET("{api_version}/users/{username}")
    Call<BitbucketCloudUser> findUser(@Path(API_VERSION) String apiVersion,
                                      @Path(USERNAME) String username);

    @Streaming
    @GET("{api_version}/repositories/{workspace}/{repository}/src/{commit}/{path}")
    Call<ResponseBody> getFileContents(@Path(API_VERSION) String apiVersion,
                                       @Path(WORKSPACE) String workspace,
                                       @Path(REPOSITORY) String repository,
                                       @Path(value = PATH, encoded = true) String path,
                                       @Path(value = COMMIT) String commit);

    @Multipart
    @POST("{api_version}/repositories/{workspace}/{repository}/src")
    Call<ResponseBody> createFile(@Path(API_VERSION) String apiVersion,
                                  @Path(WORKSPACE) String workspace,
                                  @Path(REPOSITORY) String repository,
                                  @Part MultipartBody.Part file,
                                  @Part MultipartBody.Part message,
                                  @Part MultipartBody.Part branch);

    @GET("{api_version}/repositories/{workspace}/{repository}/refs/tags")
    Call<BitbucketCloudPagedResponse<BitbucketCloudRef>> getTags(@Path(API_VERSION) String apiVersion,
                                                                 @Path(WORKSPACE) String workspace,
                                                                 @Path(REPOSITORY) String repository,
                                                                 @Query(PAGE) Integer page,
                                                                 @Query(PAGE_LENGTH) Integer pageLength);

    @POST("{api_version}/repositories/{workspace}/{repository}/refs/tags")
    Call<BitbucketCloudRef> createTag(@Path(API_VERSION) String apiVersion,
                                      @Path(WORKSPACE) String workspace,
                                      @Path(REPOSITORY) String repository,
                                      @Body BitbucketCloudRef tag);

    @GET("{api_version}/repositories/{workspace}/{repository}/commits")
    Call<BitbucketCloudPagedResponse<BitbucketCloudCommit>> getCommits(@Path(API_VERSION) String apiVersion,
                                                                       @Path(WORKSPACE) String workspace,
                                                                       @Path(REPOSITORY) String repository,
                                                                       @Query(PAGE) Integer page,
                                                                       @Query(PAGE_LENGTH) Integer pageLength);

    @GET("{api_version}/repositories/{workspace}/{repository}/commit/{commitId}")
    Call<BitbucketCloudCommit> getCommit(@Path(API_VERSION) String apiVersion,
                                         @Path(WORKSPACE) String workspace,
                                         @Path(REPOSITORY) String repository,
                                         @Path(COMMIT_ID) String commitId);

    @GET("{api_version}/repositories/{workspace}/{repository}/refs/tags/{tagName}")
    Call<BitbucketCloudRef> getTag(@Path(API_VERSION) String apiVersion,
                                   @Path(WORKSPACE) String workspace,
                                   @Path(REPOSITORY) String repository,
                                   @Path(TAG_NAME) String tagName);

    @GET("{api_version}/repositories/{workspace}/{repository}/src/{commit}/{path}")
    Call<BitbucketCloudPagedResponse<BitbucketCloudSource>> getFiles(@Path(API_VERSION) String apiVersion,
                                                                     @Path(WORKSPACE) String workspace,
                                                                     @Path(REPOSITORY) String repository,
                                                                     @Path(value = PATH) String path,
                                                                     @Path(value = COMMIT) String commit,
                                                                     @Query(PAGE) String page,
                                                                     @Query(MAX_DEPTH) Integer maxDepth);

    @GET("{api_version}/repositories/{workspace}/{repository}/refs/branches")
    Call<BitbucketCloudPagedResponse<BitbucketCloudRef>> getBranches(@Path(API_VERSION) String apiVersion,
                                                                     @Path(WORKSPACE) String workspace,
                                                                     @Path(REPOSITORY) String repository,
                                                                     @Query(PAGE) Integer page,
                                                                     @Query(PAGE_LENGTH) Integer pageLength);

    @GET("{api_version}/repositories/{workspace}/{repository}/commits/{branch}")
    Call<BitbucketCloudPagedResponse<BitbucketCloudCommit>> search(@Path(API_VERSION) String apiVersion,
                                                                   @Path(WORKSPACE) String workspace,
                                                                   @Path(REPOSITORY) String repository,
                                                                   @Path(BRANCH) String branch,
                                                                   @Query(value = PATH, encoded = true) String path);
}
