package com.epam.pipeline.manager.pipeline.transfer;

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.TransferManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class LocalPathToPipelineTransferManager implements TransferManager<Path, Pipeline> {

    private static final String LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE = "/bin/bash '%s' '%s' '%s' '%s' '%s'";

    private final PipelineManager pipelineManager;
    private final UserManager userManager;
    private final CmdExecutor cmdExecutor;
    private final String transferScript;

    @Autowired
    public LocalPathToPipelineTransferManager(final PipelineManager pipelineManager,
                                              final UserManager userManager,
                                              @Value("${pipeline.local.path.transfer.script}")
                                              final String transferScript) {
        this(pipelineManager, userManager, new CmdExecutor(), transferScript);
    }

    public void transfer(final Path path, final Pipeline pipeline) {
        final PipelineUser user = userManager.loadUserByName(pipeline.getOwner());
        Assert.notNull(user, String.format("User with identifier not found: %s", pipeline.getOwner()));
        cmdExecutor.executeCommand(getTransferCommand(path, pipelineManager.getPipelineCloneUrl(pipeline.getId()),
                user.getUserName(), user.getEmail()));
    }

    private String getTransferCommand(final Path path, final String pipelineCloneUrl,
                                      final String userName, final String userEmail) {
        return String.format(LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE, transferScript, path, pipelineCloneUrl,
                userName, userEmail);
    }

}
