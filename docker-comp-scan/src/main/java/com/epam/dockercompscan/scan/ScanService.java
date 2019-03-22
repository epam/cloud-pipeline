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

package com.epam.dockercompscan.scan;

import com.epam.dockercompscan.scan.domain.Dependency;
import com.epam.dockercompscan.dockerregistry.DockerRegistryService;
import com.epam.dockercompscan.owasp.DependencyCheckService;
import com.epam.dockercompscan.scan.domain.ImageScanResult;
import com.epam.dockercompscan.scan.domain.LayerScanResult;
import com.epam.dockercompscan.scan.domain.ScanRequest;
import com.epam.dockercompscan.util.LayerKey;
import com.epam.dockercompscan.util.LayerScanCache;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import javax.annotation.PostConstruct;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;

@Service
public class ScanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanService.class);

    @Autowired
    private DependencyCheckService checkService;

    @Autowired
    private DockerRegistryService dockerRegistryService;

    @Value("${worker.threads.count:4}")
    private int numberOfScanningThreads;

    @Value("${base.working.dir}")
    private String baseWorkingDir;

    @Autowired
    private LayerScanCache layerScanCache;

    private Semaphore scanSlots;

    @PostConstruct
    public void init() {
        scanSlots = new Semaphore(numberOfScanningThreads);
    }

    public ImageScanResult loadImageScan(String id) {
        id = URLDecoder.decode(id);
        List<LayerScanResult> layers = new ArrayList<>();
        LOGGER.debug("Load info for image by Id layer: " + id);
        @Nullable LayerScanResult lastLayer = layerScanCache.getIfPresent(LayerKey.withName(id));

        if (lastLayer == null) {
            LOGGER.debug("Layer: " + id + " not found!");
            throw new IllegalArgumentException("Layer with id: " + id + " not found!");
        }

        layers.add(lastLayer);
        while (lastLayer.getParentId() != null) {
            String layerParentId = lastLayer.getParentId();
            String layerId = lastLayer.getLayerId();
            LOGGER.debug("Load info for layer: " + layerParentId + ". " + "As parent for layer: " + layerId);

            lastLayer = layerScanCache.getIfPresent(LayerKey.withName(layerParentId));
            if (lastLayer == null) {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Layer with id: " + layerParentId +
                        " not found, but it should be since it's parent for layer: " + layerId);
            }
            layers.add(lastLayer);
        }

        return new ImageScanResult(id, layers);
    }

    public LayerScanResult scan(final ScanRequest request) {
        LOGGER.debug("Register ScanRequset with layer: " + request.getLayer());
        ScanRequest.Layer toScan = request.getLayer();

        LayerKey cacheKey = LayerKey.create(toScan.getName(), toScan.getParentName());
        LayerScanResult result = layerScanCache.getIfPresent(cacheKey);

        if (result == null || result.getStatus() == LayerScanResult.Status.FAILURE) {
            result = new LayerScanResult(toScan.getName(), LayerScanResult.Status.RUNNING, toScan.getParentName());
            layerScanCache.put(cacheKey, result);

            File outputFolder = new File(baseWorkingDir, toScan.getName());
            try {
                scanSlots.acquire();
                Files.createDirectory(Paths.get(outputFolder.getPath()));
                fetchLayer(toScan, outputFolder);
                List<Dependency> dependencies = checkService.runScan(outputFolder);
                dependencies.forEach(d -> d.setLayerId(toScan.getName()));
                result.setDependencies(dependencies);
                result.setStatus(LayerScanResult.Status.SUCCESSFUL);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
                result.setStatus(LayerScanResult.Status.FAILURE);
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error(e.getMessage(), e);
                result.setStatus(LayerScanResult.Status.FAILURE);
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            } finally {
                FileUtils.deleteQuietly(outputFolder);
                scanSlots.release();
            }
        }
        return result;
    }

    private void fetchLayer(ScanRequest.Layer layerToScan, File layerFolder) throws IOException {

        GZIPInputStream gzipInputStream = new GZIPInputStream(
                new BufferedInputStream(dockerRegistryService.getDockerLayerBlob(layerToScan)));

        LOGGER.debug("Unpack layer: " + layerToScan.getName() + " into: " + layerFolder.getAbsolutePath());
        try (ArchiveInputStream tarStream = new TarArchiveInputStream(gzipInputStream)) {
            ArchiveEntry entry;
            while ((entry = tarStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                final File entryFile = new File(layerFolder, entryName);
                if (entry.isDirectory()) {
                    Files.createDirectory(entryFile.toPath());
                } else {
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(entryFile))) {
                        IOUtils.copy(tarStream, out);
                    }
                }
            }
            LOGGER.debug("Successfully unpack layer: " + layerToScan.getName());
        }
    }
}
