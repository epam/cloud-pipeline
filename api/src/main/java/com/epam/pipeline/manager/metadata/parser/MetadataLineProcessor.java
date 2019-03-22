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

package com.epam.pipeline.manager.metadata.parser;

import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.exception.MetadataReadingException;
import com.google.common.collect.Maps;
import com.google.common.io.LineProcessor;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class MetadataLineProcessor implements LineProcessor<Map<String, PipeConfValue>> {

    private Map<String, PipeConfValue> result = Maps.newHashMap();
    private Boolean headerProcessed = false;
    private Map<String, Integer> indexes = new HashMap<>();
    private String delimiter;

    public MetadataLineProcessor(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public boolean processLine(String line) {
        if (StringUtils.isEmpty(line)) {
            return false;
        }
        if (!headerProcessed) {
            headerProcessed = true;
            String[] splitted = StringUtils.split(line, delimiter);
            for (int i = 0; i < splitted.length; i++) {
                MetadataColumnTypes type = getColumnType(splitted[i].toUpperCase());
                indexes.put(type.name(), i);
            }
        } else {
            String[] splitted = StringUtils.splitPreserveAllTokens(line, delimiter);
            if (splitted.length > 3 || splitted.length < 2) {
                throw new IllegalArgumentException("Cannot parse line: incorrect length.");
            }
            String key = splitted[indexes.get(MetadataColumnTypes.KEY.name())];
            String value = splitted[indexes.get(MetadataColumnTypes.VALUE.name())];
            String type = splitted.length == 2
                    ? "string" : splitted[indexes.get(MetadataColumnTypes.TYPE.name())];
            result.put(key, new PipeConfValue(type, value));
        }
        return true;
    }

    @Override
    public Map<String, PipeConfValue> getResult() {
        return result;
    }

    private MetadataColumnTypes getColumnType(String value) {
        try {
            return MetadataColumnTypes.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new MetadataReadingException("Unknown column type " + value);
        }
    }
}
