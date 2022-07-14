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

import com.epam.pipeline.external.datastorage.message.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class PreferenceService {
    private static final String JSON_FILE_EXTENSION = ".json";

    private final String preferencesPath;
    private final MessageHelper messageHelper;

    public PreferenceService(@Value("${preferences.path:}") final String preferencesPath,
                             final MessageHelper messageHelper) {
        this.preferencesPath = preferencesPath;
        this.messageHelper = messageHelper;
    }

    public Map<String, Map<String, Object>> loadAll() {
        final File preferencesFile = validatePreferencesFileAndGet();
        final JsonMapper objectMapper = new JsonMapper();

        try {
            return objectMapper.readValue(preferencesFile, TypeFactory.defaultInstance()
                    .constructParametricType(Map.class, String.class, Object.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse preference file", e);
        }
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
}
