/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.monitor.monitoring.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lazy key-value cache backed by a caller-supplied loader function.
 * Each key is resolved at most once per instance; null results are memoized
 * so a missing key does not trigger repeated loader calls.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LazyCache<K, V> {

    private final Function<K, V> loader;
    private final Map<K, Optional<V>> cache = new HashMap<>();

    public LazyCache(final Function<K, V> loader) {
        this.loader = loader;
    }

    public Optional<V> get(final K key) {
        return cache.computeIfAbsent(key, k -> Optional.ofNullable(loader.apply(k)));
    }
}
