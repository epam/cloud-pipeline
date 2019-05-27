/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.manager.auth;

import com.epam.pipeline.external.datastorage.controller.Result;
import com.epam.pipeline.external.datastorage.entity.PipelineToken;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PipelineAuthClient {
    @GET("restapi/proxy/token")
    Call<Result<PipelineToken>> getToken(@Query("SAMLResponse") String samlToken);
}
