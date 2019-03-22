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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.PipelineRun;

import java.util.List;

/**
 * Runner supporting one of {@link com.epam.pipeline.entity.configuration.ExecutionEnvironment}
 * providing interface for actual analysis run
 */
public interface ExecutionRunner<T extends AbstractRunConfigurationEntry> {

    List<PipelineRun> runAnalysis(AnalysisConfiguration<T> configuration);
}
