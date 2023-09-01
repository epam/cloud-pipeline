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

package com.epam.pipeline.entity.pipeline.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Object to describe assign strategy of a run pod to a node.
 * @see RunAssignPolicy.PodAssignSelector and
 * @see RunAssignPolicy.PodAssignTolerance for mode informations
 * */
@Value
@Builder
@ToString
public class RunAssignPolicy {

    PodAssignSelector selector;
    List<PodAssignTolerance> tolerances;

    public boolean isMatch(final String label, final String value) {
        if (!isValid()) {
            return false;
        }
        return this.selector.label.equals(label) && (value == null || this.selector.value.equals(value));
    }

    public boolean isMatch(final String label) {
        return isMatch(label, null);
    }

    public <T> Optional<T> ifMatchThenMapValue(final String label, Function<String, T> caster) {
        if (isMatch(label)) {
            return Optional.of(caster.apply(selector.value));
        }
        return Optional.empty();
    }

    @JsonIgnore
    public boolean isValid() {
        if (selector == null) {
            return false;
        }
        return StringUtils.isNotBlank(selector.label) && StringUtils.isNotBlank(selector.value);
    }

    public Map<String, String> loadTolerances() {
        return ListUtils.emptyIfNull(tolerances).stream()
                .filter(t -> StringUtils.isNotBlank(t.label))
                .collect(Collectors.toMap(PodAssignTolerance::getLabel,
                        t -> Optional.ofNullable(t.getValue()).orElse(StringUtils.EMPTY)));
    }

    /**
     * Object to implement selection capability while scheduling run pod.

     * User can specify selector as (with regard to real kube labels): label="node-role/cp-api-srv" value="true"
     * to assign a run to a node with label node-role/cp-api-srv=true
     * */
    @Value
    @Builder
    @ToString
    public static class PodAssignSelector {
        String label;
        String value;
    }

    /**
     * Object to implement tolerance capability while scheduling run pod.

     *    label="label-key", value="value" - equals to tolerance:
     *    tolerations:
     *      - key: "key1"
     *        operator: "Equal"
     *        value: "value"
     *        effect: "*"

     *    label="label-key", value="" or value=null - equals to tolerance:
     *    tolerations:
     *      - key: "key1"
     *        operator: "Exists"
     *        effect: "*"

     * For more information see
     * <a href="https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/">Taints, Tolerations</a>
     * */
    @Value
    @Builder
    @ToString
    public static class PodAssignTolerance {
        String label;
        String value;
    }
}
