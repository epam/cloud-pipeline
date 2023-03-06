/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.mixins;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.AuthenticationPageAO;
import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Permission;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.Cookie;

import java.util.Arrays;
import java.util.List;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.Collections.singletonList;
import static org.openqa.selenium.By.tagName;

public interface Authorization extends Navigation {
    Account admin = new Account(C.LOGIN, C.PASSWORD);
    Account user = new Account(C.ANOTHER_LOGIN, C.ANOTHER_PASSWORD);
    Account userWithoutCompletedRuns = new Account(C.CLEAN_HISTORY_LOGIN, C.CLEAN_HISTORY_PASSWORD);

    // Delay was added in order to prevent page from auto-refreshing (in milliseconds)
    long LOGIN_DELAY = C.LOGIN_DELAY_TIMEOUT;

    default void givePermissions(Account account, Permission... permissions) {
        Arrays.stream(permissions).forEachOrdered(p -> p.set(getUserNameByAccountLogin(account.login)));
    }

    default void givePermissions(String groupName, Permission... permissions) {
        Arrays.stream(permissions).forEachOrdered(p -> p.set(groupName));
    }

    default NavigationMenuAO loginAs(Account account) {
        if (impersonateMode()) {
            if (C.LOGIN.equalsIgnoreCase(account.login)) {
                return new NavigationMenuAO();
            }
            impersonateAs(account.login);
            return new NavigationMenuAO();
        }
        sleep(LOGIN_DELAY);
        if ("false".equals(C.AUTH_TOKEN)) {
            return new AuthenticationPageAO()
                    .login(account.login)
                    .password(account.password)
                    .signIn();
        }
        Cookie cookie = new Cookie("HttpAuthorization", account.password);
        WebDriverRunner.getWebDriver().manage().addCookie(cookie);
        open(C.ROOT_ADDRESS);
        return new NavigationMenuAO();
    }

    default void logout() {
        if (impersonateMode()) {
            if (checkImpersonation()) {
                stopImpersonation();
            }
            return;
        }
        sleep(LOGIN_DELAY);
        new NavigationMenuAO().logout();
    }

    default void logoutIfNeededAndPerform(Runnable runnable) {
        open(C.ROOT_ADDRESS);
        try {
            logout();
        } catch (Throwable e) {
            // User has already performed logout
        } finally {
            runnable.run();
        }
    }

    default void loginAsAdminAndPerform(Runnable runnable) {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            runnable.run();
        });
    }

    default void logoutIfNeeded() {
        open(C.ROOT_ADDRESS);
        try {
            logout();
        } catch (Throwable e) {
            // User has already performed logout
        }
    }

    default void addAccountToStoragePermissions(Account account, String storage, String... folders) {
        PipelinesLibraryAO library = library();

        Arrays.stream(folders).forEachOrdered(library::cd);

        library.selectStorage(storage)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(account.login)
                .closeAll();
    }

    default void addAccountToFolderPermissions(Account account, String root, String... folders) {
        PipelinesLibraryAO library = library().cd(root);

        Arrays.stream(folders).forEachOrdered(library::cd);

        library.clickOnFolder(folders.length == 0 ? root : folders[folders.length - 1])
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(account.login)
                .closeAll();
    }

    default void addAccountToPipelinePermissions(Account account, String pipelineName) {
        library()
                .sleep(2, SECONDS)
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(account.login)
                .closeAll();
    }

    default void addAccountToConfigurationPermissions(Account account, String configName) {
        library()
                .configurationWithin(configName, configuration ->
                        configuration
                                .edit(conf -> conf.permission().addNewUser(account.login).closeAll())
                );
    }

    default String getUserNameByAccountLogin(final String login) {
        return login.split("@")[0];
    }

    default void validateErrorPage(final List<String> messages) {
        messages.forEach(message -> $(withText(message)).should(appear));
    }

    default void loginBack() {
        $$(byCssSelector("a")).find(text(format("login back to the %s", C.PLATFORM_NAME))).shouldBe(enabled).click();
    }

    default boolean impersonateMode() {
        return "true".equalsIgnoreCase(C.IMPERSONATE_AUTH);
    }

    default void checkFailedAuthentication() {
        $(tagName("body")).shouldBe(empty);
    }

    default void validateWhileErrorPageMessage() {
        if (impersonateMode()) {
            navigationMenu()
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(admin.login);
            return;
        }
        if ("true".equals(C.AUTH_TOKEN)) {
            validateErrorPage(singletonList("User is blocked!"));
            Selenide.clearBrowserCookies();
            Utils.sleep(1, SECONDS);
            return;
        }
        validateErrorPage(Arrays.asList(
                "Please contact", "Support team", "to request the access",
                format("login back to the %s", C.PLATFORM_NAME),
                "if you already own an account")
        );
        loginBack();
    }

    class Account {
        public final String login;
        public final String password;

        public Account(String login, String password) {
            this.login = login;
            this.password = password;
        }
    }
}
