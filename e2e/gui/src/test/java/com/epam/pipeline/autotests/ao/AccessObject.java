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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.openqa.selenium.*;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.utils.Conditions.collapsedTab;
import static com.epam.pipeline.autotests.utils.Conditions.expandedTab;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.comboboxDropdown;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.globalContext;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("unchecked")
public interface AccessObject<ELEMENT_TYPE extends AccessObject> {

    default Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }

    default SelenideElement context() {
        return $(globalContext());
    }

    default SelenideElement get(Primitive primitive) {
        return Optional.ofNullable(elements().get(primitive))
                .orElseThrow(() ->
                        new NoSuchElementException(String.format(
                                "Primitive %s is not defined in %s.", primitive, this.getClass().getSimpleName())));
    }

    default Entry entry(Primitive primitive, SelenideElement element) {
        return new Entry(primitive, element);
    }

    default Map<Primitive, SelenideElement> initialiseElements(Entry... entries) {
        return Arrays.stream(entries).collect(toMap(Entry::getPrimitive, Entry::getElement));
    }

    default Map<Primitive, SelenideElement> initialiseElements(Map<Primitive, SelenideElement> elements, Entry... entries) {
        Map<Primitive, SelenideElement> map = new HashMap<>(elements);
        Arrays.stream(entries).forEach(entry -> map.put(entry.getPrimitive(), entry.getElement()));
        return map;
    }

    default ELEMENT_TYPE click(Primitive primitive) {
        get(primitive).shouldBe(visible, enabled).click();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE click(final By qualifier) {
        context().find(qualifier).shouldBe(visible, enabled).click();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE click(final By target, final By scope) {
        $(scope).find(target).shouldBe(visible, enabled).click();
        return (ELEMENT_TYPE) this;
    }

    default <DESTINATION extends AccessObject<DESTINATION>> DESTINATION click(final By qualifier,
                                                                              final Supplier<DESTINATION> destinationSupplier
    ) {
        click(qualifier);
        return destinationSupplier.get();
    }

    default <DESTINATION extends AccessObject<DESTINATION>> DESTINATION click(final Primitive primitive,
                                                                              final Supplier<DESTINATION> destinationSupplier
    ) {
        click(primitive);
        return destinationSupplier.get();
    }

    default ELEMENT_TYPE clear(final By qualifier) {
        $(qualifier).clear();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE clear(Primitive primitive) {
        get(primitive).shouldBe(visible, enabled).clear();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE enter(Primitive primitive) {
        get(primitive).pressEnter();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE enter() {
        actions().sendKeys(Keys.ENTER).perform();
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE setValue(final By qualifier, final String value) {
        return setValue($(qualifier), value);
    }

    default ELEMENT_TYPE setValue(Primitive primitive, String value) {
        return setValue(get(primitive), value);
    }

    default ELEMENT_TYPE setValue(final SelenideElement element, final String value) {
        element.shouldBe(visible, enabled)
               .sendKeys(Keys.chord(Keys.CONTROL, "a"), value);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE addToValue(Primitive primitive, String value) {
        SelenideElement element = get(primitive).shouldBe(visible, enabled);
        element.append(value);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE hover(Primitive primitive) {
        sleep(1, SECONDS);
        get(primitive).shouldBe(visible).hover();
        sleep(1, SECONDS);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE hover(final By qualifier) {
        sleep(1, SECONDS);
        context().find(qualifier).shouldBe(visible).hover();
        sleep(1, SECONDS);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensure(final By qualifier, final Condition... conditions) {
        context().find(qualifier).should(conditions);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensure(final By target, final By globalScope, final Condition... conditions) {
        $(globalScope).find(target).shouldHave(conditions);
        return (ELEMENT_TYPE) this;
    }

    /**
     * Retrieves non-empty collections of the elements by the given qualifier
     * and apply given conditions for each one of them.
     *
     * @param qualifier of the elements to match the given conditions.
     * @param conditions which all found elements should match.
     * @return the access object on which the method was called on.
     */
    default ELEMENT_TYPE ensureAll(final By qualifier, final Condition... conditions) {
        SelenideElements.of(qualifier, context())
                .forEach(element -> element.shouldHave(conditions));
        return (ELEMENT_TYPE) this;
    }

    /**
     * Retrieves non-empty collections of the elements by the given qualifier
     * and apply given collection conditions to it.
     *
     * @param qualifier of the elements to match the given conditions.
     * @param conditions which all found elements should match.
     * @return the access object on which the method was called on.
     */
    default ELEMENT_TYPE ensureAll(final By qualifier, final CollectionCondition... conditions) {
        SelenideElements.of(qualifier, context()).shouldHave(conditions);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensure(final Primitive primitive, final Condition... conditions) {
        get(primitive).should(conditions);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensureVisible(final Primitive... elements) {
        Arrays.stream(elements).forEach(el -> ensure(el, visible));
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensureNotVisible(final Primitive... elements) {
        Arrays.stream(elements).forEach(el -> ensure(el, not(visible)));
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE ensureAll(final Condition condition, final Primitive... elements) {
        Arrays.stream(elements).forEach(el -> ensure(el, condition));
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE expandTab(final By qualifier) {
        final SelenideElement selenideElement = $(qualifier);
        selenideElement.should(exist);
        if (selenideElement.is(collapsedTab)) {
            selenideElement.click();
        }
        selenideElement.shouldBe(expandedTab);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE expandTabs(final By... qualifiers) {
        Arrays.stream(qualifiers).forEach(this::expandTab);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE expandTab(final Primitive element) {
        final SelenideElement selenideElement = get(element);
        selenideElement.should(exist);
        if (selenideElement.is(collapsedTab)) {
            selenideElement.click();
        }
        selenideElement.shouldBe(expandedTab);
        return (ELEMENT_TYPE) this;
    }

    /**
     * @deprecated Use {@link #expandTabs(By...)} instead.
     */
    @Deprecated
    default ELEMENT_TYPE expandTabs(final Primitive... elements) {
        Arrays.stream(elements).forEach(this::expandTab);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE sleep(final long duration, final TimeUnit units) {
        Utils.sleep(duration, units);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE screenshot(final String fileName) {
        Selenide.screenshot(fileName);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE refresh() {
        Selenide.refresh();
        return (ELEMENT_TYPE) this;
    }

    /**
     * Reset the web driver mouse pointer by clicking on the cloud pipeline logo.
     *
     * It can be useful while hovering on different elements.
     * Notice: the method doesn't work if any element is lying above the logo (f.e. popup overlay).
     */
    default ELEMENT_TYPE resetMouse() {
        sleep(300, MILLISECONDS);
        $(byId("navigation-button-logo")).shouldBe(visible).click();
        sleep(300, MILLISECONDS);
        $(byId("navigation-button-logo")).shouldBe(visible).click();
        sleep(2, SECONDS);
        return (ELEMENT_TYPE) this;
    }

    /**
     * Performs one of the given actions. If {@code qualifier} meets {@code condition}
     * then {@code positiveAction} will be performed, otherwise {@code negativeAction}
     * will be performed instead.
     *
     * @param qualifier Qualifier of the element to test the given {@code condition}.
     * @param condition Condition to be tested.
     * @param positiveAction The action to be performed if qualifier {@code qualifier} meets {@code condition}.
     * @param negativeAction The action to be performed if qualifier {@code qualifier} doesn't meet {@code condition}.
     * @return The origin access object for chaining purpose.
     */
    default ELEMENT_TYPE performIf(final By qualifier,
                                   final Condition condition,
                                   final Consumer<ELEMENT_TYPE> positiveAction,
                                   final Consumer<ELEMENT_TYPE> negativeAction) {
        return performIf(context().find(qualifier).has(condition), positiveAction, negativeAction);
    }

    default ELEMENT_TYPE performIf(final By qualifier,
                                   final Condition condition,
                                   final Consumer<ELEMENT_TYPE> action) {
        return performIf(context().find(qualifier).has(condition), action);
    }

    default ELEMENT_TYPE performIf(final Primitive primitive,
                                   final Condition condition,
                                   final Consumer<ELEMENT_TYPE> action) {
        return performIf(get(primitive).has(condition), action);
    }

    default ELEMENT_TYPE performIf(final boolean needed,
                                   final Consumer<ELEMENT_TYPE> action) {
        return performIf(needed, action, element -> {});
    }

    default ELEMENT_TYPE performIf(final boolean needed,
                                   final Consumer<ELEMENT_TYPE> positiveAction,
                                   final Consumer<ELEMENT_TYPE> negativeAction) {
        if (needed) {
            positiveAction.accept((ELEMENT_TYPE) this);
        } else {
            negativeAction.accept((ELEMENT_TYPE) this);
        }
        return (ELEMENT_TYPE) this;
    }

    /**
     * @see #performWhile(SelenideElement, Condition, Consumer)
     */
    default ELEMENT_TYPE performWhile(final By qualifier,
                                      final Condition condition,
                                      final Consumer<ELEMENT_TYPE> action) {
        return performWhile($(qualifier), condition, action);
    }

    /**
     * @see #performWhile(SelenideElement, Condition, Consumer)
     */
    default ELEMENT_TYPE performWhile(final Primitive primitive,
                                      final Condition condition,
                                      final Consumer<ELEMENT_TYPE> action) {
        return performWhile(get(primitive), condition, action);
    }

    /**
     * Perform the given {@code action} while {@code element} meets the given {@code condition}.
     *
     * It has limitation of one hundred performs. If the {@code element} meets the given {@code condition}
     * more than the limit times then the exception is thrown.
     *
     * @param element The primitive to check condition on.
     * @param condition The condition to be checked.
     * @param action The action to perform with the origin access object.
     * @return The origin access object for chaining purpose.
     * @throws RuntimeException if the {@code element} meets {@code condition} more than one hundred times.
     */
    default ELEMENT_TYPE performWhile(final SelenideElement element,
                                      final Condition condition,
                                      final Consumer<ELEMENT_TYPE> action) {
        final int threshold = 100;
        int times = 0;
        while (element.has(condition)) {
            times += 1;
            if (times > threshold) {
                throw new RuntimeException(String.format(
                        "%s matches the condition %s over than %d times.", element, condition, threshold
                ));
            }
            action.accept((ELEMENT_TYPE) this);
        }
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE messageShouldAppear(String message) {
        $(withText(message)).should(appear);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE messageShouldNotContain(String... unexpectedTexts) {
        final SelenideElement message = $(byClassName("ant-message")).shouldBe(visible);
        Arrays.stream(unexpectedTexts)
                .map(unexpectedText -> message.text().contains(unexpectedText))
                .filter(contains -> contains)
                .findAny()
                .ifPresent(el -> {
                    screenshot("invalid-message-screenshot");
                    throw new RuntimeException();
                });
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE messageShouldAppear(String message, long timeout) {
        $(withText(message)).waitUntil(appears, timeout);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE also(final Consumer<ELEMENT_TYPE> consumer) {
        consumer.accept((ELEMENT_TYPE) this);
        return (ELEMENT_TYPE) this;
    }

    default ELEMENT_TYPE also(final Runnable runnable) {
        runnable.run();
        return (ELEMENT_TYPE) this;
    }

    /**
     * Performs action in another tab that is duplicated from the original one.
     *
     * @param action The action to perform in a new tab.
     * @return The origin access object for chaining purpose.
     */
    default ELEMENT_TYPE inAnotherTab(final Consumer<ELEMENT_TYPE> action) {
        final WebDriver driver = WebDriverRunner.getWebDriver();
        final ELEMENT_TYPE itself = (ELEMENT_TYPE) this;
        final String mainWindow = driver.getWindowHandle();
        final String duplicatingTab = String.format("window.open('%s', '_blank')", driver.getCurrentUrl());
        final Set<String> oldWindowHandles = new HashSet<>(driver.getWindowHandles());
        Selenide.executeJavaScript(duplicatingTab);
        final String openedWindow = driver.getWindowHandles().stream()
                                          .filter(handle -> !oldWindowHandles.contains(handle))
                                          .findFirst()
                                          .orElseThrow(() -> new NoSuchWindowException(String.format(
                                              "No new window opened {%s}.", Arrays.toString(oldWindowHandles.toArray())
                                          )));
        driver.switchTo().window(openedWindow);
        action.accept(itself);
        driver.close();
        driver.switchTo().window(mainWindow);
        return itself;
    }

    /**
     * Clicks to the given {@code combobox} an then selects the given {@code option} in the appeared drop down list.
     *
     * @param combobox Combobox qualifier.
     * @param option Option to be selected (not exact string, f.e. substring).
     * @return The origin access object.
     */
    default ELEMENT_TYPE selectValue(final By combobox, final String option) {
        return selectValue(combobox, withText(option));
    }

    /**
     * Clicks to the given {@code combobox} an then selects an option by the given {@code optionQualifier} in the appeared drop down list.
     *
     * @param combobox Combobox qualifier.
     * @param optionQualifier Qualifier to be selected.
     * @return The origin access object.
     */
    default ELEMENT_TYPE selectValue(final By combobox, final By optionQualifier) {
        context().find(combobox).shouldBe(visible).click();
        $(comboboxDropdown()).find(optionQualifier).shouldBe(visible).click();
        return (ELEMENT_TYPE) this;
    }

    /**
     * Clicks to the given {@code combobox} and then selects the given {@code option} in the appeared drop down list.
     *
     * @param combobox Combobox primitive object.
     * @param option Option to be selected (not exact string, f.e. substring).
     * @return The origin access object.
     */
    default ELEMENT_TYPE selectValue(final Primitive combobox, final String option) {
        return selectValue(combobox, withText(option));
    }

    /**
     * Clicks to the given {@code combobox} an then selects an option by the given {@code optionQualifier} in the appeared drop down list.
     *
     * @param combobox Combobox primitive object.
     * @param optionQualifier Qualifier to be selected.
     * @return The origin access object.
     */
    default ELEMENT_TYPE selectValue(final Primitive combobox, final By optionQualifier) {
        get(combobox).shouldBe(visible).click();
        $(visible(byClassName("ant-select-dropdown"))).find(optionQualifier).shouldBe(visible).click();
        return (ELEMENT_TYPE) this;
    }

    class Entry {
        private final Primitive primitive;
        private final SelenideElement selenideElement;

        public Entry(Primitive primitive, SelenideElement selenideElement) {
            this.primitive = primitive;
            this.selenideElement = selenideElement;
        }

        public Primitive getPrimitive() {
            return primitive;
        }

        public SelenideElement getElement() {
            return selenideElement;
        }
    }
}
