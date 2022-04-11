package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.cmd.PipelineCLI;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import com.epam.pipeline.dts.sync.service.TunnelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CloudPipelineTunnelService implements TunnelService {

    private final PreferenceService preferenceService;
    private final PipelineCLI tunnelPipelineCLI;
    private final String outputHost;
    private final String outputPort;

    public CloudPipelineTunnelService(final PreferenceService preferenceService,
                                      final PipelineCLI tunnelPipelineCLI,
                                      @Value("${dts.server.host:127.0.0.1}")
                                      final String outputHost,
                                      @Value("${dts.server.port:9997}")
                                      final String outputPort) {
        this.preferenceService = preferenceService;
        this.tunnelPipelineCLI = tunnelPipelineCLI;
        this.outputHost = outputHost;
        this.outputPort = outputPort;
    }

    @Override
    @Scheduled(fixedDelayString = "${dts.tunnel.poll:60000}")
    public void tunnel() {
        if (preferenceService.isTunnelEnabled()) {
            log.debug("Starting tunnel...");
            final String tunnelHost = preferenceService.getTunnelHost()
                    .orElseThrow(() -> new RuntimeException("Tunnel host is not configured"));
            final String tunnelPort = preferenceService.getTunnelOutputPort()
                    .orElseThrow(() -> new RuntimeException("Tunnel port is not configured"));
            tunnelPipelineCLI.transmitTunnel(tunnelHost, tunnelPort, outputHost, outputPort);
        }
    }
}
