/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.openqa.selenium.support.Color;

import static com.codeborne.selenide.CollectionCondition.exactTexts;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.switchTo;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static com.epam.pipeline.autotests.utils.Conditions.colorInputValue;
import static com.epam.pipeline.autotests.utils.Conditions.textColor;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;

/**
 * The "Theme Settings" pop-up of the SSH web terminal, opened by its gear icon (see {@link ShellAO#openThemeSettings}).
 * <p>
 * The pop-up is part of the terminal page itself (the edge's wetty application), not of the terminal frame, so
 * this object works in the page, and {@link #close()} switches back into the terminal frame. The elements are found by
 * their labels and texts: the page's class names are generated.
 */
public class ShellThemeSettingsAO implements AccessObject<ShellThemeSettingsAO> {

    private static final String TITLE = "Theme Settings";
    private static final String RESET_TO_DEFAULTS = "Reset to defaults";
    private static final int ANSI_COLORS = 16;

    private final ShellAO shell;

    /**
     * The values of the two themes the case uses, as the edge's wetty sets them
     * (deploy/docker/cp-edge/wetty/src/components/utils/themes.ts).
     */
    public enum Theme {
        LIGHT("Light", "#fafafa", "#333333", "rgba(50, 50, 50, 0.5)"),
        DEFAULT("Default", "rgb(16, 16, 16)", "rgb(240, 240, 240)", "rgba(255, 0, 0, 0.5)");

        public final String label;
        public final Color background;
        public final Color text;
        public final Color cursor;

        Theme(final String label, final String background, final String text, final String cursor) {
            this.label = label;
            this.background = Color.fromString(background);
            this.text = Color.fromString(text);
            this.cursor = Color.fromString(cursor);
        }
    }

    ShellThemeSettingsAO(final ShellAO shell) {
        this.shell = shell;
    }

    @Override
    public SelenideElement context() {
        return $(byXpath(format("//h2[normalize-space(.)='%s']/../..", TITLE)));
    }

    public ShellThemeSettingsAO ensureOpened() {
        context().shouldBe(visible);
        return this;
    }

    /**
     * Checks that the pop-up shows all its fields: the Theme drop-down, the Background, Text and Cursor color
     * selectors, the Bold text checkbox, the Font size and Font family selectors, the ANSI color selectors, the theme
     * preview panel and the Close button.
     */
    public ShellThemeSettingsAO ensureAllFieldsShown() {
        themeField().shouldBe(visible);
        backgroundColorField().shouldBe(visible);
        textColorField().shouldBe(visible);
        cursorColorField().shouldBe(visible);
        labelledField("Bold text", "input[@type='checkbox']").shouldBe(visible);
        labelledField("Font size", "input[@type='number']").shouldBe(visible);
        labelledField("Font family", "div/select").shouldBe(visible);
        context().$$(byXpath(".//span[normalize-space(.)='ANSI Colors:']/following-sibling::div[1]"
                + "//input[@type='color']")).shouldHave(size(ANSI_COLORS));
        previewTerminal().shouldBe(visible);
        closeButton().shouldBe(visible);
        return this;
    }

    public ShellThemeSettingsAO ensureSelectedTheme(final Theme theme) {
        themeField().getSelectedOption().shouldHave(exactText(theme.label));
        return this;
    }

    /**
     * Clicks the Theme drop-down. It is a native select, whose open list the browser draws outside the page: what
     * can be checked is the list of its options, see {@link #ensureThemesListed}.
     */
    public ShellThemeSettingsAO clickThemeField() {
        themeField().shouldBe(visible).click();
        return this;
    }

    /**
     * Checks the themes the Theme drop-down lists, in their order. Its "Select theme" placeholder is a disabled
     * option, not a theme.
     */
    public ShellThemeSettingsAO ensureThemesListed(final String... themes) {
        themeField().$$(byXpath("./option[not(@disabled)]")).shouldHave(exactTexts(themes));
        return this;
    }

    public ShellThemeSettingsAO selectTheme(final Theme theme) {
        themeField().selectOption(theme.label);
        return this;
    }

    /**
     * Checks the Background, Text and Cursor colors of the pop-up against the theme.
     * <p>
     * The Cursor color is checked as the terminal setting that its color selector shows, not as the color of the
     * selector: the themes give the cursor color as rgba(...), the pop-up passes it to the color input unconverted,
     * and an input of type color can only hold #rrggbb. Chrome 120 (e2e/gui/Dockerfile) then shows #000000 for every
     * theme; browsers that convert CSS colors themselves show the right one. Once the GUI converts the color (develop
     * does since ee36555583), the selector can be checked too. The pop-up's own
     * rendering of the cursor color, in the preview, is checked by {@link #ensurePreview}.
     */
    public ShellThemeSettingsAO ensureColors(final Theme theme) {
        backgroundColorField().shouldHave(colorInputValue(theme.background));
        textColorField().shouldHave(colorInputValue(theme.text));
        Selenide.Wait()
                .withTimeout(ofMillis(DEFAULT_TIMEOUT))
                .withMessage(format("the terminal's cursor color setting is %s", theme.cursor.asRgba()))
                .until(driver -> Objects.equals(theme.cursor, terminalCursorColor()));
        return this;
    }

    /**
     * Checks the "Theme preview" panel against the theme: the background and text colors of its terminal, and the
     * color of its cursor (a CSS background, which takes any CSS color).
     */
    public ShellThemeSettingsAO ensurePreview(final Theme theme) {
        previewTerminal().shouldHave(backgroundColor(theme.background), textColor(theme.text));
        previewCursor().shouldHave(backgroundColor(theme.cursor));
        return this;
    }

    public ShellThemeSettingsAO ensureResetToDefaultsShown() {
        resetToDefaultsButton().shouldBe(visible);
        return this;
    }

    public ShellThemeSettingsAO ensureResetToDefaultsHidden() {
        resetToDefaultsButton().shouldNot(exist);
        return this;
    }

    public ShellThemeSettingsAO resetToDefaults() {
        resetToDefaultsButton().shouldBe(visible).click();
        return this;
    }

    /**
     * Clicks the Close button and returns to the terminal frame.
     */
    public ShellAO close() {
        closeButton().shouldBe(visible).click();
        context().shouldNot(exist);
        switchTo().frame(0);
        return shell;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }

    private SelenideElement themeField() {
        return labelledField("Theme", "div/select");
    }

    private SelenideElement backgroundColorField() {
        return labelledField("Background Color", "input[@type='color']");
    }

    private SelenideElement textColorField() {
        return labelledField("Text Color", "input[@type='color']");
    }

    private SelenideElement cursorColorField() {
        return labelledField("Cursor Color", "input[@type='color']");
    }

    /**
     * The field after the label of the given text: the wetty Input and Select put the label first in their wrapper.
     */
    private SelenideElement labelledField(final String label, final String field) {
        return context().$(byXpath(format(".//label[normalize-space(.)='%s']/following-sibling::%s", label, field)));
    }

    /**
     * The terminal of the "Theme preview" panel: the box with the theme's background, under the panel's label.
     */
    private SelenideElement previewTerminal() {
        return context().$(byXpath(".//span[normalize-space(.)='Theme preview:']/following-sibling::div[1]/div[1]"));
    }

    /**
     * The cursor of the "Theme preview" panel: the box after the prompt of its last row.
     */
    private SelenideElement previewCursor() {
        return context().$(byXpath(".//span[normalize-space(.)='Theme preview:']/following-sibling::div[1]"
                + "//span[normalize-space(.)='[root@pipeline ~]#']/following-sibling::div[1]"));
    }

    private SelenideElement resetToDefaultsButton() {
        return context().$$("button").findBy(exactText(RESET_TO_DEFAULTS));
    }

    private SelenideElement closeButton() {
        return context().$$("button").findBy(exactText("Close"));
    }

    /**
     * The cursor color setting of the terminal, which the Cursor Color selector shows. The page exposes its hterm
     * terminal as window.term.
     */
    private Color terminalCursorColor() {
        final Object color = Selenide.executeJavaScript("return window.term.prefs_.get('cursor-color');");
        return color == null ? null : Color.fromString(color.toString());
    }
}
