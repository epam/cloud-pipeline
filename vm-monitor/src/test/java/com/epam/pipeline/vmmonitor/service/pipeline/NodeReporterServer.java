/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.pipeline;

import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A local HTTP server that answers every request with the same JSON body from the test resources, as the node
 * reporter of a node answers the VM monitor.
 */
public final class NodeReporterServer implements AutoCloseable {

    private final HttpServer server;

    private NodeReporterServer(final HttpServer server) {
        this.server = server;
    }

    public static NodeReporterServer start(final String resource) throws IOException {
        final byte[] body = read(resource);
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        return new NodeReporterServer(server);
    }

    public String getHost() {
        return server.getAddress().getAddress().getHostAddress();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static byte[] read(final String resource) throws IOException {
        try (InputStream stream = NodeReporterServer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Test resource is not found: " + resource);
            }
            return IOUtils.toByteArray(stream);
        }
    }
}
