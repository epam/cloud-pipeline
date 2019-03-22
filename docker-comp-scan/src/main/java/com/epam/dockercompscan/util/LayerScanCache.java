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

package com.epam.dockercompscan.util;

import com.epam.dockercompscan.scan.domain.LayerScanResult;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.TimeUnit;

public class LayerScanCache {

    private static final long MAX_NUMBER_OF_LAYERS_PER_IMAGE = 127;
    private static final long SECONDS_IN_HOUR = 3600;

    private final Cache<String, String> byParents;

    private final Cache<String, LayerScanResult> byNames;

    public LayerScanCache(int expireCacheTime, int numberOfCachedScans) {
        byParents = CacheBuilder.newBuilder()
                .maximumSize(MAX_NUMBER_OF_LAYERS_PER_IMAGE * numberOfCachedScans)
                .expireAfterWrite(expireCacheTime * SECONDS_IN_HOUR + 1, TimeUnit.SECONDS)
                .build();
        byNames = CacheBuilder.newBuilder()
                .maximumSize(MAX_NUMBER_OF_LAYERS_PER_IMAGE * numberOfCachedScans)
                .expireAfterWrite(expireCacheTime * SECONDS_IN_HOUR + 1, TimeUnit.SECONDS)
                .build();
    }


    @Nullable
    public LayerScanResult getIfPresent(LayerKey key) {
        if (key.getName() != null) {
            return byNames.getIfPresent(key.getName());
        } else {
            String nameByParent = byParents.getIfPresent(key.getParentName());
            return nameByParent != null ? byNames.getIfPresent(nameByParent) : null;
        }
    }

    public synchronized void put(LayerKey key, LayerScanResult value) {
        if (key.getParentName() != null) {
            byParents.put(key.getParentName(), value.getLayerId());
        }
        if (key.getName() != null){
            byNames.put(key.getName(), value);
        }
    }

    public long size() {
        return byParents.size();
    }

    public void cleanUp() {
        byParents.cleanUp();
        byNames.cleanUp();
    }

}
