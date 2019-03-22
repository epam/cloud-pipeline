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

import com.epam.dockercompscan.AbstractSpringTest;
import com.epam.dockercompscan.dockerregistry.DockerRegistryService;
import com.epam.dockercompscan.owasp.DependencyCheckServiceTest;
import com.epam.dockercompscan.scan.domain.LayerScanResult;
import com.epam.dockercompscan.scan.domain.ScanRequest;
import com.epam.dockercompscan.util.LayerKey;
import com.epam.dockercompscan.util.LayerScanCache;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ScanServiceTest extends AbstractSpringTest {

    private static final int SLEEP_PERIOD = 2_000;
    private static final int BUFFER_SIZE = 1_000_000;
    private ClassLoader classLoader = DependencyCheckServiceTest.class.getClassLoader();

    private ScanRequest headTestLayer;
    private ScanRequest childTestLayer;

    @MockBean
    private DockerRegistryService registryService;

    @Autowired
    private ScanService scanService;

    @Autowired
    private LayerScanCache layerScanCache;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        headTestLayer = getLayer("sha256:test", "http://false-docker.io", null);
        childTestLayer = getLayer("sha256:test2", "http://false-docker.io", headTestLayer.getLayer().getName());
        mockDockerLayer("owasp/analyzer/negative", headTestLayer);
        mockDockerLayer("owasp/analyzer/positive", childTestLayer);
    }

    @Test
    public void scanServiceWorksProperlyTest() {
        scanService.scan(headTestLayer);
        scanService.scan(childTestLayer);

        List<LayerScanResult> layers = scanService.loadImageScan(childTestLayer.getLayer().getName()).getLayers();

        Assert.assertEquals(2, layers.size());
        LayerScanResult child = layers.get(0);
        Assert.assertFalse(child.getDependencies().isEmpty());

        Assert.assertNotNull(layerScanCache.getIfPresent(LayerKey.withName(childTestLayer.getLayer().getName())));
        Assert.assertNotNull(layerScanCache.getIfPresent(
                LayerKey.withParent(childTestLayer.getLayer().getParentName())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void scanServiceCleanupCacheTest() throws InterruptedException {
        scanService.scan(headTestLayer);
        scanService.scan(childTestLayer);

        List<LayerScanResult> layers = scanService.loadImageScan(childTestLayer.getLayer().getName()).getLayers();

        Assert.assertEquals(2, layers.size());

        Thread.sleep(SLEEP_PERIOD);
        layerScanCache.cleanUp();

        scanService.loadImageScan(headTestLayer.getLayer().getName());
    }

    private static ScanRequest getLayer(String name, String path, String parentName) {
        ScanRequest.Layer layer = new ScanRequest.Layer();
        layer.setName(name);
        layer.setPath(path);
        layer.setParentName(parentName);
        ScanRequest scanRequest = new ScanRequest();
        scanRequest.setLayer(layer);
        return scanRequest;
    }


    private void mockDockerLayer(String s, ScanRequest headTestLayer) throws IOException, URISyntaxException {
        try (ByteArrayOutputStream layer = new ByteArrayOutputStream(BUFFER_SIZE);
            TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(layer))) {
            Files.walk(Paths.get(classLoader.getResource(s).toURI()))
                 .filter(Files::isRegularFile)
                 .forEach(path -> {
                     File file = path.toFile();
                     TarArchiveEntry entry = new TarArchiveEntry(file, file.getName());
                     entry.setSize(file.length());
                     try {
                         tar.putArchiveEntry(entry);
                         tar.write(IOUtils.toByteArray(new FileInputStream(file)));
                         tar.closeArchiveEntry();
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 });
            tar.close();

            Mockito.when(registryService.getDockerLayerBlob(headTestLayer.getLayer()))
                    .thenReturn(new ByteArrayInputStream(layer.toByteArray()));
        }
    }
}