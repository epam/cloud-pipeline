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
package com.epam.pipeline.autotests.mixins;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.AuthenticationPageAO;
import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Permission;
import org.openqa.selenium.Cookie;

import java.util.Arrays;

import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;

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
        sleep(LOGIN_DELAY);
        Cookie cookie = new Cookie("HttpAuthorization", account.password);
        WebDriverRunner.getWebDriver().manage().addCookie(cookie);
        Selenide.open(C.ROOT_ADDRESS);
        return new NavigationMenuAO();
    }

    default AuthenticationPageAO logout() {
        sleep(LOGIN_DELAY);
        return new NavigationMenuAO()
                .logout();
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
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(account.login)
                .closeAll();
    }

    default String getUserNameByAccountLogin(final String login) {
        return login.replaceAll("_", " ").split("@")[0];
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
