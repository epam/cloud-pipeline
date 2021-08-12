package com.epam.pipeline.manager.pipeline.transfer;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LocalPathToPipelineTransferManagerTest {

    private static final String TRANSFER_SCRIPT = "TRANSFER_SCRIPT";
    private static final Path PATH = Paths.get("").toAbsolutePath();
    private static final String REPO_URL = "REPO_URL";
    private static final GitCredentials GIT_CREDENTIALS = new GitCredentials(null, null, null, REPO_URL);
    private static final String USER_NAME = "USER_NAME";
    private static final String USER_EMAIL = "USER_EMAIL";
    private static final Pipeline PIPELINE = PipelineCreatorUtils.getPipeline(CommonCreatorConstants.ID, USER_NAME);
    private static final PipelineUser USER = UserCreatorUtils.getPipelineUserWithEmail(USER_NAME, USER_EMAIL);

    private final GitManager gitManager = mock(GitManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final CmdExecutor cmdExecutor = mock(CmdExecutor.class);
    private final LocalPathToPipelineTransferManager manager = new LocalPathToPipelineTransferManager(
            gitManager, userManager, messageHelper, cmdExecutor, TRANSFER_SCRIPT);

    @Before
    public void setUp() {
        doReturn(GIT_CREDENTIALS)
                .when(gitManager).getGitCredentials(CommonCreatorConstants.ID, false, false);
        doReturn(USER).when(userManager).loadUserByName(USER_NAME);
    }

    @Test
    public void transferShouldFailIfPipelineOwnerDoesNotExist() {
        doReturn(null).when(userManager).loadUserByName(USER_NAME);

        assertThrows(() -> manager.transfer(PATH, PIPELINE));
    }

    @Test
    public void transferShouldExecuteTransferScript() {
        manager.transfer(PATH, PIPELINE);

        verifyScriptArgument(0, TRANSFER_SCRIPT);
    }

    @Test
    public void transferShouldPassPathAsFirstTransferScriptArgument() {
        manager.transfer(PATH, PIPELINE);

        verifyScriptArgument(1, PATH.toString());
    }

    @Test
    public void transferShouldPassRepositoryUrlAsSecondTransferScriptArgument() {
        manager.transfer(PATH, PIPELINE);

        verifyScriptArgument(2, REPO_URL);
    }

    @Test
    public void transferShouldPassUserNameAsThirdTransferScriptArgument() {
        manager.transfer(PATH, PIPELINE);

        verifyScriptArgument(3, USER_NAME);
    }

    @Test
    public void transferShouldPassUserEmailAsFourthTransferScriptArgument() {
        manager.transfer(PATH, PIPELINE);

        verifyScriptArgument(4, USER_EMAIL);
    }

    private void verifyScriptArgument(final int position, final String argument) {
        verify(cmdExecutor).executeCommand(argThat(matches(command ->
                Arrays.stream(command.split(" "))
                        .skip(position + 1)
                        .findFirst()
                        .filter(argument::equals)
                        .isPresent())));
    }
}
