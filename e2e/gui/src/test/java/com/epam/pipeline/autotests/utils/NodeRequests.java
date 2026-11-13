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
package com.epam.pipeline.autotests.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Asks the helper of run-e2e.sh (scripts/node-requests.sh) to do on the node of a run what the tests container can't
 * do, since it has no access to the cluster or to the nodes: label the node, so that the VM monitor checks the open
 * files of its processes, or remove that label; stop the kubelet of the node, so that the node stops reporting to
 * Kubernetes, or start it again. A request is a file written into a folder that run-e2e.sh mounts writable
 * (e2e.ui.node.requests.dir), and the helper, which runs on the node of run-e2e.sh, answers with another file. It also
 * replaces the file ".watcher" there every 2 s, which shows that it runs.
 * <p>
 * The helper acts only on the node of a RUNNING run of a tests account, records the node, and reverts what it did there
 * after the tests if the test did not.
 */
public class NodeRequests {

    private static final String WATCHER = ".watcher";
    private static final Duration WATCHER_MAX_AGE = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration RESPONSE_POLL = Duration.ofSeconds(1);

    private final Path dir;

    public NodeRequests(final String dir) {
        assertTrue(dir != null && !dir.trim().isEmpty(), "No node requests folder: set e2e.ui.node.requests.dir "
                + "(run-e2e.sh mounts it at /node-requests)");
        this.dir = Paths.get(dir);
    }

    /**
     * Fails unless the folder is writable and the helper replaced its ".watcher" file within the last 30 s.
     */
    public NodeRequests assertWatched() {
        assertTrue(Files.isDirectory(dir) && Files.isWritable(dir), format("%s is not a folder the tests user can "
                + "write to: the node requests folder is not mounted", dir));
        final Path watcher = dir.resolve(WATCHER);
        assertTrue(Files.exists(watcher), format("%s does not exist: the helper that acts on the nodes "
                + "(scripts/node-requests.sh) is not started by run-e2e.sh", watcher));
        final long age = System.currentTimeMillis() - lastModified(watcher);
        assertTrue(age <= WATCHER_MAX_AGE.toMillis(), format("%s was last written %d s ago: the helper that acts on "
                + "the nodes (scripts/node-requests.sh) does not run", watcher, age / 1000));
        return this;
    }

    /**
     * Fails unless the helper can stop and start a kubelet: it says so in its ".watcher" file (kubelet=ready), or why
     * not, e.g. that run-e2e.sh has no SSH key for the nodes. Checked before a run is launched for nothing.
     */
    public NodeRequests assertKubeletRequestsReady() {
        assertWatched();
        final String state = watcherValue("kubelet");
        assertTrue("ready".equals(state), format("The helper that acts on the nodes (scripts/node-requests.sh) can't "
                + "stop a kubelet: %s", state.isEmpty() ? "it does not say why (an older helper?)" : state));
        return this;
    }

    private String watcherValue(final String key) {
        try {
            return new Response(Files.readAllLines(dir.resolve(WATCHER), StandardCharsets.UTF_8)).value(key);
        } catch (NoSuchFileException e) {
            // Replaced by the helper meanwhile
            sleep(RESPONSE_POLL.toMillis(), MILLISECONDS);
            return watcherValue(key);
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to read %s", dir.resolve(WATCHER)), e);
        }
    }

    /**
     * Asks the helper to label the node of the run, and returns its answer, which names the node and the label.
     */
    public Response label(final String runId) {
        return request(format("action=label%nrun=%s%n", runId));
    }

    /**
     * Asks the helper to remove the label from the node, which it must have labelled itself.
     */
    public Response unlabel(final String node) {
        return request(format("action=unlabel%nnode=%s%n", node));
    }

    /**
     * Asks the helper to stop the kubelet of the node of the run, which must have the IP address {@code ip}. The answer
     * names the node and gives the UTC times before and after the kubelet was stopped ({@code requested_at},
     * {@code stopped_at}: yyyy-MM-dd HH:mm:ss), and after how many seconds the node starts the kubelet again by itself
     * ({@code restore_seconds}).
     */
    public Response stopKubelet(final String runId, final String ip) {
        return request(format("action=stop-kubelet%nrun=%s%nip=%s%n", runId, ip));
    }

    /**
     * Asks the helper to start the kubelet of the node again, which it must have stopped itself.
     */
    public Response startKubelet(final String node) {
        return request(format("action=start-kubelet%nnode=%s%n", node));
    }

    private Response request(final String content) {
        final String id = "cp-e2e-" + UUID.randomUUID();
        final Path temporary = dir.resolve("." + id + ".tmp");
        final Path request = dir.resolve(id + ".request");
        final Path response = dir.resolve(id + ".response");
        try {
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            // Renamed into place: the helper never reads a part of the request
            Files.move(temporary, request, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to write the request %s", request), e);
        }
        final long deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT.toMillis();
        while (!Files.exists(response)) {
            if (System.currentTimeMillis() > deadline) {
                deleteQuietly(request);
                fail(format("The helper that acts on the nodes did not answer %s in %d min", request,
                        RESPONSE_TIMEOUT.toMinutes()));
            }
            sleep(RESPONSE_POLL.toMillis(), MILLISECONDS);
        }
        try {
            final Response answer = new Response(Files.readAllLines(response, StandardCharsets.UTF_8));
            deleteQuietly(response);
            return answer;
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to read the answer %s", response), e);
        }
    }

    private static long lastModified(final Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (NoSuchFileException e) {
            // Replaced by the helper meanwhile: it is current
            return System.currentTimeMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to read the time of %s", file), e);
        }
    }

    private static void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.out.printf("Unable to delete %s: %s%n", file, e.getMessage());
        }
    }

    /**
     * The answer of the helper. {@code labelledBefore} is true if the node had the label already: the helper left it
     * as it is, and does not remove it. The other values of an answer are in {@link #value(String)}.
     */
    public static final class Response {

        public final boolean ok;
        public final String node;
        public final String label;
        public final boolean labelledBefore;
        public final String message;
        private final Map<String, String> values;

        Response(final List<String> lines) {
            final Map<String, String> parsed = new HashMap<>();
            for (final String line : lines) {
                final int separator = line.indexOf('=');
                if (separator > 0) {
                    parsed.put(line.substring(0, separator), line.substring(separator + 1).trim());
                }
            }
            this.values = Collections.unmodifiableMap(parsed);
            this.ok = "ok".equals(values.get("status"));
            this.node = value("node");
            this.label = value("label");
            this.labelledBefore = "true".equals(values.get("labelled_before"));
            this.message = value("message");
        }

        /**
         * The value of the answer's line {@code <key>=<value>}, blank if it has no such line.
         */
        public String value(final String key) {
            return values.getOrDefault(key, "");
        }
    }
}
