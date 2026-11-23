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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import io.fabric8.kubernetes.api.model.Toleration;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PodSpecMapperHelper {

    private PodSpecMapperHelper() {}

    public static final String TOLERATION_OP_EQUAL = "Equal";
    public static final String TOLERATION_OP_EXISTS = "Exists";

    public static List<Toleration> buildTolerations(final Map<String, String> nodeTolerances) {
        return MapUtils.emptyIfNull(nodeTolerances)
                .entrySet()
                .stream().map(t -> {
                    final Toleration toleration = new Toleration();
                    toleration.setKey(t.getKey());
                    if (StringUtils.isNotBlank(t.getValue())) {
                        toleration.setValue(t.getValue());
                        toleration.setOperator(TOLERATION_OP_EQUAL);
                    } else {
                        toleration.setOperator(TOLERATION_OP_EXISTS);
                    }
                    return toleration;
                }).collect(Collectors.toList());
    }

    /**
     * Builds the image of a run's pod, pinned to the run's docker image digest.
     *
     * @param pinImageDigest whether the pod may be pinned at all
     * @return the pinned image, or an empty value if the pod shall be created with the docker image tag:
     * the pinning is not allowed or the run has no digest, e.g. it was launched while the pinning was disabled
     */
    public static Optional<String> buildPinnedDockerImage(final PipelineRun run, final boolean pinImageDigest) {
        if (!pinImageDigest || StringUtils.isBlank(run.getActualDockerImageDigest())) {
            return Optional.empty();
        }
        return Optional.of(ToolUtils.getImageWithDigest(run.getActualDockerImage(),
                run.getActualDockerImageDigest()));
    }
}
