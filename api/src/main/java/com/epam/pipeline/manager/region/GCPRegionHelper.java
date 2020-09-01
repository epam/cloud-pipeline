/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
@Slf4j
public class GCPRegionHelper implements CloudRegionHelper<GCPRegion, AbstractCloudRegionCredentials> {

    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @Override
    public void validateRegion(final GCPRegion region, final AbstractCloudRegionCredentials credentials) {
        validateRegionCode(region.getRegionCode(), messageHelper);
        Assert.state(StringUtils.isNotBlank(region.getProject()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_PROJECT_REQUIRED));
        Assert.state(StringUtils.isNotBlank(region.getSshPublicKeyPath()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_SSH_KEY_REQUIRED));
        Assert.state(StringUtils.isNotBlank(region.getImpersonatedAccount()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_IMP_ACC_REQUIRED));
    }

    @Override
    public List<String> loadAvailableRegions() {
        return preferenceManager.getPreference(SystemPreferences.GCP_REGION_LIST);
    }

    @Override
    public GCPRegion mergeRegions(final GCPRegion originalRegion, final GCPRegion updatedRegion) {
        originalRegion.setName(updatedRegion.getName());
        originalRegion.setCorsRules(updatedRegion.getCorsRules());
        originalRegion.setPolicy(updatedRegion.getPolicy());
        originalRegion.setDefault(updatedRegion.isDefault());
        originalRegion.setSshPublicKeyPath(updatedRegion.getSshPublicKeyPath());
        originalRegion.setAuthFile(updatedRegion.getAuthFile());
        originalRegion.setProject(updatedRegion.getProject());
        originalRegion.setApplicationName(updatedRegion.getApplicationName());
        originalRegion.setImpersonatedAccount(updatedRegion.getImpersonatedAccount());
        originalRegion.setCustomInstanceTypes(updatedRegion.getCustomInstanceTypes());
        originalRegion.setBackupDuration(updatedRegion.getBackupDuration());
        originalRegion.setVersioningEnabled(updatedRegion.isVersioningEnabled());
        originalRegion.setMountStorageRule(updatedRegion.getMountStorageRule());
        return originalRegion;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    @Override
    public String serializeCredentials(final GCPRegion region, final AbstractCloudRegionCredentials credentials) {
        final String authFile = region.getAuthFile();
        if (authFile != null) {
            try {
                final String fileContent = Files.lines(Paths.get(authFile), StandardCharsets.UTF_8)
                    .map(String::trim)
                    .collect(Collectors.joining(StringUtils.EMPTY));
                return Base64.encodeBase64String(fileContent.getBytes());
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        return null;
    }
}
