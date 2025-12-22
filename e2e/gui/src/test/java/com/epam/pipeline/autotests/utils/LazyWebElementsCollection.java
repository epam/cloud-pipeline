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

import com.codeborne.selenide.Driver;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.WebDriverRunner;

import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

import static com.codeborne.selenide.impl.Alias.NONE;
import com.codeborne.selenide.impl.Alias;
import com.codeborne.selenide.impl.CollectionSource;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.screenshot;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

import javax.annotation.Nonnull;

class LazyWebElementsCollection implements CollectionSource {

    private final IntPredicate collectionSizeValidator;
    private final By elementsQualifier;
    private final int maxAttempts;
    private final String expectedSize;
    private ElementsCollection elements = null;
    private Alias alias = NONE;

    public LazyWebElementsCollection(final By elementsQualifier,
                                     final IntPredicate sizePredicate,
                                     final int maxAttempts,
                                     final String expectedSize
    ) {
        super();
        this.elementsQualifier = elementsQualifier;
        this.collectionSizeValidator = sizePredicate;
        this.maxAttempts = maxAttempts;
        this.expectedSize = expectedSize;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WebElement> getElements() {
        return (List)
                Optional.ofNullable(elements)
                        .orElseGet(() -> {
                            this.elements = retrieveElements();
                            return this.elements;
                        });
    }

    @Nonnull
    @Override
    public WebElement getElement(int index) {
        return getElements().get(index);
    }

    @Nonnull
    @Override
    public String getSearchCriteria() {
        return "$$(" + getElements().size() + " elements)";
    }

    private ElementsCollection retrieveElements() {
        int attempt = 0;

        while (attempt < maxAttempts) {
            sleep(1, SECONDS);

            final ElementsCollection elementsCollection = $$(elementsQualifier);

            if (collectionSizeValidator.test(elementsCollection.size())) {
                return elementsCollection;
            } else {
                attempt += 1;
            }
        }

        final ElementsCollection elementsCollection = $$(elementsQualifier);
        final String screenShotFile = screenshot("screenshot-" + Utils.randomSuffix());

        throw new RuntimeException(
                String.format("Elements collection of {%s} has mismatched size.%n" +
                                "Expected size: %s.%n" +
                                "Actual size: %s.%n" +
                                "Screenshot: %s.",
                        elementsQualifier.toString(),
                        expectedSize,
                        elementsCollection.size(),
                        screenShotFile
                )
        );
    }

    public List<WebElement> getActualElements() {
        return getElements();
    }

    @Override
    public String description() {
        return "lazy loaded elements";
    }

    @Override
    public Driver driver() {
        return (Driver) WebDriverRunner.getWebDriver();
    }

    @Nonnull
    @Override
    public Alias getAlias() {
        return alias;
    }

    @Override
    public void setAlias(String alias) {
        this.alias = new Alias(alias);
    }
}