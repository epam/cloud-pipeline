package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static java.lang.String.format;

public class RBACTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String toolEndpoint = testingTool.substring(testingTool.lastIndexOf("/") + 1);

    @Test
    @TestCase(value = "EPMCMBIBPC-3014")
    public void authenticationInPlatform() {
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToCLI()
                .switchGitCLI()
                .ensureCodeHasText(format("git config --global user.name \"%s\"", admin.login));
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3015")
    public void failedAuthentication() {
        logout();
        final Account wrongAccount = new Account(C.LOGIN, String.format("%s###", C.PASSWORD));
        loginAs(wrongAccount);
        if ("true".equals(C.AUTH_TOKEN)) {
            validateErrorPage("type=Unauthorized, status=401");
            return;
        }
        validateErrorPage("Incorrect user name or password");
    }

    @Test
    @TestCase(value = "EPMCMBIBPC-3169")
    public void checkAnonymousAccess() {
        if ("false".equals(C.AUTH_TOKEN)) {
            return;
        }
        logoutIfNeeded();
        loginAs(admin);
        String endpoint = tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .launchTool(this, toolEndpoint)
                .show(getLastRunId())
                .waitForInitializeNode(getLastRunId())
                .clickEndpoint()
                .getEndpoint();
        logout();
        ShellAO.open(endpoint)
                .assertPageContains("type=Unauthorized, status=401")
                .close();
        loginAs(admin);
        runsMenu()
                .log(getLastRunId(), log -> log
                        .shareWithGroup("ROLE_ANONYMOUS_USER")
                        .validateShareLink("role_anonymous_user")
        );
        logout();
        ShellAO.open(endpoint).also(() -> {
            final Account idpAccount = new Account(C.ANONYMOUS_NAME, C.ANONYMOUS_TOKEN);
            loginAs(idpAccount);
            new ToolPageAO(endpoint).validateEndpointPage(C.ANONYMOUS_NAME).closeTab();
        });
    }
}
