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

package com.epam.pipeline.entity.region;

import lombok.Getter;

@Getter
public enum GCPCustomVMType {

    n1(true, 6.5, ""),
    n2(true, 8.0, "n2-"),
    n2d(true, 8.0, "n2d-"),
    e2(false, null, "e2-");

    private final boolean supportExternal;
    private final Double extendedFactor;
    private final String prefix;

    GCPCustomVMType(final boolean supportExternal, final Double extendedFactor, final String prefix) {
        this.supportExternal = supportExternal;
        this.extendedFactor = extendedFactor;
        this.prefix = prefix;
    }
}
