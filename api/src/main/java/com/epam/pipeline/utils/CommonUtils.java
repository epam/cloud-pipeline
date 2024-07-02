/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
                    (value1, value2) -> value1,
                    HashMap::new));
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mergeMaps(final Map<K, V> first,
                                             final Map<K, V> second,
                                             final Map<K, V>... remaining) {
        return Stream.of(
                    MapUtils.emptyIfNull(first).entrySet().stream(),
                    MapUtils.emptyIfNull(second).entrySet().stream(),
                    Arrays.stream(remaining).map(MapUtils::emptyIfNull).map(Map::entrySet).flatMap(Set::stream))
                .flatMap(Function.identity())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (value1, value2) -> value1,
                    HashMap::new));
    }

    @SafeVarargs
    public static <T> Optional<T> first(Supplier<Optional<T>>... suppliers) {
        return Arrays.stream(suppliers)
                .map(Supplier::get)
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .findFirst();
    }

    public static <T> List<T> toList(final Iterable<T> items) {
        if (Objects.isNull(items)) {
            return Collections.emptyList();
        }
        return StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toList());
    }

    @SafeVarargs
    public static <K, V> Map<K, V> toMap(final Pair<K, V>... pairs) {
        if (Objects.isNull(pairs)) {
            return Collections.emptyMap();
        }
        return Arrays.stream(pairs).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public static <T> List<T> subtract(final List<T> left, final List<T> right) {
        final List<T> result = new ArrayList<>(left);
        result.removeAll(right);
        return result;
    }

    public static <T> List<T> reversed(final List<T> items) {
        final List<T> result = new ArrayList<>(items);
        Collections.reverse(result);
        return result;
    }
}
