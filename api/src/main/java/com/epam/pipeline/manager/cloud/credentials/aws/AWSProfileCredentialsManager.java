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

package com.epam.pipeline.manager.cloud.credentials.aws;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.cloud.credentials.CloudProfileCredentialsManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.repository.cloud.credentials.aws.AWSProfileCredentialsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.transaction.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AWSProfileCredentialsManager implements CloudProfileCredentialsManager<AWSProfileCredentials> {

    private final AWSProfileCredentialsRepository repository;
    private final CloudProfileCredentialsMapper mapper;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    @Transactional
    public AWSProfileCredentials create(final AWSProfileCredentials credentials) {
        validateProfileCredentials(credentials);
        final AWSProfileCredentialsEntity entity = mapper.toAWSEntity(credentials);
        entity.setId(null);
        return mapper.toAWSDto(repository.save(entity));
    }

    @Override
    @Transactional
    public AWSProfileCredentials update(final Long id, final AWSProfileCredentials credentials) {
        validateProfileCredentials(credentials);
        final AWSProfileCredentialsEntity entity = findEntity(id);
        entity.setAssumedRole(credentials.getAssumedRole());
        entity.setProfileName(credentials.getProfileName());
        entity.setPolicy(credentials.getPolicy());
        repository.save(entity);
        return mapper.toAWSDto(entity);
    }

    @Override
    public TemporaryCredentials generateProfileCredentials(final AWSProfileCredentials credentials,
                                                           final AbstractCloudRegion region) {
        final Integer duration = preferenceManager.getPreference(SystemPreferences.PROFILE_TEMP_CREDENTIALS_DURATION);

        final String role = credentials.getAssumedRole();
        final String profile = credentials.getProfileName();
        final String policy = credentials.getPolicy();
        final String regionCode = getRegionCode(region);

        return AWSUtils.generate(duration, policy, role, profile, regionCode);
    }

    private AWSProfileCredentialsEntity findEntity(final Long id) {
        final AWSProfileCredentialsEntity entity = repository.findOne(id);
        Assert.notNull(entity, messageHelper.getMessage(MessageConstants.ERROR_PROFILE_ID_NOT_FOUND, id));
        return entity;
    }

    private void validateProfileCredentials(final AWSProfileCredentials credentials) {
        Assert.state(StringUtils.isNotBlank(credentials.getAssumedRole()),
                messageHelper.getMessage(MessageConstants.ERROR_PROFILE_ASSUMED_ROLE_NOT_FOUND));
        Assert.state(StringUtils.isNotBlank(credentials.getPolicy()),
                messageHelper.getMessage(MessageConstants.ERROR_PROFILE_POLICY_NOT_FOUND));
        Assert.state(StringUtils.isNotBlank(credentials.getProfileName()),
                messageHelper.getMessage(MessageConstants.ERROR_PROFILE_NAME_NOT_FOUND));
    }

    private String getRegionCode(final AbstractCloudRegion region) {
        if (Objects.isNull(region)) {
            return null;
        }
        return region.getRegionCode();
    }
}
