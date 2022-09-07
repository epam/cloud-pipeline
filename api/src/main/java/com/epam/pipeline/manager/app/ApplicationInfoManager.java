/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.app;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.app.ApplicationInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
public class ApplicationInfoManager {

    @Value("${app.component.version.file:classpath:static/components_versions.json}")
    private String componentVersionFile;

    public ApplicationInfo getInfo() {
        return new ApplicationInfo(getComponentVersions());
    }

    private Map<String, String> getComponentVersions() {
        try (FileReader reader = new FileReader(ResourceUtils.getFile(componentVersionFile))) {
            final String content = String.join("\n", IOUtils.readLines(reader));
            return JsonMapper.parseData(content, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.error("Failed to read component version file from {}: {}", componentVersionFile, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
