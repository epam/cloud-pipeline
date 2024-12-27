/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
 *  limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.cluster.LaunchCapability;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PipelineConfigurationLaunchCapabilitiesProcessor {

    private static final String COMMA = ",";
    private static final String CAPABILITY_PARAM_PREFIX = "CP_CAP_CUSTOM_";
    private static final List<String> BOOLEAN_PARAM_VALUES = Arrays.asList("true", "false", "yes", "no", "on", "off");

    private final PreferenceManager preferenceManager;

    public Map<String, PipeConfValueVO> process(final Map<String, PipeConfValueVO> params) {
        final List<LaunchCapability> capabilities = extractCapabilities(params);
        final List<Map<String, PipeConfValueVO>> capabilitiesParams = extractParams(capabilities);

        return Stream.concat(
                        Stream.of(params),
                        capabilitiesParams.stream())
                .reduce(this::mergeParams)
                .orElseGet(Collections::emptyMap);
    }

    private List<LaunchCapability> extractCapabilities(final Map<String, PipeConfValueVO> params) {
        final Map<String, LaunchCapability> capabilities = getCapabilities();
        return extractEnabledCapabilities(normalizeKeys(params), normalizeKeys(capabilities));
    }

    private Map<String, LaunchCapability> getCapabilities() {
        return flatten(getCapabilitiesTree());
    }

    private Map<String, LaunchCapability> getCapabilitiesTree() {
        return Optional.of(SystemPreferences.LAUNCH_CAPABILITIES)
                .map(preferenceManager::getPreference)
                .orElseGet(Collections::emptyMap);
    }

    private Map<String, LaunchCapability> flatten(final Map<String, LaunchCapability> capabilities) {
        return capabilities.entrySet()
                .stream()
                .flatMap(this::flatten)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Stream<Map.Entry<String, LaunchCapability>> flatten(final Map.Entry<String, LaunchCapability> entry) {
        return Optional.of(entry)
                .map(Map.Entry::getValue)
                .map(LaunchCapability::getCapabilities)
                .map(this::flatten)
                .filter(MapUtils::isNotEmpty)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(() -> Stream.of(entry));
    }

    private <V> Map<String, V> normalizeKeys(final Map<String, V> params) {
        return params.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()))
                .collect(Collectors.toMap(entry -> StringUtils.upperCase(entry.getKey()), Map.Entry::getValue));
    }

    private List<LaunchCapability> extractEnabledCapabilities(final Map<String, PipeConfValueVO> params,
                                                              final Map<String, LaunchCapability> capabilities) {
        return capabilities.entrySet().stream()
                .filter(entry -> isCapabilityEnabled(params, entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private boolean isCapabilityEnabled(final Map<String, PipeConfValueVO> params, final String name) {
        return Optional.ofNullable(name)
                .map(it -> CAPABILITY_PARAM_PREFIX + it)
                .map(params::get)
                .map(PipeConfValueVO::getValue)
                .map(BooleanUtils::toBoolean)
                .orElse(false);
    }

    private List<Map<String, PipeConfValueVO>> extractParams(final List<LaunchCapability> capabilities) {
        return capabilities.stream().map(this::extractParams).collect(Collectors.toList());
    }

    private Map<String, PipeConfValueVO> extractParams(final LaunchCapability capability) {
        return Optional.ofNullable(capability)
                .map(LaunchCapability::getParams)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new PipeConfValueVO(entry.getValue())));
    }

    private Map<String, PipeConfValueVO> mergeParams(final Map<String, PipeConfValueVO> left,
                                                     final Map<String, PipeConfValueVO> right) {
        final Map<String, PipeConfValueVO> params = new HashMap<>(left);
        right.forEach((key, value) -> params.merge(key, value, this::mergeParams));
        return params;
    }

    private PipeConfValueVO mergeParams(final PipeConfValueVO left, final PipeConfValueVO right) {
        final String type = resolveType(left);
        if (StringUtils.equalsIgnoreCase(type, PipeConfValueVO.STRING_TYPE)) {
            return new PipeConfValueVO(mergeStringParamsValues(left, right), type);
        }
        return new PipeConfValueVO(right.getValue(), type);
    }

    private String resolveType(final PipeConfValueVO value) {
        return Optional.ofNullable(value)
                .map(PipeConfValueVO::getType)
                .flatMap(type -> StringUtils.equalsIgnoreCase(type, PipeConfValueVO.STRING_TYPE)
                        ? resolveImplicitType(value)
                        : Optional.of(type))
                .orElse(PipeConfValueVO.STRING_TYPE);
    }

    private Optional<String> resolveImplicitType(final PipeConfValueVO value) {
        return Optional.ofNullable(value)
                .map(PipeConfValueVO::getValue)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::strip)
                .map(StringUtils::lowerCase)
                .map(it -> BOOLEAN_PARAM_VALUES.contains(it) ? PipeConfValueVO.BOOLEAN_TYPE : null);
    }

    private String mergeStringParamsValues(final PipeConfValueVO left, final PipeConfValueVO right) {
        return Stream.of(left, right)
                .map(PipeConfValueVO::getValue)
                .map(value -> StringUtils.split(value, COMMA))
                .flatMap(Arrays::stream)
                .distinct()
                .sorted()
                .collect(Collectors.joining(COMMA));
    }
}
