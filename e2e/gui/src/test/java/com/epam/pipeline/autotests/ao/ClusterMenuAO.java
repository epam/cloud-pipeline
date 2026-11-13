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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Driver;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.NaturalOrderComparators;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.openqa.selenium.By.className;

public class ClusterMenuAO implements AccessObject<ClusterMenuAO> {
    public static By node() {
        return byXpath(".//*[contains(@class, 'cluster__table')]//tr[contains(@class, 'cluster-row')]");
    }

    public static By node(final String pipeline) {
        final String clusterTableClass = "cluster__table";
        final String clusterRowClass = "cluster-row";
        final String pipelineColumnClass = "cluster__cluster-node-row-pipeline";
        return byXpath(String.format(
            ".//*[contains(@class, '%s')]//tr[contains(@class, '%s') and ./td[@class = '%s' and text() = '%s']]",
            clusterTableClass, clusterRowClass, pipelineColumnClass, pipeline
        ));
    }

    public static By nodeLabel(final String text) {
        return byXpath(String.format(
            ".//*[@class = 'cluster__cluster-node-row-labels']/*[contains(., '%s')]", text
        ));
    }

    public static Condition master() {
        return new Condition("master node") {
            @Override
            public boolean apply(Driver driver, final WebElement element) {
                return $(element).has(or("master node", contains(nodeLabel("MASTER")),
                        contains(nodeLabel("EDGE")),
                        contains(nodeLabel("CP-SEARCH-ELK")),
                        contains(nodeLabel("HEAPSTER")),
                        contains(nodeLabel("DNS")),
			contains(nodeLabel("CP-TINYPROXY"))));
            }
        };
    }

    public static Condition windows() {
        return new Condition("windows node") {
            @Override
            public boolean apply(Driver driver, final WebElement element) {
                return $(element).has(contains(nodeLabel("WINDOWS")));
            }
        };
    }

    public enum HeaderColumn {
        DATE("cluster__cluster-node-row-created"),
        NAME("cluster__cluster-node-row-name"),
        LABEL("cluster__cluster-node-row-labels"),
        ADDRESS("cluster__cluster-node-row-addresses");

        private String cssClass;

        HeaderColumn(String cssClass) {
            this.cssClass = cssClass;
        }

    }

    public ClusterMenuAO waitForTheNode(String pipelineName, String runId) {
        waitForRunIdAppearing(runId);
        assertTrue(
                nodeLine(runId)
                        .shouldBe(visible)
                        .findAll("td")
                        .get(2)
                        .text()
                        .contains(pipelineName)
        );
        return this;
    }

    public ClusterMenuAO waitForTheNode(String runId) {
        waitForRunIdAppearing(runId);
        return this;
    }

    private SelenideElement nodeLine(String runId) {
        return $(byText(runIdLabelText(runId)))
                .closest("tr");
    }

    private void waitForRunIdAppearing(String runId) {
        for (int i = 0; i < 320; i++) {
            $(button("Refresh")).click();
            if ($(byText(runIdLabelText(runId))).exists()) {
                break;
            }
            sleep(5, SECONDS);
        }
    }

    public int getNodesCount() {
        return $("tbody").findAll("tr").size();
    }

    public void assertNodesTableIsEmpty() {
        $("tbody").findAll("tr").shouldHave(size(0));
    }

    public ClusterMenuAO filerBy(HeaderColumn header, String ip) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(header.cssClass));
        createdHeaderButton.find(byAttribute("title", "Filter menu")).click();
        switch (header) {
            case ADDRESS:
                $$("input").findBy(attribute("placeholder", "IP")).setValue(ip);
                break;
            case LABEL:
                $$("input").findBy(attribute("placeholder", "Run Id")).setValue(ip);
                break;
            default:
                throw new RuntimeException("Could be filtered only by Label of Address");
        }
        $$(byText("OK")).find(visible).click();

        return this;
    }

    public ClusterMenuAO resetFiltering(HeaderColumn header) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(header.cssClass));
        createdHeaderButton.find(byAttribute("title", "Filter menu")).click();

        $$(byText("Clear")).find(visible).click();

        return this;
    }

    public ClusterMenuAO removeNode(String runId) {
        $(byText(runIdLabelText(runId)))
                .closest("tr")
                .find(byId("terminate-node-button"))
                .click();
        sleep(6, SECONDS);
        $$(button("OK")).find(visible).click();

        return this;
    }

    public ClusterMenuAO removeNodeIfPresent(String runId) {
        $(byClassName("ant-table-tbody")).shouldBe(visible);
        sleep(2, SECONDS);
        if ($(byText(runIdLabelText(runId))).is(visible)) {
            removeNode(runId);
        }
        return this;
    }

    public ClusterMenuAO validateThereIsNoNode(String runId) {
        for (int i = 0; i < 5; i++) {
            if ($(byText(runIdLabelText(runId))).is(not(exist))) {
                break;
            }
            sleep(2, SECONDS);
        }
        $(byText(runIdLabelText(runId))).shouldNot(exist);
        return this;
    }

    public ClusterMenuAO validateThereIsNoNodeWithName(final String nodeName) {
        // node names contain dots, so the row class "cluster-row-<name>" can't be used as a CSS class selector
        $$(byCssSelector("td." + HeaderColumn.NAME.cssClass))
                .filterBy(exactText(nodeName))
                .shouldHave(size(0));
        return this;
    }

    public String getNodeName(String runId) {
        return $(byText(runIdLabelText(runId)))
                .should(exist)
                .closest("tr")
                .findAll("td")
                .get(1)
                .text();
    }

    private String runIdLabelText(String runId) {
        return "RUN ID " + runId;
    }

    public ClusterMenuAO sortByIncrease(HeaderColumn column) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(column.cssClass));
        createdHeaderButton.find(".ant-table-column-sorter-up").click();

        createdHeaderButton.find(".ant-table-column-sorter-up").shouldHave(cssClass("on"));
        createdHeaderButton.find(".ant-table-column-sorter-down").shouldHave(cssClass("off"));

        return this;
    }

    public ClusterMenuAO sortByDecrease(HeaderColumn column) {
        SelenideElement createdHeaderButton = $$("th").findBy(cssClass(column.cssClass));
        createdHeaderButton.find(".ant-table-column-sorter-down").click();

        createdHeaderButton.find(".ant-table-column-sorter-up").shouldHave(cssClass("off"));
        createdHeaderButton.find(".ant-table-column-sorter-down").shouldHave(cssClass("on"));

        return this;
    }

    public ClusterMenuAO validateSortedByIncrease(HeaderColumn column) {
        if (column == HeaderColumn.LABEL) {
            validateSortedByRunId(false);
            return this;
        }
        final Comparator<String> comparator = column == HeaderColumn.NAME
                ? Comparator.naturalOrder()
                : NaturalOrderComparators.createNaturalOrderRegexComparator();
        validateSortedBy(column, comparator);
        return this;
    }

    public ClusterMenuAO validateSortedByDecrease(HeaderColumn column) {
        if (column == HeaderColumn.LABEL) {
            validateSortedByRunId(true);
            return this;
        }
        final Comparator<String> comparator = column == HeaderColumn.NAME
                ? Comparator.reverseOrder()
                : NaturalOrderComparators.createNaturalOrderRegexComparator().reversed();
        validateSortedBy(column, comparator);
        return this;
    }

    public String getNodeAddress(String runId) {
        return $(byText(runIdLabelText(runId)))
                .closest("tr")
                .findAll("td")
                .get(4)
                .text()
                .replaceAll(", .+", "");
    }

    public String getNodeRunId(int index) {
        return $("tbody")
                .findAll("tr")
                .get(index)
                .find("#label-RUNID")
                .text();
    }

    private void validateSortedBy(HeaderColumn column, Comparator<String> comparator) {
        ElementsCollection dates = $$("td").filterBy(cssClass(column.cssClass));
        List<String> sortedByStrings = new ArrayList<>();
        for (SelenideElement date : dates) {
            sortedByStrings.add(date.text());
        }

        ArrayList<String> sortedSortedByStrings = new ArrayList<>(sortedByStrings);
        sortedSortedByStrings.sort(comparator);

        assertTrue(sortedByStrings.size() > 0);
        assertEquals(sortedByStrings, sortedSortedByStrings);
    }

    /**
     * The Labels column is sorted by the run id of a node; a node without a run id counts as 0.
     * A node with a non-numeric run id (a free node of a node pool) has no defined place in that order and is skipped.
     */
    private void validateSortedByRunId(final boolean descending) {
        final Pattern runIdLabel = Pattern.compile("RUN ID (\\S+)");
        final List<Long> runIds = new ArrayList<>();
        for (SelenideElement row : $$(node())) {
            final Matcher matcher = runIdLabel.matcher(row.find("td." + HeaderColumn.LABEL.cssClass).text());
            if (!matcher.find()) {
                runIds.add(0L);
            } else if (matcher.group(1).matches("\\d+")) {
                runIds.add(Long.parseLong(matcher.group(1)));
            }
        }

        final List<Long> sortedRunIds = new ArrayList<>(runIds);
        sortedRunIds.sort(descending ? Comparator.<Long>reverseOrder() : Comparator.<Long>naturalOrder());

        assertTrue(runIds.size() >= 2, "At least two nodes are expected in the table: " + runIds);
        assertTrue(runIds.stream().anyMatch(runId -> runId > 0), "No node with a RUN ID label: " + runIds);
        assertEquals(runIds, sortedRunIds);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }
}
