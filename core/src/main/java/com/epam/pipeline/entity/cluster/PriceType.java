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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Instance price type.
 */
@RequiredArgsConstructor
@Getter
public enum PriceType {
    SPOT("spot"),
    ON_DEMAND("on_demand");

    /**
     * Price type string representation.
     */
    private final String literal;

    @Override
    public String toString() {
        return literal;
    }

    public static PriceType fromTermType(final String termType) {
        if (termType.equals(TermType.ON_DEMAND.getName())) {
            return ON_DEMAND;
        } else if (termType.equals(TermType.LOW_PRIORITY.getName())
                || termType.equals(TermType.SPOT.getName())
                || termType.equals(TermType.PREEMPTIBLE.getName())) {
            return SPOT;
        } else {
            throw new IllegalArgumentException("Wrong term type of instance: " + termType);
        }
    }
}
