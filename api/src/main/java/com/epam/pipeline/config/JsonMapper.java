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

package com.epam.pipeline.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonMapper extends ObjectMapper {
    private static final long serialVersionUID = -1414537788709027470L;

    /**
     * {@code String} specifies date format without offset used to serialize
     * or deserialize dates with Jackson
     */
    private static final String FMT_ISO_LOCAL_DATE = Constants.FMT_ISO_LOCAL_DATE;
    private static final String EMPTY_JSON = "{}";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(FMT_ISO_LOCAL_DATE);

    private static volatile JsonMapper instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public JsonMapper() {
        // calls the default constructor
        super();

        JavaTimeModule javaTimeModule = new JavaTimeModule();

        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));

        super.registerModule(javaTimeModule);

        // configures ISO8601 formatter for date without time zone
        // the used format is 'yyyy-MM-dd'
        super.setDateFormat(new SimpleDateFormat(FMT_ISO_LOCAL_DATE));

        // enforces to skip null and empty values in the serialized JSON output
        super.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        // enforces to skip null references in the serialized output of Map
        super.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);

        // enables serialization failures, when mapper encounters unknown properties names
        super.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // configures the format to prevent writing of the serialized output for java.util.Date
        // instances as timestamps. any date should be written in ISO format
        super.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public JsonMapper(final ObjectMapper src) {
        super(src);
    }

    public static <T> String convertDataToJsonStringForQuery(T data) {
        if (data == null) {
            return EMPTY_JSON;
        }
        String resultData;
        try {
            resultData = instance.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        if (StringUtils.isNotBlank(resultData)) {
            return resultData;
        }
        return EMPTY_JSON;
    }

    public static <T> T parseData(String data, TypeReference type) {
        return parseData(data, type, instance);
    }

    public static <T> T parseData(final String data,
                                  final TypeReference type,
                                  final ObjectMapper customMapper) {
        if (StringUtils.isBlank(data)) {
            return null;
        }

        try {
            return customMapper.readValue(data, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse json data " + data, e);
        }
    }

    /**
     * Returns a copy of the json mapper instance.
     *
     * It returns an ObjectMapper mapper instead of JsonMapper to
     * avoid miss leading usages of singleton methods of JsonMapper.
     */
    public static ObjectMapper newInstance() {
        return new JsonMapper(instance);
    }
}
