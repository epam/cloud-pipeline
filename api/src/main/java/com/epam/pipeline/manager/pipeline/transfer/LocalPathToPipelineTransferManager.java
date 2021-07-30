package com.epam.pipeline.manager.pipeline.transfer;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.TransferManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocalPathToPipelineTransferManager implements TransferManager<Path, Pipeline> {

    private static final String LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE = "/bin/bash %s %s %s %s %s";

    private final GitManager gitManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;
    private final CmdExecutor cmdExecutor;
    private final String transferScript;

    @Autowired
    public LocalPathToPipelineTransferManager(final GitManager gitManager,
                                              final UserManager userManager,
                                              final MessageHelper messageHelper,
                                              @Value("${pipeline.local.path.transfer.script}")
                                              final String transferScript) {
        this(gitManager, userManager, messageHelper, new CmdExecutor(), transferScript);
    }

    public void transfer(final Path path, final Pipeline pipeline) {
        final PipelineUser owner = getOwner(pipeline);
        cmdExecutor.executeCommand(getTransferCommand(path, getCloneUrl(pipeline),
                owner.getUserName(), owner.getEmail()));
    }

    private PipelineUser getOwner(final Pipeline pipeline) {
        return Optional.ofNullable(userManager.loadUserByName(pipeline.getOwner()))
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_USER_NAME_NOT_FOUND, pipeline.getOwner())));
    }

    private String getCloneUrl(final Pipeline pipeline) {
        return gitManager.getGitCredentials(pipeline.getId(), false, false).getUrl();
    }

    private String getTransferCommand(final Path path, final String pipelineCloneUrl,
                                      final String userName, final String userEmail) {
        return String.format(LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE, transferScript, path, pipelineCloneUrl,
                userName, userEmail);
    }

}
