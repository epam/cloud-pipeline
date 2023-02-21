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

package com.epam.pipeline.dao.region;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.Optional;

import static com.epam.pipeline.config.JsonMapper.convertDataToJsonStringForQuery;

@Service
class AzureRegionDaoHelper extends AbstractCloudRegionDaoHelper<AzureRegion, AzureRegionCredentials> {
    @Getter
    private final CloudProvider provider = CloudProvider.AZURE;

    @Override
    public MapSqlParameterSource getProviderParameters(final AzureRegion region,
                                                       final AzureRegionCredentials credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(CloudRegionParameters.STORAGE_ACCOUNT.name(), region.getStorageAccount());
        Optional.ofNullable(credentials)
                .map(AzureRegionCredentials::getStorageAccountKey)
                .ifPresent(accountKey -> params.addValue(CloudRegionParameters.STORAGE_ACCOUNT_KEY.name(), accountKey));
        params.addValue(CloudRegionParameters.RESOURCE_GROUP.name(), region.getResourceGroup());
        params.addValue(CloudRegionParameters.CORS_RULES.name(), region.getCorsRules());
        params.addValue(CloudRegionParameters.POLICY.name(), convertDataToJsonStringForQuery(region.getAzurePolicy()));
        params.addValue(CloudRegionParameters.SUBSCRIPTION.name(), region.getSubscription());
        params.addValue(CloudRegionParameters.AUTH_FILE.name(), region.getAuthFile());
        params.addValue(CloudRegionParameters.SSH_PUBLIC_KEY.name(), region.getSshPublicKeyPath());
        params.addValue(CloudRegionParameters.METER_REGION_NAME.name(), region.getMeterRegionName());
        params.addValue(CloudRegionParameters.AZURE_API_URL.name(), region.getAzureApiUrl());
        params.addValue(CloudRegionParameters.PRICE_OFFER_ID.name(), region.getPriceOfferId());
        params.addValue(CloudRegionParameters.ENTERPRISE_AGREEMENTS.name(), region.isEnterpriseAgreements());
        return params;
    }

    @Override
    @SneakyThrows
    public AzureRegion parseCloudRegion(final ResultSet rs) {
        final AzureRegion region = new AzureRegion();
        fillCommonCloudRegionFields(region, rs);
        region.setCorsRules(rs.getString(CloudRegionParameters.CORS_RULES.name()));
        region.setStorageAccount(rs.getString(CloudRegionParameters.STORAGE_ACCOUNT.name()));
        region.setResourceGroup(rs.getString(CloudRegionParameters.RESOURCE_GROUP.name()));
        region.setAzurePolicy(parsePolicy(rs.getString(CloudRegionParameters.POLICY.name())));
        region.setAuthFile(rs.getString(CloudRegionParameters.AUTH_FILE.name()));
        region.setSubscription(rs.getString(CloudRegionParameters.SUBSCRIPTION.name()));
        region.setSshPublicKeyPath(rs.getString(CloudRegionParameters.SSH_PUBLIC_KEY.name()));
        region.setMeterRegionName(rs.getString(CloudRegionParameters.METER_REGION_NAME.name()));
        region.setAzureApiUrl(rs.getString(CloudRegionParameters.AZURE_API_URL.name()));
        region.setPriceOfferId(rs.getString(CloudRegionParameters.PRICE_OFFER_ID.name()));
        region.setEnterpriseAgreements(rs.getBoolean(CloudRegionParameters.ENTERPRISE_AGREEMENTS.name()));
        return region;
    }

    private AzurePolicy parsePolicy(final String policy) {
        return JsonMapper.parseData(policy, new TypeReference<AzurePolicy>() { });
    }

    @Override
    @SneakyThrows
    public AzureRegionCredentials parseCloudRegionCredentials(final ResultSet rs) {
        final AzureRegionCredentials credentials = new AzureRegionCredentials();
        credentials.setStorageAccountKey(rs.getString(CloudRegionParameters.STORAGE_ACCOUNT_KEY.name()));
        return credentials;
    }
}
