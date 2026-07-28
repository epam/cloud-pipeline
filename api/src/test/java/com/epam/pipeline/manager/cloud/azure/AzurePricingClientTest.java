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

package com.epam.pipeline.manager.cloud.azure;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzurePricingClientTest {

    public static final int CODE_500 = 500;

    @Test
    public void executeRequestShouldReturnBodyOnSuccessfulResponse() throws IOException {
        final Call<String> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success("body"));

        final String result = AzurePricingClient.executeRequest(call);

        assertThat(result, is("body"));
    }

    @Test(expected = IOException.class)
    public void executeRequestShouldThrowIOExceptionOnNonSuccessfulResponse() throws IOException {
        final Call<String> call = mock(Call.class);
        when(call.execute()).thenReturn(
                Response.error(CODE_500, ResponseBody.create(MediaType.parse("text/plain"), "server error")));

        AzurePricingClient.executeRequest(call);
    }

    @Test(expected = IOException.class)
    public void executeRequestShouldPropagateIOExceptionFromCallExecute() throws IOException {
        final Call<String> call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("network error"));

        AzurePricingClient.executeRequest(call);
    }
}
