/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.manager.preference;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import com.epam.pipeline.external.datastorage.message.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PreferenceService {
    private static final String JSON_FILE_EXTENSION = ".json";

    private final String preferencesPath;
    private final MessageHelper messageHelper;
    private final JsonMapper objectMapper;
    private final Set<String> apiPreferences;
    private final CloudPipelineApiExecutor apiExecutor;
    private final PreferenceClient preferenceClient;
    private final PipelineAuthManager authManager;

    public PreferenceService(@Value("${preferences.path:}") final String preferencesPath,
                             @Value("${preferences.api.keys:}") final String apiPreferences,
                             final MessageHelper messageHelper,
                             final CloudPipelineApiBuilder builder,
                             final CloudPipelineApiExecutor apiExecutor,
                             final PipelineAuthManager authManager) {
        this.preferencesPath = preferencesPath;
        this.messageHelper = messageHelper;
        this.apiPreferences = new HashSet<>(Arrays.asList(apiPreferences.split(",")));
        this.authManager = authManager;
        this.objectMapper = new JsonMapper();
        this.apiExecutor = apiExecutor;
        this.preferenceClient = builder.getClient(PreferenceClient.class);
    }

    public String getValue(final String name) {
        try {
            final Map<String, Map<String, Object>> preferences = loadAll();
            return MapUtils.emptyIfNull(preferences)
                    .values()
                    .stream()
                    .flatMap(map -> map.entrySet().stream())
                    .filter(entry -> name.equals(entry.getKey()))
                    .map(entry -> String.valueOf(entry.getValue()))
                    .findFirst()
                    .orElse(null);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getValue(final String name, final TypeReference type) {
        return parseData(getValue(name), type);
    }

    public Map<String, Map<String, Object>> loadAll() {
        final File preferencesFile = validatePreferencesFileAndGet();
        try {
            final Map<String, Map<String, Object>> values = objectMapper.readValue(preferencesFile,
                    TypeFactory.defaultInstance().constructParametricType(Map.class, String.class, Object.class));
            return addApiPreferences(values);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse preference file", e);
        }
    }

    private Map<String, Map<String, Object>> addApiPreferences(final Map<String, Map<String, Object>> values) {
        final HashMap<String, Map<String, Object>> preferences = new HashMap<>(values);
        if (CollectionUtils.isNotEmpty(apiPreferences)) {
            final List<Preference> apiPrefs = apiExecutor.execute(
                    preferenceClient.loadPreferences(authManager.getHeader()));
            ListUtils.emptyIfNull(apiPrefs).forEach(pref -> {
                if (apiPreferences.contains(pref.getName())) {
                    preferences.values().forEach(v -> v.remove(pref.getName()));
                    preferences.putIfAbsent(pref.getPreferenceGroup(), new HashMap<>());
                    preferences.get(pref.getPreferenceGroup()).put(pref.getName(), pref.getValue());
                }
            });
        }
        return preferences;
    }

    private File validatePreferencesFileAndGet() {
        Assert.isTrue(StringUtils.isNotBlank(preferencesPath),
                messageHelper.getMessage("preferences.path.not.found"));
        Assert.isTrue(StringUtils.endsWithIgnoreCase(preferencesPath, JSON_FILE_EXTENSION),
                messageHelper.getMessage("preferences.not.json.extension"));
        final Path preferencesFile = Paths.get(preferencesPath);
        Assert.isTrue(Files.exists(preferencesFile) && Files.isRegularFile(preferencesFile),
                messageHelper.getMessage("preferences.file.not.found"));
        return preferencesFile.toFile();
    }

    public <T> T parseData(final String data,
                           final TypeReference type) {
        if (StringUtils.isBlank(data)) {
            return null;
        }
        try {
            return objectMapper.readValue(data, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse json data " + data, e);
        }
    }
}
