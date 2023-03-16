/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.execution;

import io.fabric8.kubernetes.api.model.Toleration;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PodSpecMapperHelper {

    private PodSpecMapperHelper() {}

    public static final String TOLERATION_OP_EQUALS = "Equals";
    public static final String TOLERATION_OP_EXISTS = "Exists";

    public static List<Toleration> buildTolerations(Map<String, String> nodeTolerances) {
        return MapUtils.emptyIfNull(nodeTolerances)
                .entrySet()
                .stream().map(t -> {
                    final Toleration toleration = new Toleration();
                    toleration.setKey(t.getKey());
                    if (StringUtils.isNotBlank(t.getValue())) {
                        toleration.setValue(t.getValue());
                        toleration.setOperator(TOLERATION_OP_EQUALS);
                    } else {
                        toleration.setOperator(TOLERATION_OP_EXISTS);
                    }
                    return toleration;
                }).collect(Collectors.toList());
    }
}
