package com.epam.pipeline.autotests.ao;

import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;

public class AuthAzurePageAO {
    public AuthAzurePageAO login(String login) {
        $(byId("i0116"))
                .shouldBe(visible)
                .setValue(login)
                .shouldHave(value(login));
        return this;
    }

    public NavigationMenuAO signIn() {
        $(byId("idSIButton9"))
                .shouldBe(visible)
                .click();

        return new NavigationMenuAO();
    }
}
