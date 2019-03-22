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

package com.epam.pipeline.assertions.base;

import com.epam.pipeline.entity.BaseEntity;
import org.assertj.core.api.AbstractAssert;

import java.util.Objects;

public abstract class AbstractBaseEntityAssert<S extends AbstractBaseEntityAssert<S, A>, A extends BaseEntity>
        extends AbstractAssert<S, A> {

    public AbstractBaseEntityAssert(A actual, Class<S> selfType) {
        super(actual, selfType);
    }

    public S hasId(Long id) {
        isNotNull();
        if (!Objects.equals(actual.getId(), id)) {
            failWithMessage("Expected entity id to be <%s> but was <%s>.", id, actual.getId());
        }
        return myself;
    }

    public S hasName(String name) {
        isNotNull();
        if (!Objects.equals(actual.getName(), name)) {
            failWithMessage("Expected entity name to be <%s> but was <%s>.", name, actual.getName());
        }
        return myself;
    }
}
