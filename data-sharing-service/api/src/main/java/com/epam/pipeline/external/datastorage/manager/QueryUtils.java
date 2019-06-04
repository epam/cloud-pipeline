/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.manager;

import com.epam.pipeline.external.datastorage.controller.Result;
import com.epam.pipeline.external.datastorage.controller.ResultStatus;
import com.epam.pipeline.external.datastorage.exception.PipleineResponseException;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public final class QueryUtils {

    private QueryUtils() {}

    public static <T> T execute(Call<Result<T>> call) {
        try {
            Response<Result<T>> response =call.execute();

            if (response.isSuccessful() && response.body().getStatus() == ResultStatus.OK) {
                return response.body().getPayload();
            }

            if (!response.isSuccessful()) {
                throw new PipleineResponseException(String.format("Unexpected status: %d, %s", response.code(),
                        response.errorBody() != null ? response.errorBody().string() : ""));
            } else {
                throw new PipleineResponseException(String.format("Unexpected status: %s, %s",
                        response.body().getStatus(), response.body().getMessage()));
            }
        } catch (IOException e) {
            throw  new PipleineResponseException(e);
        }
    }

}
