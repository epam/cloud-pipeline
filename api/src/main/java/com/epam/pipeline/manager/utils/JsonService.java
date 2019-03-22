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

package com.epam.pipeline.manager.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonService.class);

    public <T> T parseJsonFromFile(String filePath, TypeReference type) {
        if (StringUtils.isBlank(filePath)) {
            return null;
        }
        File file = new File(filePath);
        if (!file.isFile() || !file.exists()) {
            return null;
        }
        try(FileReader reader = new FileReader(file)) {
            String content = IOUtils.toString(reader);
            return JsonMapper.parseData(content, type);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.debug(e.getMessage(), e);
            return null;
        }
    }
}
