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

package com.epam.pipeline.manager.docker.scan.clair;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@NoArgsConstructor
public class ClairScanRequest {
    private Layer layer;

    public ClairScanRequest(String name, String digest, String registryPath, String image, String parentDigest,
                            String userName, String password) {
        this.layer = new Layer();
        this.layer.name = name;
        this.layer.path = String.format("https://%s/v2/%s/blobs/%s", registryPath, image, digest);

        if (StringUtils.isNotBlank(userName)) {
            String credentials = userName + ":" + password;
            this.layer.headers = Collections.singletonMap("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes()));
        }

        this.layer.parentName = parentDigest;
    }

    public ClairScanRequest(String name, String digest, String registryPath, String image, String parentDigest,
                            String token) {
        this.layer = new Layer();
        this.layer.name = name;
        this.layer.path = String.format("https://%s/v2/%s/blobs/%s", registryPath, image, digest);

        if (StringUtils.isNotBlank(token)) {
            this.layer.headers = Collections.singletonMap("Authorization", "Bearer " + token);
        }

        this.layer.parentName = parentDigest;
    }

    @Getter
    @Setter
    public static class Layer {
        private String name;
        private String path;
        private Map<String, String> headers;
        private String parentName;
        private String format = "Docker";
        private Integer indexByVersion;
    }

}
