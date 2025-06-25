/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.client.gcp;

import com.epam.pipeline.entity.gcp.price.BillingAccountPriceResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GCPPriceApiService {
    @GET("/v1beta/billingAccounts/{billingAccountId}/skus/-/prices")
    Call<BillingAccountPriceResponse> getAllPrices(@Path("billingAccountId") String billingAccountId,
                                                   @Query("currencyCode") String currencyCode,
                                                   @Query("pageToken") String pageToken,
                                                   @Query("pageSize") Integer pageSize);
}
