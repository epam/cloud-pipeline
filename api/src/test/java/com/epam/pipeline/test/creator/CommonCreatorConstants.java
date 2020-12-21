/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator;

import com.epam.pipeline.controller.Result;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommonCreatorConstants {

    public static final TypeReference<Result<String>> STRING_INSTANCE_TYPE =
            new TypeReference<Result<String>>() {};
    public static final TypeReference<Result<Boolean>> BOOLEAN_INSTANCE_TYPE =
            new TypeReference<Result<Boolean>>() {};
    public static final TypeReference<Result<Long>> LONG_INSTANCE_TYPE =
            new TypeReference<Result<Long>>() {};
    public static final TypeReference<Result<Map<String, String>>> STRING_MAP_INSTANCE_TYPE =
            new TypeReference<Result<Map<String, String>>>() {};
    public static final TypeReference<Result<Set<String>>> STRING_SET_INSTANCE_TYPE =
            new TypeReference<Result<Set<String>>>() {};
    public static final TypeReference<Result<List<String>>> STRING_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<String>>>() {};
    public static final TypeReference<Result<Set<Long>>> LONG_SET_INSTANCE_TYPE =
            new TypeReference<Result<Set<Long>>>() {};
    public static final TypeReference<Result<Integer>> INTEGER_TYPE = new TypeReference<Result<Integer>>() {};
    public static final TypeReference<Result<Object>> OBJECT_TYPE = new TypeReference<Result<Object>>() {};

    public static final long ID = 1L;
    public static final long ID_2 = 2L;
    public static final long ID_3 = 3L;
    public static final int TEST_INT = 4;
    public static final int READ_PERMISSION = 1;
    public static final int WRITE_PERMISSION = 2;
    public static final int EXECUTE_PERMISSION = 4;
    public static final String TEST_STRING = "TEST";
    public static final List<String> TEST_STRING_LIST = Collections.singletonList(TEST_STRING);
    public static final byte[] TEST_ARRAY = {1, 1, 1};
    public static final Map<String, String> TEST_STRING_MAP = Collections.singletonMap(TEST_STRING, TEST_STRING);
    public static final Set<String> TEST_STRING_SET = Collections.singleton(TEST_STRING);
    public static final List<Long> TEST_LONG_LIST = Collections.singletonList(ID);
    public static final Set<Long> TEST_LONG_SET = Collections.singleton(ID);

    private CommonCreatorConstants() {

    }
}
