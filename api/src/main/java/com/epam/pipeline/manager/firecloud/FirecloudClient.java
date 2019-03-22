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

package com.epam.pipeline.manager.firecloud;

import com.epam.pipeline.entity.firecloud.FirecloudInputsOutputs;
import com.epam.pipeline.entity.firecloud.FirecloudMethodConfiguration;
import com.epam.pipeline.entity.firecloud.FirecloudMethodWDL;
import com.epam.pipeline.entity.firecloud.FirecloudRawMethod;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.util.List;

/**
 * A Retrofit2-based client to interact with Firecloud API
 */
public interface FirecloudClient {

    String AUTH_HEADER = "Authorization";

    @GET("methods")
    Call<List<FirecloudRawMethod>> getMethods(@Header(AUTH_HEADER) String bearer);

    @GET("methods/{workspace}/{method}/{snapshot}")
    Call<FirecloudMethodWDL> getMethod(@Header(AUTH_HEADER) String bearer,
                                       @Path("workspace") String workspace,
                                       @Path("method") String method,
                                       @Path("snapshot") Long snapshot);

    @GET("methods/{workspace}/{method}/configurations")
    Call<List<FirecloudMethodConfiguration>> getConfigurations(@Header(AUTH_HEADER) String bearer,
                                                               @Path("workspace") String workspace,
                                                               @Path("method") String method);


    @POST("inputsOutputs")
    Call<FirecloudInputsOutputs> getInputsOutputs(@Header(AUTH_HEADER) String bearer,
                                                  @Body RequestBody request);
}
