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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.NodeRequests;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE;
import static com.epam.pipeline.autotests.ao.LogAO.taskList;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * 3297: a node that stops reporting to Kubernetes keeps serving its run for the period of the preference
 * cluster.node.unavailable.grace.period.minutes, and the platform shows that the node is unavailable.
 * <p>
 * The API's autoscaler checks the nodes every cluster.autoscale.rate (40 s by default). A node whose condition Ready is
 * Unknown, because its kubelet stopped posting the node status, is unavailable: the run gets the tag NODE_UNAVAILABLE,
 * and with the date suffix of the tags (system.run.tag.date.suffix) the time, and the task NodeAvailabilityMonitor logs
 * "Node <instance id> has been unavailable since <time>". The time is when Kubernetes marked the node unavailable,
 * about 40 s (the node monitor grace period of Kubernetes) after the kubelet stopped. The pod of the run tolerates the
 * taints of such a node, so the run keeps running until the grace period ends. When the kubelet reports again, the tag
 * is removed and the task logs "Node <instance id> has been available since <time>".
 * <p>
 * The tests container can't reach the node, so the helper of run-e2e.sh (scripts/node-requests.sh) stops the kubelet
 * over SSH from its node, as an admin would from the master, and starts it again. Before it stops the kubelet it
 * starts a timer on the node that starts the kubelet again after a few minutes, so the node comes back even if the test
 * stops meanwhile. The base class stops the run and terminates its node.
 */
public class NodeUnavailableTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private static final String CASE = "3297";
    private static final String TAG = "NODE_UNAVAILABLE";
    private static final String TASK = "NodeAvailabilityMonitor";
    private static final String GRACE_PREFERENCE = "cluster.node.unavailable.grace.period.minutes";
    private static final String DATE_SUFFIX_PREFERENCE = "system.run.tag.date.suffix";
    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(30);
    // The kubelet stays stopped for at most the mark timeout and the checks, or until the timer of the node starts it
    // (10 min by default): the platform must not terminate the node before
    private static final Duration MIN_GRACE_PERIOD = Duration.ofMinutes(15);
    private static final Duration MARK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RELOAD_PERIOD = ofSeconds(15);
    // Kubernetes marks the node about 40 s after the kubelet stopped, more if its node monitor grace period is longer
    private static final Duration MARK_DELAY_MAX = Duration.ofMinutes(3);
    // The times of the helper and of the node condition both have whole seconds
    private static final Duration CLOCK_TOLERANCE = ofSeconds(2);
    private static final DateTimeFormatter HELPER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_TIME_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}";
    // The value of the IP row: "<instance id> (<IP address>)"
    private static final Pattern IP_ROW = Pattern.compile("^\\s*(\\S+)\\s+\\((\\d{1,3}(?:\\.\\d{1,3}){3})\\)");
    private static final Pattern INSTANCE_ID = Pattern.compile("i-[0-9a-f]+");
    // The page loads the run every 5 s
    private static final Duration TAGS_TIMEOUT = ofSeconds(60);
    private static final Duration TAGS_POLL = ofSeconds(5);

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    private NodeRequests nodes;
    private String stoppedNode;

    @Test
    @TestCase(value = "3297")
    public void unavailableNodeIsKeptAndMarked() {
        // Checked before anything is launched
        nodes = new NodeRequests(C.NODE_REQUESTS_DIR).assertKubeletRequestsReady();
        logoutIfNeeded();
        loginAs(admin);
        open(C.ROOT_ADDRESS);
        // The API answers a preference that is not set with its default
        final Duration grace = gracePeriod(preference(GRACE_PREFERENCE));
        assertTrue(grace.compareTo(MIN_GRACE_PERIOD) >= 0, format("The platform terminates an unavailable node after "
                + "%d min (preference %s): the test needs at least %d min", grace.toMinutes(), GRACE_PREFERENCE,
                MIN_GRACE_PERIOD.toMinutes()));
        assertFalse(preference(DATE_SUFFIX_PREFERENCE).isEmpty(), format("The preference %s is empty: the platform "
                + "puts no date tag next to %s", DATE_SUFFIX_PREFERENCE, TAG));

        // Steps 1-5
        tools().perform(registry, group, tool, tab -> tab.runOnDemand(this));
        final String runId = getLastRunId();
        final LogAO runLog = runsMenu()
                .showLog(runId)
                .waitForSshLink();
        System.out.printf("%s: run %s, price type %s%n", CASE, runId, runLog.getParameterValue("Price type"));

        // Step 6
        final String ipRow = runLog.getParameterValue("IP");
        final Matcher ip = IP_ROW.matcher(ipRow);
        assertTrue(ip.find(), format("The IP of the Instance section of run %s is not '<instance id> (<IP address>)': "
                + "%s", runId, ipRow));
        final String instanceId = ip.group(1);
        final String nodeIp = ip.group(2);
        System.out.printf("%s: run %s runs on instance %s (%s)%n", CASE, runId, instanceId, nodeIp);
        // The field shows the node name, which is the instance id on the nodes of this platform; the log names the
        // instance id. A node with another name is found by its record alone.
        final String nodeRegex = INSTANCE_ID.matcher(instanceId).matches() ? Pattern.quote(instanceId) : "\\S+";
        if (!INSTANCE_ID.matcher(instanceId).matches()) {
            System.out.printf("%s: the node name %s is not an instance id: the record of any node is taken%n", CASE,
                    instanceId);
        }

        // Steps 7-9: the helper logs in to the node from its own node, as an admin would from the master
        final NodeRequests.Response stop = nodes.stopKubelet(runId, nodeIp);
        assertTrue(stop.ok, format("The kubelet of the node of run %s is not stopped: %s", runId, stop.message));
        stoppedNode = stop.node;
        System.out.printf("%s: %s%n", CASE, stop.message);
        final LocalDateTime requestedAt = helperTime(stop, "requested_at");
        final LocalDateTime stoppedAt = helperTime(stop, "stopped_at");
        final String restore = stop.value("restore_seconds");
        assertTrue(restore.matches("\\d+") && Long.parseLong(restore) < grace.getSeconds(), format("The node starts "
                + "the kubelet again after '%s' s, which is not before the platform terminates it", restore));

        // Steps 10-12
        final MatchResult unavailable = waitForTaskLine(runLog, runId, Pattern.compile(format(
                "Node (?<node>%s) has been unavailable since (?<time>%s)", nodeRegex, LOG_TIME_REGEX)), MARK_TIMEOUT);
        final String nodeId = unavailable.group(1);
        final String since = unavailable.group(2);
        System.out.printf("%s: the task %s logs: Node %s has been unavailable since %s%n", CASE, TASK, nodeId, since);
        runLog.shouldHaveRunningStatus();
        // The page loads the run, with its tags, apart from the log: it may show the tags of a few seconds before
        final List<String> tags = waitForTags(runLog, t -> t.contains(TAG));
        assertTrue(tags.contains(TAG), format("The Instance section of run %s has no tag %s: %s", runId, TAG, tags));
        // Step 14: the GUI hides the date tag of a predefined tag such as this one (it shows only the duration in the
        // pop-up of the tag), so the date tag is read through the API. Its value has the format of the log line
        final JsonNode runTags = PlatformApi.get("run/" + runId).path("tags");
        assertEquals(runTags.path(TAG).asText(), "true", format("The API tags of run %s have no %s=true: %s", runId,
                TAG, runTags));
        final String dateTag = TAG + preference(DATE_SUFFIX_PREFERENCE);
        assertEquals(runTags.path(dateTag).asText(), since, format("The API tags of run %s have no date tag %s with "
                + "the time of the log, %s: %s", runId, dateTag, since, runTags));
        final LocalDateTime sinceTime = LocalDateTime.parse(since, LOG_TIME);
        assertFalse(sinceTime.isBefore(requestedAt.minus(CLOCK_TOLERANCE)), format("The node of run %s is unavailable "
                + "since %s, before its kubelet was stopped at %s UTC", runId, since, requestedAt));
        assertFalse(sinceTime.isAfter(stoppedAt.plus(MARK_DELAY_MAX)), format("The node of run %s is unavailable since "
                + "%s, more than %d min after its kubelet was stopped at %s UTC", runId, since,
                MARK_DELAY_MAX.toMinutes(), stoppedAt));

        // Steps 15-17: the node reports again, and the run still runs. After: the base class stops the run and
        // terminates its node
        final NodeRequests.Response start = nodes.startKubelet(stoppedNode);
        assertTrue(start.ok, format("The kubelet of node %s of run %s is not started again: %s", stoppedNode, runId,
                start.message));
        stoppedNode = null;
        System.out.printf("%s: %s%n", CASE, start.message);
        final String availableSince = waitForTaskLine(runLog, runId, Pattern.compile(format(
                "Node (?<node>%s) has been available since (?<time>%s)", Pattern.quote(nodeId), LOG_TIME_REGEX)),
                RECOVERY_TIMEOUT).group(2);
        System.out.printf("%s: the task %s logs: Node %s has been available since %s%n", CASE, TASK, nodeId,
                availableSince);
        assertFalse(LocalDateTime.parse(availableSince, LOG_TIME).isBefore(sinceTime), format("The node of run %s is "
                + "available since %s, before it became unavailable at %s", runId, availableSince, since));
        runLog.shouldHaveRunningStatus();
        final List<String> tagsAfter = waitForTags(runLog, t -> !t.contains(TAG));
        assertFalse(tagsAfter.contains(TAG), format("The Instance section of run %s still has the tag %s after the "
                + "node is available again: %s", runId, TAG, tagsAfter));
    }

    /**
     * Starts the kubelet again if the test stopped it and did not start it. The base class then stops the run and
     * terminates its node; run-e2e.sh starts a kubelet that is still stopped after the tests.
     */
    @AfterClass(alwaysRun = true)
    public void startKubelet() {
        if (nodes == null || stoppedNode == null) {
            return;
        }
        try {
            final NodeRequests.Response response = nodes.startKubelet(stoppedNode);
            System.out.printf("%s: %s%n", CASE, response.message);
        } catch (RuntimeException | AssertionError e) {
            System.out.printf("%s: unable to start the kubelet of node %s, the node starts it by itself and run-e2e.sh "
                    + "after the tests: %s%n", CASE, stoppedNode, e.getMessage());
        }
    }

    private static String preference(final String name) {
        return PlatformApi.get("preferences/" + name).path("value").asText("").trim();
    }

    private static Duration gracePeriod(final String minutes) {
        if (minutes.isEmpty()) {
            return DEFAULT_GRACE_PERIOD;
        }
        try {
            return Duration.ofMinutes(Long.parseLong(minutes));
        } catch (NumberFormatException e) {
            fail(format("The preference %s is not a number of minutes: %s", GRACE_PREFERENCE, minutes));
            return DEFAULT_GRACE_PERIOD;
        }
    }

    private static LocalDateTime helperTime(final NodeRequests.Response response, final String key) {
        try {
            return LocalDateTime.parse(response.value(key), HELPER_TIME);
        } catch (DateTimeParseException e) {
            fail(format("The helper's answer has no time %s (yyyy-MM-dd HH:mm:ss): '%s'", key, response.value(key)));
            return null;
        }
    }

    /**
     * Reloads the log page of the run until its task {@link #TASK} shows a line that the pattern finds, and returns the
     * match. The page also polls the run and the log itself; a reload just shows the latest state at once.
     */
    private static MatchResult waitForTaskLine(final LogAO runLog, final String runId, final Pattern line,
                                               final Duration timeout) {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<String> lines = Collections.emptyList();
        while (true) {
            if ($(taskWithName(TASK)).is(visible)) {
                runLog.clickTaskWithName(TASK);
                lines = runLog.logMessages().collect(Collectors.toList());
                for (final String text : lines) {
                    final Matcher matcher = line.matcher(text);
                    if (matcher.find()) {
                        return matcher.toMatchResult();
                    }
                }
            }
            if (System.currentTimeMillis() > deadline) {
                fail(format("The task %s of run %s shows no line '%s' after %d min: %s", TASK, runId, line.pattern(),
                        timeout.toMinutes(), lines));
            }
            sleep(RELOAD_PERIOD.getSeconds(), SECONDS);
            refresh();
            $(taskList()).shouldBe(visible, ofSeconds(60));
        }
    }

    /**
     * Waits up to {@link #TAGS_TIMEOUT} until the tags of the run, which the page reloads every few seconds, are as
     * expected, and returns the last tags it read.
     */
    private static List<String> waitForTags(final LogAO runLog, final Predicate<List<String>> expected) {
        final long deadline = System.currentTimeMillis() + TAGS_TIMEOUT.toMillis();
        while (true) {
            final List<String> tags = runTags(runLog);
            if (expected.test(tags) || System.currentTimeMillis() > deadline) {
                return tags;
            }
            sleep(TAGS_POLL.getSeconds(), SECONDS);
        }
    }

    /**
     * The tags in the Instance section of the run, as the page shows them: a tag with the value true by its name, the
     * others by their value. Empty if the run has no tags to show.
     */
    private static List<String> runTags(final LogAO runLog) {
        runLog.expandTab(INSTANCE);
        final SelenideElement row = $(parameterWithName("Tags"));
        if (!row.exists()) {
            return Collections.emptyList();
        }
        return row.$$(tagName("span")).texts().stream()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.toList());
    }
}
