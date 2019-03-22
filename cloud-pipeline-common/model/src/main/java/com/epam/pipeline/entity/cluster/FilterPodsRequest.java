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

import io.fabric8.kubernetes.api.model.Pod;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FilterPodsRequest {
    private List<String> podStatuses;

    public static Predicate<? super Pod> getPodsByNodeNamePredicate(String nodeName) {
        return pod -> pod.getSpec() != null &&
                pod.getSpec().getNodeName() != null &&
                pod.getSpec().getNodeName().equals(nodeName);
    }

    public static Predicate<? super Pod> getPodsByNodeNameAndStatusPredicate(String nodeName, List<String> statuses) {
        Predicate<? super Pod> statusCheck = pod ->
                pod.getStatus() != null &&
                pod.getStatus().getPhase() != null &&
                statuses.indexOf(pod.getStatus().getPhase()) >= 0;
        if (statuses == null || statuses.size() == 0) {
            return FilterPodsRequest.getPodsByNodeNamePredicate(nodeName);
        }
        return pod -> FilterPodsRequest.getPodsByNodeNamePredicate(nodeName).test(pod) &&
                statusCheck.test(pod);
    }
}
