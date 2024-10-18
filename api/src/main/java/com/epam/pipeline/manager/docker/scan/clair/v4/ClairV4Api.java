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

package com.epam.pipeline.manager.docker.scan.clair.v4;

import com.epam.pipeline.entity.scan.clair.v4.ClairIndexReport;
import com.epam.pipeline.entity.scan.clair.v4.ClairIndexRequest;
import com.epam.pipeline.entity.scan.clair.v4.ClairVulnerabilityReport;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ClairV4Api {

    @POST("indexer/api/v1/index_report")
    Call<ClairIndexReport> scanLayer(@Body ClairIndexRequest request);

    @GET("indexer/api/v1/index_report/{layer}")
    Call<ClairIndexReport> getIndexReport(@Path("layer") String layer);

    @GET("matcher/api/v1/vulnerability_report/{layer}")
    Call<ClairVulnerabilityReport> getVulnerabilitiesReport(@Path("layer") String layer);
}
