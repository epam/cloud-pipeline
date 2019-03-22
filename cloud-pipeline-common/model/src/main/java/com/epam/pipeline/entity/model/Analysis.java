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

package com.epam.pipeline.entity.model;

import lombok.Builder;
import lombok.Value;

/**
 * Model analysis.
 *
 * It stores analysis current status and its result.
 */
@Value
@Builder
public class Analysis {
    private final AnalysisStatus status;
    private final AnalysisResult result;

    public static Analysis success(final AnalysisResult analysisResult) {
        return new Analysis(AnalysisStatus.FINISHED, analysisResult);
    }

    public static Analysis failed() {
        return new Analysis(AnalysisStatus.FAILED, AnalysisResult.empty());
    }

    public static Analysis running() {
        return new Analysis(AnalysisStatus.RUNNING, AnalysisResult.empty());
    }
}
