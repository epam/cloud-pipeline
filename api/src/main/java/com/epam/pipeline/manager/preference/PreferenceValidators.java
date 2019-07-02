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

package com.epam.pipeline.manager.preference;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.KeyListEntry;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static com.epam.pipeline.manager.preference.AbstractSystemPreference.*;
import static com.epam.pipeline.manager.preference.SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL;


/**
 * Validator functions for SystemPreferences
 */
public final class PreferenceValidators {
    // CHECKSTYLE:OFF
    /**
     * Checks that a preference is a valid URL and it is accessible
     */
    public static final BiPredicate<String, Map<String, Preference>> isValidUrl = (pref, dependencies) -> {
        try {
            URL url = new URL(pref);
            final URLConnection connection = url.openConnection();
            connection.connect();
            return true;
        } catch (IOException e) {
            return false;
        }
    };

    public static final BiPredicate<String, Map<String, Preference>> isValidUrlOrBlank = (pref, dependencies) -> {
        if (StringUtils.isBlank(pref)) {
            return true;
        }
        return isValidUrl.test(pref, dependencies);
    };

    /**
     * Checks that a preference is a syntactically valid URL only
     */
    public static final BiPredicate<String, Map<String, Preference>> isValidUrlSyntax = (pref, dependencies) -> {
        try {
            new URL(pref);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    };

    public static final BiPredicate<String, Map<String, Preference>> isEmptyOrValidUrlSyntax = (pref, dependencies) ->
            StringUtils.isEmpty(pref) || isValidUrlSyntax.test(pref, dependencies);

    /**
     * Checks that the validated preference value is a JSON object, that could be mapped to a specified type
     * @param typeReference a type reference of a type, that a JSON object preference should conform to
     * @param <T> a type, that a JSON object preference should conform to
     * @return a JSON object preference validator
     */
    public static <T> BiPredicate<String, Map<String, Preference>> isNullOrValidJson(TypeReference<T> typeReference) {
        return (pref, dependencies) -> {
            try {
                JsonMapper.parseData(pref, typeReference);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
    }

    /**
     * A validator, that checks that preference value is a JSON object
     */
    public static final BiPredicate<String, Map<String, Preference>> isNullOrAnyValidJson = (pref, dependencies) -> {
        if (StringUtils.isBlank(pref)) {
            return true;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.readTree(pref);
            return true;
        } catch (IOException e) {
            return false;
        }
    };

    public static final BiPredicate<String, Map<String, Preference>> isValidSpotAllocStrategy = (pref, dependencies) ->
            StringUtils.isNotBlank(pref) && (pref.equals("on_demand") || pref.equals("manual"));

    public static final BiPredicate<String, Map<String, Preference>> isValidCron = (pref, dependencies) -> {
        try {
            new CronTrigger(pref);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    };


    public static final BiPredicate<String, Map<String, Preference>> isEmptyOrValidBatchOfPaths = (pref, dependencies) ->
            pref.isEmpty() || Arrays.stream(pref.split(",")).allMatch(s -> s.matches("[^\0 \n]+[^\\/]")
                    || "/".equals(s) || "\\".equals(s));

    public static BiPredicate<String, Map<String, Preference>> isGreaterThan(int x) {
        return (pref, dependencies) -> StringUtils.isNumeric(pref) && Integer.parseInt(pref) > x;
    }

    public static BiPredicate<String, Map<String, Preference>> isMemorySize() {
        return (pref, dependencies) -> StringUtils.isNumeric(pref) && Integer.parseInt(pref) > 0
                || Pattern.compile(MemorySizePreference.MEMORY_SIZE_PATTERN).matcher(pref).matches();
    }

    public static BiPredicate<String, Map<String, Preference>> isGreaterThan(float x) {
        return (pref, dependencies) -> NumberUtils.isNumber(pref) && Float.parseFloat(pref) > x;
    }

    public static BiPredicate<String, Map<String, Preference>> isGreaterThanOrEquals(int x) {
        return (pref, dependencies) -> StringUtils.isNumeric(pref) && Integer.parseInt(pref) >= x;
    }

    /**
     * A no-op validator, that is always true
     */
    public static final BiPredicate<String, Map<String, Preference>> pass = (pref, dependencies) -> true;

    public static final BiPredicate<String, Map<String, Preference>> isAwsRoleValid = (pref, dependencies) -> {
        AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
        return iam.listRoles().getRoles().stream().anyMatch(r -> r.getArn().equals(pref));
    };

    public static final BiPredicate<String, Map<String, Preference>> isValidKey = (pref, dependencies) -> {
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
        return ec2.describeKeyPairs().getKeyPairs().stream()
                .anyMatch(key -> key.getKeyName().equals(pref));
    };

    public static final BiPredicate<String, Map<String, Preference>> isValidKMSKeyId = (pref, dependencies) -> {
        AWSKMS kmsClient = AWSKMSClientBuilder.defaultClient();
        List<KeyListEntry> keys = kmsClient.listKeys().getKeys();
        return keys.stream().anyMatch(k -> k.getKeyId().equals(pref));
    };

    public static final BiPredicate<String, Map<String, Preference>> isValidKMSKeyARN = (pref, dependencies) -> {
        AWSKMS kmsClient = AWSKMSClientBuilder.defaultClient();
        List<KeyListEntry> keys = kmsClient.listKeys().getKeys();
        return keys.stream().anyMatch(k -> k.getKeyArn().equals(pref));
    };

    public static final BiPredicate<String, Map<String, Preference>> isNotBlank = (pref, dependencies) ->
            StringUtils.isNotBlank(pref);

    public static final BiPredicate<String, Map<String, Preference>> isEmptyOrFileExist = (pref, dependencies) ->
            StringUtils.isEmpty(pref) || Files.exists(Paths.get(pref));

    public static final BiPredicate<String, Map<String, Preference>> isValidExternalServices =
        isNullOrValidJson(new TypeReference<List<ExternalServiceEndpoint>>() {})
            .and((pref, dependencies) -> {
                List<ExternalServiceEndpoint> endpoints = JsonMapper.parseData(pref,
                        new TypeReference<List<ExternalServiceEndpoint>>() {});
                return endpoints.stream().allMatch(e -> {
                    try {
                        new URL(e.getEndpointId());
                    } catch (MalformedURLException e1) {
                        return false;
                    }
                    return Files.exists(Paths.get(e.getMetadataPath()));
                });
            });

    public static final BiPredicate<String, Map<String, Preference>> isDockerSecurityScanGroupValid =
        (pref, dependencies) -> !Boolean.valueOf(pref) ||
                            (dependencies.get(DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL.getKey()).getValue() != null);

    public static final BiPredicate<String, Map<String, Preference>> isClusterInstanceTypeAllowed =
        (pref, dependencies) -> {
            Preference allowedInstanceTypes = dependencies.get(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey());
            return Arrays.stream(allowedInstanceTypes.getValue().split(","))
                .anyMatch(type -> {
                    AntPathMatcher matcher = new AntPathMatcher();
                    return matcher.match(type, pref);
                });
        };

    public static final BiPredicate<String, Map<String, Preference>> isValidIdleAction =
        (pref, ignored) -> IdleRunAction.contains(pref);

    private PreferenceValidators() {
        // No-op
    }

    // CHECKSTYLE:ON

}
