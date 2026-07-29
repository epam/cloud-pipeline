/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.credits;

import com.epam.pipeline.acl.credits.PlatformUsageCreditsEventApiService;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = PlatformUsageCreditsEventController.class)
public class PlatformUsageCreditsEventControllerTest extends AbstractControllerTest {

    private static final String EXPORT_URL = SERVLET_PATH + "/usage/credits/events/export";
    private static final String CSV_FILE_NAME = "credits_events.csv";
    private static final byte[] CSV_BYTES = "Timestamp,data\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private PlatformUsageCreditsEventApiService mockApiService;

    @Test
    public void shouldFailExportForUnauthorizedUser() {
        performUnauthorizedRequest(post(EXPORT_URL));
    }

    @Test
    @WithMockUser
    public void shouldExport() throws Exception {
        doAnswer(invocation -> {
            final OutputStream os = invocation.getArgument(1, OutputStream.class);
            os.write(CSV_BYTES);
            return null;
        }).when(mockApiService).export(any(PlatformUsageCreditsEventFilterVO.class), any(OutputStream.class));
        final String content = getObjectMapper()
                .writeValueAsString(PlatformUsageCreditsEventFilterVO.builder().build());

        final MvcResult result = performRequest(
                post(EXPORT_URL).content(content),
                EXPECTED_CONTENT_TYPE,
                MediaType.APPLICATION_OCTET_STREAM_VALUE);

        verify(mockApiService).export(any(PlatformUsageCreditsEventFilterVO.class), any(OutputStream.class));
        assertResponseHeader(result, CSV_FILE_NAME);
        assertContent(result, CSV_BYTES);
    }
}
