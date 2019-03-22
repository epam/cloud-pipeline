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

import com.epam.pipeline.entity.AbstractSecuredEntity;

import java.util.Objects;

public abstract class AbstractSecuredEntityAssert <S extends AbstractSecuredEntityAssert<S, A>,
        A extends AbstractSecuredEntity> extends AbstractBaseEntityAssert<S, A> {

    public AbstractSecuredEntityAssert(A actual, Class<S> selfType) {
        super(actual, selfType);
    }

    public S hasOwner(String owner) {
        isNotNull();
        if (!Objects.equals(actual.getOwner(), owner)) {
            failWithMessage("Expected entity owner to be <%s> but was <%s>.", owner, actual.getOwner());
        }
        return myself;
    }

    public S hasMask(int mask) {
        isNotNull();
        if (!Objects.equals(actual.getMask(), mask)) {
            failWithMessage("Expected entity mask to be <%s> but was <%s>.", mask, actual.getMask());
        }
        return myself;
    }

    public S isLocked(boolean locked) {
        isNotNull();
        if (!Objects.equals(actual.isLocked(), locked)) {
            failWithMessage("Expected entity locked to be <%s> but was <%s>.", locked, actual.isLocked());
        }
        return myself;
    }
}
