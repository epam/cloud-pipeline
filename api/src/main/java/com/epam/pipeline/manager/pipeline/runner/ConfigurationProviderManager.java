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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("unchecked")
public class ConfigurationProviderManager {
    private Map<ExecutionEnvironment, ConfigurationProvider> configurationProviders;

    @Autowired
    public void setConfigurationProvider(List<ConfigurationProvider> providers) {
        if (CollectionUtils.isEmpty(providers)) {
            configurationProviders = new EnumMap<>(ExecutionEnvironment.class);
        } else {
            configurationProviders = providers.stream()
                    .collect(Collectors.toMap(ConfigurationProvider::getExecutionEnvironment, Function.identity()));
        }
    }

    public ConfigurationProvider getConfigurationProvider(AbstractRunConfigurationEntry entry) {
        return getConfigurationProviderByExecutionEnvironment(entry.getExecutionEnvironment(), entry.getName());
    }

    public boolean hasNoPermission(AbstractRunConfigurationEntry entry, String permissionName) {
        return getConfigurationProvider(entry).hasNoPermission(entry, permissionName);
    }

    public List<AbstractRunConfigurationEntry> mergeConfigurationEntry(List<AbstractRunConfigurationEntry> entries,
                                                                       List<AbstractRunConfigurationEntry> dbEntries) {
        checkAllEntriesOfTheSameType(entries);
        return getConfigurationProvider(dbEntries.get(0)).mergeConfigurationEntries(entries, dbEntries);
    }

    public void validateEntry(AbstractRunConfigurationEntry entry) {
        getConfigurationProvider(entry).validateEntry(entry);
    }

    public List<PipelineRun> runAnalysis(AnalysisConfiguration<AbstractRunConfigurationEntry> configuration) {
        checkAllEntriesOfTheSameType(configuration.getEntries());
        AbstractRunConfigurationEntry entry = configuration.getEntries().get(0);
        return getConfigurationProvider(entry).runAnalysis(configuration);
    }

    public boolean stop(Long runId, ExecutionPreferences preferences) {
        return getConfigurationProviderByExecutionEnvironment(preferences.getEnvironment(),
                preferences.getEnvironment().name())
                .stop(runId, preferences);
    }

    public void assertExecutionEnvironment(AbstractRunConfigurationEntry entry) {
        Assert.state(configurationProviders.containsKey(entry.getExecutionEnvironment()),
                "Unsupported execution environment");
    }

    private ConfigurationProvider<? extends AbstractRunConfigurationEntry, ExecutionPreferences>
        getConfigurationProviderByExecutionEnvironment(ExecutionEnvironment environment, String entryName) {
        ConfigurationProvider<? extends AbstractRunConfigurationEntry, ExecutionPreferences> provider =
                configurationProviders.get(environment);
        if (provider == null) {
            throw new IllegalArgumentException(String.format(
                    "Run configuration %s with execution environment %s is not supported.",
                    entryName, environment));
        }
        return provider;
    }

    private void checkAllEntriesOfTheSameType(List<AbstractRunConfigurationEntry> entries) {
        if (CollectionUtils.isEmpty(entries)) {
            return;
        }
        AbstractRunConfigurationEntry firstEntry = entries.get(0);
        if (entries.stream()
                .anyMatch(entry -> entry.getExecutionEnvironment() != firstEntry.getExecutionEnvironment())) {
            throw new IllegalArgumentException("All configuration entities must be of the same type.");
        }
    }
}
