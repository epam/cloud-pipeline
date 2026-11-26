/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git;

import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class RestApiUtilsTest {

    private static final byte[] CONTENT = "file content".getBytes(StandardCharsets.UTF_8);
    private static final long UNKNOWN_LENGTH = -1L;
    private static final int LARGE_LIMIT = 1024;
    private static final int SMALL_LIMIT = 4;

    @Test
    public void getFileContentShouldReadTheWholeContentWhenTheStreamReturnsItInParts() throws IOException {
        assertArrayEquals(CONTENT,
                RestApiUtils.getFileContent(call(CONTENT.length), LARGE_LIMIT));
    }

    @Test
    public void getFileContentShouldNotPadTheContentWhenItsLengthIsUnknown() throws IOException {
        assertArrayEquals(CONTENT,
                RestApiUtils.getFileContent(call(UNKNOWN_LENGTH), LARGE_LIMIT));
    }

    @Test
    public void getFileContentShouldStopAtTheByteLimit() throws IOException {
        assertArrayEquals(Arrays.copyOf(CONTENT, SMALL_LIMIT),
                RestApiUtils.getFileContent(call(UNKNOWN_LENGTH), SMALL_LIMIT));
    }

    @SuppressWarnings("unchecked")
    private static Call<ResponseBody> call(final long contentLength) throws IOException {
        final ResponseBody body = mock(ResponseBody.class);
        doReturn(new OneByteAtATimeInputStream(CONTENT)).when(body).byteStream();
        doReturn(contentLength).when(body).contentLength();
        final Call<ResponseBody> call = mock(Call.class);
        doReturn(Response.success(body)).when(call).execute();
        return call;
    }

    /**
     * A stream that returns one byte per read, as a network stream may return only what has arrived so far.
     */
    private static final class OneByteAtATimeInputStream extends InputStream {

        private final ByteArrayInputStream delegate;

        private OneByteAtATimeInputStream(final byte[] content) {
            this.delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) {
            return delegate.read(buffer, offset, Math.min(length, 1));
        }
    }
}
