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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.exception.DtsRequestException;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.PUT;

import java.io.IOException;
import java.util.Objects;

public interface DtsClient {

    @GET("list")
    Call<Result<DtsDataStorageListing>> getList(@Query("path") String path,
                                                @Query("pageSize") Integer pageSize,
                                                @Query("marker") String marker,
                                                @Query("user") String user);

    @POST("submission")
    Call<Result<DtsSubmission>> createSubmission(@Body DtsSubmission submission);


    @GET("submission")
    Call<Result<DtsSubmission>> findSubmission(@Query("runId") Long runId);

    @GET("cluster")
    Call<Result<DtsClusterConfiguration>> getClusterConfiguration();

    @PUT("submission/stop")
    Call<Result<DtsSubmission>> stopSubmission(@Query("runId") Long runId);

    static  <T> T executeRequest(Call<T> request) {
        try {
            Response<T> response = request.execute();
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body());
            } else {
                throw new DtsRequestException(
                        String.format("Request to Data Transfer Service returned status %s with message %s",
                                response.code(), response.errorBody() == null ? "" : response.errorBody().string()));
            }
        } catch (IOException e) {
            throw new DtsRequestException(e);
        }
    }

}

