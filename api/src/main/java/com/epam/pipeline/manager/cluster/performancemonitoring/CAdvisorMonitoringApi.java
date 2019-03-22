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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.entity.cluster.monitoring.RawContainerStats;
import com.epam.pipeline.entity.cluster.monitoring.RawMonitoringStats;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface CAdvisorMonitoringApi {

    @GET("/api/v1.3/containers/")
    Call<RawMonitoringStats> getStatsForNode();

    @GET("/api/v2.0/stats/{containerId}?type=docker")
    Call<RawContainerStats> getStatsForContainer(@Path("containerId") String containerId);
}


