/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.manager.cluster.KubernetesConstants;
import io.fabric8.kubernetes.api.model.Node;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NodePoolUtils {

    static long determineActiveNodesCount(final List<Node> availableNodes,
                                          final Set<String> activePodIds,
                                          final Long poolId) {
        return ListUtils.emptyIfNull(availableNodes)
                .stream()
                .filter(currentNode -> {
                    final Map<String, String> labels = MapUtils.emptyIfNull(currentNode.getMetadata().getLabels());
                    final String nodeIdLabel = labels.get(KubernetesConstants.NODE_POOL_ID_LABEL);
                    if (StringUtils.isBlank(nodeIdLabel) && !NumberUtils.isDigits(nodeIdLabel)) {
                        return false;
                    }
                    return poolId.equals(Long.parseLong(nodeIdLabel)) &&
                            activePodIds.contains(labels.get(KubernetesConstants.RUN_ID_LABEL));
                })
                .count();
    }
}
