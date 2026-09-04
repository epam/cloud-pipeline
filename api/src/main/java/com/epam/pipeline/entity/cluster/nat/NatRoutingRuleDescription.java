/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.nat;

import java.util.Objects;

public record NatRoutingRuleDescription(
        String externalName,
        String externalIp,
        Integer port,
        String description,
        String protocol) {

    // Preserve the original @EqualsAndHashCode(exclude = {"description","protocol"}) semantics:
    // two rules are equal when they refer to the same host+ip+port regardless of description/protocol.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NatRoutingRuleDescription that)) {
            return false;
        }
        return Objects.equals(externalName, that.externalName)
                && Objects.equals(externalIp, that.externalIp)
                && Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalName, externalIp, port);
    }
}
