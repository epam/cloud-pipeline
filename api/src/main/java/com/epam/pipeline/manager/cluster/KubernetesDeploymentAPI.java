/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.Path;

import java.util.Map;

public interface KubernetesDeploymentAPI {

    String NAMESPACE = "namespace";
    String NAME = "name";

    @PATCH("namespaces/{namespace}/deployments/{name}")
    @Headers("Content-Type: application/strategic-merge-patch+json")
    Call<Deployment> updateDeployment(@Path(NAMESPACE) String namespace, @Path(NAME) String deploymentName,
                                      @Body Map<String, Object> patch);
}
