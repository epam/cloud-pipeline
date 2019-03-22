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
import com.epam.pipeline.controller.vo.AwsRegionVO;
import com.epam.pipeline.dao.region.AwsRegionDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.AbstractCORSRuleMixin;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.mapper.AwsRegionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@AclSync
@Service
@RequiredArgsConstructor
public class AwsRegionManager implements SecuredEntityManager {

    private static final String US_EAST_1 = "us-east-1";

    private final AwsRegionDao awsRegionDao;
    private final AwsRegionMapper awsRegionMapper;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;

    public List<AwsRegion> loadAll() {
        return awsRegionDao.loadAll();
    }

    @Transactional
    public AwsRegion create(final AwsRegionVO awsRegionVO) {
        validateAwsRegionVO(awsRegionVO);
        final AwsRegion region = awsRegionMapper.toAwsRegion(awsRegionVO);
        region.setOwner(authManager.getAuthorizedUser());
        region.setCreatedDate(DateUtils.now());
        fillMissingRegionSettingsWithDefaultValues(region);
        switchDefaultRegion(region);
        return awsRegionDao.create(region);
    }

    @Transactional
    public AwsRegion update(final Long id, final AwsRegionVO awsRegionVO) {
        final AwsRegion oldRegion = load(id);
        validateAwsRegionVO(awsRegionVO);
        final AwsRegion region = awsRegionMapper.toAwsRegion(awsRegionVO);
        region.setId(id);
        region.setOwner(oldRegion.getOwner());
        region.setCreatedDate(oldRegion.getCreatedDate());
        fillMissingRegionSettingsWithDefaultValues(region);
        switchDefaultRegion(region);
        awsRegionDao.update(region);
        return region;
    }


    @Transactional
    public AwsRegion delete(final Long id) {
        final AwsRegion awsRegion = load(id);
        awsRegionDao.delete(id);
        return awsRegion;
    }

    @Override
    public AwsRegion load(final Long id) {
        return awsRegionDao.loadById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_NOT_FOUND, id)));
    }

    @Override
    public AwsRegion changeOwner(final Long id, final String owner) {
        final AwsRegion awsRegion = load(id);
        awsRegion.setOwner(owner);
        final AwsRegionVO awsRegionVO = awsRegionMapper.toAwsRegionVO(awsRegion);
        return update(id, awsRegionVO);
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.AWS_REGION;
    }

    @Override
    public AwsRegion loadByNameOrId(final String identifier) {
        return loadRegionByNameOrId(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_NOT_FOUND, identifier)));
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractSecuredEntity loadWithParents(Long id) {
        throw new UnsupportedOperationException();
    }

    public List<String> loadAllAvailable() {
        return Arrays.stream(Region.values())
                .map(region -> Optional.ofNullable(region.getFirstRegionId()).orElse(US_EAST_1))
                .collect(Collectors.toList());
    }

    public AwsRegion getAwsRegion(S3bucketDataStorage dataStorage) {
        return Optional.ofNullable(dataStorage.getRegionId())
                .map(regionId -> load(dataStorage.getRegionId()))
                .orElse(loadDefaultRegion());
    }

    public AwsRegion loadDefaultRegion() {
        return awsRegionDao.loadDefaultRegion()
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_DEFAULT_UNDEFINED)));
    }

    public AwsRegion loadByAwsRegionName(String awsRegionName) {
        return awsRegionDao.loadByAwsRegion(awsRegionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_REGIONID_INVALID, awsRegionName)));
    }

    public AwsRegion loadRegionOrGetDefault(Long regionId) {
        return Optional.ofNullable(regionId)
                .map(this::load)
                .orElse(loadDefaultRegion());
    }

    private void switchDefaultRegion(AwsRegion region) {
        if (region.isDefault()) {
            awsRegionDao.loadDefaultRegion().ifPresent(defaultRegion -> {
                if (!defaultRegion.getId().equals(region.getId())) {
                    defaultRegion.setDefault(false);
                    awsRegionDao.update(defaultRegion);
                }
            });
        }
    }

    private void fillMissingRegionSettingsWithDefaultValues(final AwsRegion region) {
        if (StringUtils.isBlank(region.getCorsRules())) {
            preferenceManager.load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey())
                    .ifPresent(policy -> region.setCorsRules(policy.getValue()));
        }
        if (StringUtils.isBlank(region.getPolicy())) {
            preferenceManager.load(SystemPreferences.DATA_STORAGE_POLICY.getKey())
                    .ifPresent(policy -> region.setPolicy(policy.getValue()));
        }
        if (StringUtils.isBlank(region.getKmsKeyId())) {
            region.setKmsKeyId(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SECURITY_KEY_ID));
        }
        if (StringUtils.isBlank(region.getKmsKeyArn())) {
            region.setKmsKeyArn(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SECURITY_KEY_ARN));
        }
    }

    private ObjectMapper corsRulesMapper() {
        return JsonMapper.newInstance()
                .addMixIn(CORSRule.class, AbstractCORSRuleMixin.class)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    private Optional<AwsRegion> loadRegionByNameOrId(final String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            final Optional<AwsRegion> awsRegion = awsRegionDao.loadById(Long.parseLong(identifier));
            return awsRegion.isPresent()
                    ? awsRegion
                    : awsRegionDao.loadByName(identifier);
        } else {
            return awsRegionDao.loadByName(identifier);
        }
    }

    private void validateAwsRegionVO(final AwsRegionVO awsRegionVO) {
        Assert.notNull(awsRegionVO.getName(), messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_NAME_MISSING));
        validateRegionId(awsRegionVO);
        if (StringUtils.isNotBlank(awsRegionVO.getCorsRules())) {
            try {
                corsRulesMapper().readValue(awsRegionVO.getCorsRules(), new TypeReference<List<CORSRule>>() {});
            } catch (IOException e) {
                throw new AwsRegionException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_CORS_RULES_INVALID,
                                awsRegionVO.getCorsRules()), e);
            }
        }
        if (StringUtils.isNotBlank(awsRegionVO.getPolicy())) {
            try {
                new ObjectMapper().readValue(awsRegionVO.getPolicy(), new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                throw new AwsRegionException(
                        messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_POLICY_INVALID,
                                awsRegionVO.getPolicy()), e);
            }
        }
    }

    private void validateRegionId(AwsRegionVO awsRegionVO) {
        Assert.notNull(awsRegionVO.getAwsRegionName(),
                messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_REGIONID_MISSING));
        try {
            Region.fromValue(awsRegionVO.getAwsRegionName());
        } catch (IllegalArgumentException e) {
            throw new AwsRegionException(messageHelper.getMessage(MessageConstants.ERROR_AWS_REGION_REGIONID_INVALID,
                    awsRegionVO.getAwsRegionName()), e);
        }
    }

}
