/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.EnumUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommonUtils {

    private CommonUtils() {
        //no op
    }


    public static <T extends Enum<T>> T getEnumValueOrDefault(final String enumName, final T defaultValue) {
        return Optional.ofNullable(enumName)
                .map(name -> EnumUtils.getEnum(defaultValue.getDeclaringClass(), enumName))
                .orElse(defaultValue);
    }

    public static <T extends CloudAwareService> Map<CloudProvider, T> groupByCloudProvider(final List<T> services) {
        return groupByKey(services, CloudAwareService::getProvider);
    }

    public static <T extends CloudAwareService> T getServiceForRegion(final Map<CloudProvider, T> services,
                                                                      final MessageHelper helper,
                                                                      final AbstractCloudRegion region) {
        return Optional.ofNullable(MapUtils.emptyIfNull(services).get(region.getProvider()))
            .orElseThrow(() -> new IllegalArgumentException(
                helper.getMessage(MessageConstants.ERROR_CLOUD_PROVIDER_NOT_SUPPORTED, region.getProvider())));
    }

    public static <K, T> Map<K, T> groupByKey(final List<T> services,
                                              final Function<T, K> keyFunction) {
        return ListUtils.emptyIfNull(services).stream()
                .collect(Collectors.toMap(keyFunction, Function.identity()));
    }

    public static <K, V> Map<K, V> mergeMaps(final Map<K, V> first,
                                             final Map<K, V> second) {
        return Stream.concat(
                MapUtils.emptyIfNull(first).entrySet().stream(),
                MapUtils.emptyIfNull(second).entrySet().stream())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (value1, value2) -> value1));
    }

    public static <T> Optional<T> first(Supplier<Optional<T>>... suppliers) {
        return Arrays.asList(suppliers).stream()
                .map(Supplier::get)
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .findFirst();
    }
}
