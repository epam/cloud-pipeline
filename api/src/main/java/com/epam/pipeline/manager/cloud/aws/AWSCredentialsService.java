/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@RequiredArgsConstructor
@Service
public class AWSCredentialsService {
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    public TemporaryCredentials generate(final AwsRegion region) {
        Assert.isTrue(StringUtils.isNotBlank(region.getIamRole()),
                messageHelper.getMessage(MessageConstants.ERROR_MISSING_IAM_ROLE));
        final Integer duration = preferenceManager.getPreference(
                SystemPreferences.CLOUD_REGION_TEMP_CREDENTIALS_DURATION);
        return AWSUtils.generate(duration, null,
                region.getIamRole(), null, region.getRegionCode());
    }
}
