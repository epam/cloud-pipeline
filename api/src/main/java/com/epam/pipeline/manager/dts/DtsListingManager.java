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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class DtsListingManager {
    private static final String DELIMITER = "/";

    private final AuthManager authManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsClientBuilder clientBuilder;

    public DtsDataStorageListing list(String path, Long dtsId, Integer pageSize, String marker) {
        DtsClient dtsClient = clientBuilder.createDtsClient(getDtsBaseUrl(path, dtsId),
                authManager.issueTokenForCurrentUser().getToken());
        Result<DtsDataStorageListing> result =
                DtsClient.executeRequest(dtsClient.getList(path, pageSize, marker));
        return result.getPayload();
    }

    private String getDtsBaseUrl(String path, Long dtsId) {
        DtsRegistry registry = dtsRegistryManager.load(dtsId);
        Assert.isTrue(registry.getPrefixes()
                        .stream()
                        .anyMatch(prefix -> path.startsWith(trimTrailingDelimiter(prefix))),
                String.format("Required path %s does not satisfy DTS registry with id %d", path, dtsId));
        return registry.getUrl();
    }

    private String trimTrailingDelimiter(String content) {
        if (content.endsWith(DELIMITER)) {
            return content.substring(0, content.length() - 1);
        }
        return content;
    }
}
