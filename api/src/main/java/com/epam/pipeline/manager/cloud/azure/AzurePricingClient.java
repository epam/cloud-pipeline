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

import com.epam.pipeline.entity.pricing.azure.AzureEAPricingResult;
import com.epam.pipeline.entity.pricing.azure.AzureRateCardPricingResult;
import org.apache.commons.lang.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.Optional;

public interface AzurePricingClient {

    String AUTH_HEADER = "Authorization";

    @GET("subscriptions/{subscription}/providers/Microsoft.Commerce/RateCard")
    Call<AzureRateCardPricingResult> getPricing(@Header(AUTH_HEADER) String bearer,
                                                @Path("subscription") String subscription,
                                                @Query("$filter") String filter,
                                                @Query("api-version") String apiVersion);

    @GET("subscriptions/{subscription}/providers/Microsoft.Consumption/pricesheets/default")
    Call<AzureEAPricingResult> getPricesheet(@Header(AUTH_HEADER) String bearer,
                                             @Path("subscription") String subscription,
                                             @Query("api-version") String apiVersion,
                                             @Query("$expand") String expand,
                                             @Query("$top") int top,
                                             @Query(value = "$skiptoken", encoded = true) String skiptoken);

    static <T> T executeRequest(Call<T> request) throws IOException {
        final Response<T> response = request.execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        final String errorBody = Optional.ofNullable(response.errorBody()).map(body -> {
            try {
                return body.string();
            } catch (IOException e) {
                return "Failed to read error body";
            }
        }).orElse(StringUtils.EMPTY);
        throw new IOException(String.format("Azure request failed: %s %s", response.message(), errorBody));
    }
}
