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
package com.epam.pipeline.autotests.utils;

import com.codeborne.selenide.ElementsCollection;

import com.codeborne.selenide.SelenideElement;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

public interface SelenideElements {

    int MAX_ATTEMPTS = 100;

    static ElementsCollection of(final By qualifier, final SelenideElement scope) {
        final By absoluteQualifier = new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return scope.findElements(qualifier);
            }

            @Override
            public String toString() {
                return String.format("qualifier in the scope of a selenide element.%nQualifier: %s.%nScope:%s.",
                        qualifier, scope);
            }
        };
        return of(absoluteQualifier);
    }

    /**
     * Returns non-empty lazy selenide elements collection by the given {@code qualifier}.
     *
     * @param qualifier Of the elements of the collection.
     * @return Non-empty lazy selenide elements collection.
     */
    static ElementsCollection of(final By qualifier) {
        return of(qualifier, 1);
    }

    /**
     * Returns lazy selenide elements collection by the given {@code qualifier}.
     *
     * @param qualifier Of the elements of the collection.
     * @param minimumSize Of the collection to be return.
     * @return Lazy selenide elements collection with at least {@code minimumSize}.
     */
    static ElementsCollection of(final By qualifier,
                                 final int minimumSize
    ) {
        return collection(qualifier, size -> size >= minimumSize, String.format("greater than %d", minimumSize));
    }

    /**
     * Returns lazy selenide elements collection by the given {@code qualifier}.
     *
     * @param qualifier Of the elements of the collection.
     * @param exactSize Of the collection to be return.
     * @return Lazy selenide elements collection with {@code exactSize}.
     */
    static ElementsCollection exact(final By qualifier,
                                    final int exactSize
    ) {
        return collection(qualifier, size -> size == exactSize, String.valueOf(exactSize));
    }

    static ElementsCollection collection(final By qualifier,
                                         final IntPredicate sizePredicate,
                                         final String expectedMessage
    ) {
        Objects.requireNonNull(qualifier, "Collection qualifier should be an object.");
        Objects.requireNonNull(qualifier, "Size predicate should be an object.");

        return new ElementsCollection(new LazyWebElementsCollection(
                qualifier,
                sizePredicate,
                MAX_ATTEMPTS,
                expectedMessage
        ));
    }

}