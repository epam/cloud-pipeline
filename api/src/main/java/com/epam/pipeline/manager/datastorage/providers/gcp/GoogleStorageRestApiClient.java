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

package com.epam.pipeline.manager.datastorage.providers.gcp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * A Retrofit2-based client to interact with Google Storage REST API
 */
public interface GoogleStorageRestApiClient {

    String AUTH_HEADER = "Authorization";
    String CONTENT_TYPE_HEADER = "Content-Type";

    @PUT("v1/b/{bucketName}")
    Call<Object> disableLifecycleRules(@Header(AUTH_HEADER) String bearer,
                                       @Header(CONTENT_TYPE_HEADER) String contentType,
                                       @Path("bucketName") String bucketName,
                                       @Query("fields") String lifecycle,
                                       @Body String data);
}
