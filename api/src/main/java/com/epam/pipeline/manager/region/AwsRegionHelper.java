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

package com.epam.pipeline.manager.region;

import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.Region;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.AbstractCORSRuleMixin;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@RequiredArgsConstructor
@Component
public class AwsRegionHelper implements CloudRegionHelper<AwsRegion, AbstractCloudRegionCredentials> {

    private static final String US_EAST_1 = "us-east-1";

    private final MessageHelper messageHelper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public void validateRegion(final AwsRegion region, final AbstractCloudRegionCredentials credentials) {
        validateRegionId(region);
        if (StringUtils.isNotBlank(region.getCorsRules())) {
            try {
                corsRulesMapper().readValue(region.getCorsRules(), new TypeReference<List<CORSRule>>() {});
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_CORS_RULES_INVALID,
                                region.getCorsRules()), e);
            }
        }
        if (StringUtils.isNotBlank(region.getPolicy())) {
            try {
                new ObjectMapper().readValue(region.getPolicy(), new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_REGION_POLICY_INVALID,
                                region.getPolicy()), e);
            }
        }
    }

    private void validateRegionId(final AbstractCloudRegion region) {
        final String regionCode = region.getRegionCode();
        Assert.notNull(regionCode, messageHelper.getMessage(MessageConstants.ERROR_REGION_REGIONID_MISSING));
        try {
            Region.fromValue(regionCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_REGION_REGIONID_INVALID, regionCode), e);
        }
    }

    @Override
    public List<String> loadAvailableRegions() {
        return Arrays.stream(Region.values())
                .map(region -> Optional.ofNullable(region.getFirstRegionId()).orElse(US_EAST_1))
                .collect(Collectors.toList());
    }

    @Override
    public AwsRegion mergeRegions(final AwsRegion originalRegion, final AwsRegion updatedRegion) {
        originalRegion.setName(updatedRegion.getName());
        originalRegion.setDefault(updatedRegion.isDefault());
        originalRegion.setCorsRules(updatedRegion.getCorsRules());
        originalRegion.setPolicy(updatedRegion.getPolicy());
        originalRegion.setKmsKeyId(updatedRegion.getKmsKeyId());
        originalRegion.setKmsKeyArn(updatedRegion.getKmsKeyArn());
        originalRegion.setVersioningEnabled(updatedRegion.isVersioningEnabled());
        originalRegion.setBackupDuration(updatedRegion.getBackupDuration());
        originalRegion.setTempCredentialsRole(updatedRegion.getTempCredentialsRole());
        originalRegion.setProfile(updatedRegion.getProfile());
        originalRegion.setFileShareMounts(updatedRegion.getFileShareMounts());
        originalRegion.setSshKeyName(updatedRegion.getSshKeyName());
        return originalRegion;
    }

    private ObjectMapper corsRulesMapper() {
        return JsonMapper.newInstance()
                .addMixIn(CORSRule.class, AbstractCORSRuleMixin.class)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }
}
