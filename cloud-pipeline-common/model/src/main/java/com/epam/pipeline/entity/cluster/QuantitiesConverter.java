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

package com.epam.pipeline.entity.cluster;

import io.fabric8.kubernetes.api.model.Quantity;
import java.util.HashMap;
import java.util.Map;

public final class QuantitiesConverter {

    private QuantitiesConverter() {
    }

    public static Map<String, String> convertQuantityMap(Map<String, Quantity> map) {
        if (map != null) {
            Map<String, String> result = new HashMap<>();
            map.forEach((key, quantity) -> result.put(key, quantity.getAmount()));
            return result;
        }
        return null;
    }
}
