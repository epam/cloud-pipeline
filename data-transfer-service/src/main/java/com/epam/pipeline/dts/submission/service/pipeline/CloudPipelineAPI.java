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

package com.epam.pipeline.dts.submission.service.pipeline;

import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.submission.model.pipeline.PipelineRun;
import com.epam.pipeline.dts.submission.model.pipeline.RunInstance;
import com.epam.pipeline.dts.submission.model.pipeline.RunLog;
import com.epam.pipeline.dts.submission.model.pipeline.StatusUpdate;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface CloudPipelineAPI {

    String RUN_ID = "runId";

    @POST("run/{runId}/status")
    Call<Result<PipelineRun>> updateRunStatus(@Path(RUN_ID) Long runId,
                                              @Body StatusUpdate statusUpdate);

    @POST("run/{runId}/log")
    Call<Result<RunLog>> saveLogs(@Path(RUN_ID) Long runId,
                                  @Body RunLog log);

    @POST("run/{runId}/instance")
    Call<Result<PipelineRun>> updateRunInstance(@Path(RUN_ID) Long runId,
                                              @Body RunInstance instance);
}
