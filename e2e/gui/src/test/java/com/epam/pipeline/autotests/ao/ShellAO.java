/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.openqa.selenium.Keys;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.codeborne.selenide.Selenide.switchTo;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ShellAO implements AccessObject<ShellAO> {

    public static ShellAO open(String link) {
        Selenide.open(link);
        return open();
    }

    private static ShellAO open() {
        switchTo().frame(0);
        return new ShellAO();
    }

    public ShellAO assertPageContains(String text) {
        $(withText(text)).shouldBe(visible);
        return this;
    }

    public ShellAO assertPageContains(String text1, String text2) {
        $(withText(text1)).shouldHave(text(text2));
        return this;
    }

    public ShellAO assertPageDoesNotContain(String text) {
        $(withText(text)).shouldNotBe(visible);
        return this;
    }

    public ShellAO execute(String command) {
        sleep(300, MILLISECONDS);
        Utils.sendKeysWithSlashes(command);
        sleep(300, MILLISECONDS);
        actions().sendKeys(Keys.ENTER).perform();
        return this;
    }

    public ShellAO assertOutputContains(String... messages) {
        Arrays.stream(messages)
                .forEach(this::assertPageContains);
        return this;
    }

    public ShellAO assertOutputDoesNotContain(String... messages) {
        Arrays.stream(messages)
                .forEach(this::assertPageDoesNotContain);
        return this;
    }

    public ShellAO assertPageContainsString(String str) {
        context().shouldHave(text(str));
        return this;
    }

    public ShellAO assertNextStringIsVisible(String str1, String str2) {
        $(withText(str1)).shouldBe(visible).parent()
                .$(byXpath(format("following::x-row[contains(text(), '%s')]", str2))).shouldBe(visible);
        return this;
    }

    public NavigationMenuAO assertAccessIsDenied() {
        assertPageContains("Permission denied");
        return close();
    }

    public NavigationMenuAO close() {
        Selenide.open(C.ROOT_ADDRESS);
        return new NavigationMenuAO();
    }

    public ShellAO waitUntilTextAppears(final String runId) {
        for (int i = 0; i < 2; i++) {
            sleep(10, SECONDS);
            if ($(withText(format("pipeline-%s", runId))).exists()) {
                break;
            }
            sleep(1, MINUTES);
            refresh();
            close();
            sleep(5, SECONDS);
            new NavigationMenuAO().runs().showLog(runId).clickOnSshLink();
        }
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }
}
