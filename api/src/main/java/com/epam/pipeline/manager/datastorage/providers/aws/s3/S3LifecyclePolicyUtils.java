/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.services.s3.model.lifecycle.LifecycleAndOperator;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilterPredicate;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.amazonaws.services.s3.model.lifecycle.LifecycleTagPredicate;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.lifecycle.s3.S3StorageLifecyclePolicy;
import com.epam.pipeline.entity.datastorage.lifecycle.s3.S3StorageLifecycleRuleFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Util class providing methods to interact with AWS S3 API.
 * Uses Default Credential Provider Chain for AWS authorization.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final public class S3LifecyclePolicyUtils {

    public static Optional<List<BucketLifecycleConfiguration.Rule>> buildBucketLifecycleRulesIfAny(
            final StoragePolicy policy) {
        return Optional.ofNullable(policy.getStorageLifecyclePolicy())
                .map(policyString -> {
                    final S3StorageLifecyclePolicy storageLifecyclePolicy =
                            JsonMapper.parseData(policyString, new TypeReference<S3StorageLifecyclePolicy>(){});
                    return ListUtils.emptyIfNull(storageLifecyclePolicy.getRules())
                            .stream()
                            .map(storageLifecycleRule -> {
                                final BucketLifecycleConfiguration.Rule rule = new BucketLifecycleConfiguration.Rule()
                                        .withId(storageLifecycleRule.getId())
                                        .withTransitions(
                                                ListUtils.emptyIfNull(storageLifecycleRule.getTransitions())
                                                        .stream()
                                                        .map(trn -> new BucketLifecycleConfiguration.Transition()
                                                                .withDays(trn.getTransitionAfterDays())
                                                                .withStorageClass(trn.getStorageClass())
                                                        ).collect(Collectors.toList())
                                        ).withExpirationInDays(storageLifecycleRule.getExpirationAfterDays())
                                        .withStatus(BucketLifecycleConfiguration.ENABLED);

                                final LifecycleFilterPredicate lifecyclePredicate =
                                        constructLifecyclePredicate(storageLifecycleRule.getFilter());
                                if (lifecyclePredicate != null) {
                                    rule.withFilter(new LifecycleFilter(lifecyclePredicate));
                                }
                                return rule;
                            }).collect(Collectors.toList());
                });
    }

    private static LifecycleFilterPredicate constructLifecyclePredicate(final S3StorageLifecycleRuleFilter filter) {
        final List<LifecycleFilterPredicate> prefixPredicates = ListUtils.emptyIfNull(filter.getPrefixes()).stream()
                .map(LifecyclePrefixPredicate::new).collect(Collectors.toList());
        final LifecycleAndOperator prefixesPredicate = new LifecycleAndOperator(prefixPredicates);

        final List<LifecycleFilterPredicate> tagPredicates = ListUtils.emptyIfNull(filter.getTags())
                .stream()
                .map(t -> new Tag(t.getKey(), t.getValue()))
                .map(LifecycleTagPredicate::new).collect(Collectors.toList());
        final LifecycleAndOperator tagsPredicate = new LifecycleAndOperator(tagPredicates);

        LifecycleFilterPredicate resultPredicate = null;
        if (!prefixesPredicate.getOperands().isEmpty() && !tagsPredicate.getOperands().isEmpty()) {
            resultPredicate = new LifecycleAndOperator(
                    Arrays.asList(prefixesPredicate, tagsPredicate)
            );
        } else if (!prefixesPredicate.getOperands().isEmpty()) {
            resultPredicate = prefixesPredicate;
        } else if (!tagsPredicate.getOperands().isEmpty()) {
            resultPredicate = tagsPredicate;
        }
        return resultPredicate;
    }

}
