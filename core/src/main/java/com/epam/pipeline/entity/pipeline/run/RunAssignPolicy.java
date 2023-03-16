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

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Function;


@Value
@Builder
public class RunAssignPolicy {

    String label;
    String value;

    public boolean isMatch(final String label, final String value) {
        return this.label.equals(label) && this.value.equals(value);
    }

    public boolean isMatch(final String label) {
        return this.label.equals(label);
    }

    public <T> Optional<T> ifMatchThenMapValue(final String label, Function<String, T> caster) {
        if (isMatch(label)) {
            return Optional.of(caster.apply(value));
        }
        return Optional.empty();
    }

    public boolean isValid() {
        return StringUtils.isNotBlank(label) && StringUtils.isNotBlank(value);
    }
}
