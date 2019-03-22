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
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.ExecutionPreferences;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface ConfigurationProvider<T extends AbstractRunConfigurationEntry, E extends ExecutionPreferences> {
    ExecutionEnvironment getExecutionEnvironment();
    boolean hasNoPermission(T entry, String permissionName);
    void validateEntry(T entry);
    List<PipelineRun> runAnalysis(AnalysisConfiguration<T> configuration);
    boolean stop(Long runId, E executionPreferences);

    default List<T> mergeConfigurationEntries(List<T> entries, List<T> dbEntries) {
        if (CollectionUtils.isEmpty(entries)) {
            return dbEntries;
        }
        Set<String> selectedEntryNames = entries.stream()
                .map(AbstractRunConfigurationEntry::getName)
                .collect(Collectors.toSet());
        return dbEntries.stream()
                .filter(entry -> selectedEntryNames.contains(entry.getName()))
                .collect(Collectors.toList());
    }
}
