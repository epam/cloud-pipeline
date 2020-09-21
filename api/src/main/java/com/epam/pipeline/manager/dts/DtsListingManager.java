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
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DtsListingManager {
    private static final String DELIMITER = "/";
    private static final String DEFAULT_DTS_USER_METADATA_KEY = "dts_name";

    private final AuthManager authManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsClientBuilder clientBuilder;
    private final MetadataManager metadataManager;
    private final PreferenceManager preferenceManager;

    public DtsDataStorageListing list(String path, Long dtsId, Integer pageSize, String marker) {
        DtsClient dtsClient = clientBuilder.createDtsClient(getDtsBaseUrl(path, dtsId),
                authManager.issueTokenForCurrentUser().getToken());
        Result<DtsDataStorageListing> result =
                DtsClient.executeRequest(dtsClient.getList(path, pageSize, marker, getDtsUser()));
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

    private String getDtsUser() {
        return Optional.ofNullable(authManager.getCurrentUser())
                .map(PipelineUser::getId)
                .map(id -> new EntityVO(id, AclClass.PIPELINE_USER))
                .map(Collections::singletonList)
                .map(metadataManager::listMetadataItems)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(MetadataEntry::getData)
                .map(data -> data.get(getDtsUserMetadataKey()))
                .map(PipeConfValue::getValue)
                .orElseGet(authManager::getAuthorizedUser);
    }

    private String getDtsUserMetadataKey() {
        return Optional.ofNullable(preferenceManager.getStringPreference(
                SystemPreferences.DTS_USER_METADATA_KEY.getKey()))
                .orElse(DEFAULT_DTS_USER_METADATA_KEY);
    }
}
