/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.kube;

import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface KubernetesAPI {
    String NAMESPACE = "namespace";
    String NAME = "name";

    @GET("/apis/networking.k8s.io/v1/namespaces/{namespace}/networkpolicies/{name}")
    Call<NetworkPolicy> getNetworkPolicy(@Path(NAMESPACE) String namespace, @Path(NAME) String policyName);

    @PUT("/apis/networking.k8s.io/v1/namespaces/{namespace}/networkpolicies/{name}")
    Call<NetworkPolicy> updateNetworkPolicy(@Path(NAMESPACE) String namespace, @Path(NAME) String policyName,
                                            @Body NetworkPolicy body);
}
