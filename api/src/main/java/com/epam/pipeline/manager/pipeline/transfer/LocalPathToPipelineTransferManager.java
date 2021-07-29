package com.epam.pipeline.manager.pipeline.transfer;

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.TransferManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class LocalPathToPipelineTransferManager implements TransferManager<Path, Pipeline> {

    private static final String LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE = "/bin/bash %s '%s' '%s'";

    private final CmdExecutor cmdExecutor;
    private final String transferScript;

    @Autowired
    public LocalPathToPipelineTransferManager(@Value("${pipeline.local.path.transfer.script}")
                                              final String transferScript) {
        this(new CmdExecutor(), transferScript);
    }

    public void transfer(final Path path, final Pipeline pipeline) {
//        todo: Use git credentials
        cmdExecutor.executeCommand(getTransferCommand(path, pipeline));
    }

    private String getTransferCommand(final Path path, final Pipeline pipeline) {
        return String.format(LOCAL_PATH_TO_PIPELINE_TRANSFER_COMMAND_TEMPLATE,
                transferScript, path, pipeline.getRepository());
    }
}
