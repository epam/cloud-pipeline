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

package com.epam.pipeline.util;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public final class ControllerTestUtils {

    private ControllerTestUtils() {
    }

    public static <T> ResponseResult<T> buildExpectedResult(final T payload) {
        final ResponseResult<T> expectedResult = new ResponseResult<>();
        expectedResult.setStatus("OK");
        expectedResult.setPayload(payload);
        return expectedResult;
    }

    public static <T> void assertResponse(final MvcResult mvcResult,
                                          final JsonMapper objectMapper,
                                          final ResponseResult<T> expectedResult,
                                          final TypeReference<Result<T>> typeReference) throws Exception {
        final String actual = mvcResult.getResponse().getContentAsString();
        StringUtils.isNotBlank(actual);
        assertThat(actual).isEqualToIgnoringWhitespace(objectMapper.writeValueAsString(expectedResult));

        final Result<T> actualResult = JsonMapper.parseData(actual, typeReference);
        assertEquals(expectedResult.getPayload(), actualResult.getPayload());
        System.out.println(actualResult.getPayload());
    }
}
