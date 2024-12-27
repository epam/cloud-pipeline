/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.manager.docker.scan.clair.ClairClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public final class ToolScanRestApiUtils {

    private ToolScanRestApiUtils() {
        // no-op
    }

    public static <T> void checkExecutionStatus(final Response<T> response, final String image,
                                                final String tag, final String layerRef) throws IOException {
        if (!response.isSuccessful()) {
            try (ResponseBody error = response.errorBody()) {
                final String errorBody = error != null ? error.string() : null;
                log.error(
                        String.format(
                                "Service: %s : Failed for %s:%s on %s layer: %s:%s response code: %d",
                                ClairClient.class, image, tag, layerRef, response.message(),
                                errorBody, response.code()
                        )
                );
            }
        }
    }

    public static <T> Optional<T> getScanResult(final boolean initialized, final Supplier<Call<T>> call)
            throws IOException {
        if (!initialized) {
            return Optional.empty();
        }

        final Response<T> response = call.get().execute();
        if (!response.isSuccessful()) {
            try (ResponseBody errorBody = response.errorBody()) {
                log.error(errorBody != null ? "Error while getting scan result: " + errorBody.string() : "");
                return Optional.empty();
            }
        }
        return Optional.ofNullable(response.body());
    }
}
