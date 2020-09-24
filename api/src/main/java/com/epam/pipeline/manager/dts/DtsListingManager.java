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

import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsDataStorageListingRequest;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.PipelineCredentials;
import com.epam.pipeline.exception.SystemPreferenceNotSetException;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Paths;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DtsListingManager {
    private static final String DELIMITER = "/";

    private final AuthManager authManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsClientBuilder clientBuilder;
    private final PreferenceManager preferenceManager;

    public DtsDataStorageListing list(String path, Long dtsId, Integer pageSize, String marker) {
        String apiToken = authManager.issueTokenForCurrentUser().getToken();
        DtsClient dtsClient = clientBuilder.createDtsClient(getDtsBaseUrl(path, dtsId), apiToken);
        PipelineCredentials credentials = new PipelineCredentials(getApi(), apiToken);
        DtsDataStorageListingRequest listingRequest = new DtsDataStorageListingRequest(
                Paths.get(path), pageSize, marker, credentials);
        return DtsClient.executeRequest(dtsClient.getList(listingRequest)).getPayload();
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

    private String getApi() {
        return Optional.of(SystemPreferences.BASE_API_HOST_EXTERNAL)
                .map(AbstractSystemPreference::getKey)
                .map(preferenceManager::getStringPreference)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new SystemPreferenceNotSetException(SystemPreferences.BASE_API_HOST_EXTERNAL));
    }

}
