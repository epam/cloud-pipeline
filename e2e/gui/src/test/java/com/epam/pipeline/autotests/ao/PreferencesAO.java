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

import com.codeborne.selenide.SelenideElement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.UnaryOperator;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.Utils.*;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertTrue;

public class PreferencesAO extends SettingsPageAO {
    public final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(CLUSTER_TAB, $(byClassName("section-cluster"))),
            entry(SYSTEM_TAB, $(byClassName("section-system"))),
            entry(DOCKER_SECURITY_TAB, $(byClassName("section-docker-security"))),
            entry(AUTOSCALING_TAB, $(byClassName("section-grid-engine-autoscaling"))),
            entry(USER_INTERFACE_TAB, $(byClassName("section-user-interface"))),
            entry(LUSTRE_FS_TAB, $(byClassName("section-lustre-fs"))),
            entry(LAUNCH_TAB, $(byClassName("section-launch"))),
            entry(SEARCH,  context().find(byClassName("ant-input-search")).find(tagName("input"))),
            entry(SAVE, $(byId("edit-preference-form-ok-button")))
    );

    public PreferencesAO(final PipelinesLibraryAO pipelinesLibraryAO) {
        super(pipelinesLibraryAO);
    }

    public ClusterTabAO switchToCluster() {
        click(CLUSTER_TAB);
        return new ClusterTabAO(parentAO);
    }

    public SystemTabAO switchToSystem() {
        click(SYSTEM_TAB);
        return new SystemTabAO(parentAO);
    }

    public AutoscalingTabAO switchToAutoscaling() {
        click(AUTOSCALING_TAB);
        return new AutoscalingTabAO(parentAO);
    }

    public DockerSecurityAO switchToDockerSecurity() {
        click(DOCKER_SECURITY_TAB);
        return new DockerSecurityAO(parentAO);
    }

    public UserInterfaceAO switchToUserInterface() {
        click(USER_INTERFACE_TAB);
        return new UserInterfaceAO(parentAO);
    }

    public PreferencesAO searchPreference(String preference) {
        clear(SEARCH);
        setValue(SEARCH, preference);
        enter();
        return this;
    }

    public LustreFSAO switchToLustreFS() {
        click(LUSTRE_FS_TAB);
        return new LustreFSAO(parentAO);

    }

    public LaunchAO switchToLaunch() {
        click(LAUNCH_TAB);
        return new LaunchAO(parentAO);
    }

    private void setEyeOption(boolean eyeIsChecked) {
        final SelenideElement eye = context().find(byClassName("preference-group__preference-row"))
                .find(byClassName("anticon"));
        if((eye.has(cssClass("anticon-eye-o")) && eyeIsChecked) ||
                (eye.has(cssClass("anticon-eye")) && !eyeIsChecked)) {
            eye.click();
        }
    }

    public PreferencesAO setPreference(String preference, String value, boolean eyeIsChecked) {
        searchPreference(preference);
        final By pref = getByField(preference);
        click(pref)
                .clear(pref)
                .setValue(pref, value);
        setEyeOption(eyeIsChecked);
        return this;
    }

    public String[] getPreference(String preference) {
        searchPreference(preference);
        $(byClassName("CodeMirror-code")).shouldBe(visible);
        sleep(1, SECONDS);
        final List<String> list = $$(byClassName("CodeMirror-line")).texts();
        final String[] prefValue = new String[2];
        prefValue[0] = (list.size() <= 1 ) ? String.join("", list)
                : String.join("\n", list).replaceAll("\\u00a0", "");
        prefValue[1] = String.valueOf(!context().find(byClassName("preference-group__preference-row"))
                .$(byClassName("anticon")).has(cssClass("anticon-eye-o")));
        return prefValue;
    }

    public PreferencesAO clearAndSetJsonToPreference(String preference, String value, boolean eyeIsChecked) {
        SelenideElement pref = context().$(byClassName("preference-group__code-editor"));
        searchPreference(preference);
        selectAllAndClearTextField(pref);
        clickAndSendKeysWithSlashes(pref, value);
        deleteExtraBrackets(pref, 300);
        setEyeOption(eyeIsChecked);
        return this;
    }

    public PreferencesAO updateCodeText(final String preference, final String value, final boolean eyeIsChecked) {
        searchPreference(preference);
        final SelenideElement editor = $(byClassName("CodeMirror-line"));
        selectAllAndClearTextField(editor);
        pasteText(editor, value);
        setEyeOption(eyeIsChecked);
        return this;
    }

    public String[] getLinePreference(String preference) {
        searchPreference(preference);
        String[] prefValue = new String[2];
        final SelenideElement selenideElement = context().$$(byClassName("preference-group__preference-row"))
                .filter(exactText(preference)).first();
        prefValue[0] = selenideElement.$(byClassName("ant-input-sm")).attr("value");
        prefValue[1] = String.valueOf(!selenideElement.$(byClassName("anticon")).has(cssClass("anticon-eye-o")));
        return prefValue;
    }

    public PreferencesAO setCheckboxPreference(String preference, boolean checkboxIsEnable, boolean eyeIsChecked) {
        searchPreference(preference);
        final SelenideElement checkBox = context().shouldBe(visible)
                .find(byXpath(".//span[.='Enabled']/preceding-sibling::span"));
        if ((checkBox.has(cssClass("ant-checkbox-checked")) && !checkboxIsEnable) ||
                (!checkBox.has(cssClass("ant-checkbox-checked")) && checkboxIsEnable)) {
            checkBox.click();
        }
        setEyeOption(eyeIsChecked);
        return this;
    }

    public boolean[] getCheckboxPreferenceState(String preference) {
        boolean[] checkboxState = new boolean[2];
        searchPreference(preference);
        checkboxState[0] = context().shouldBe(visible)
                .find(byXpath(".//span[.='Enabled']/preceding-sibling::span")).has(cssClass("ant-checkbox-checked"));
        checkboxState[1] = context().find(byClassName("preference-group__preference-row"))
                .find(byClassName("anticon")).has(cssClass("anticon-eye"));
        return checkboxState;
    }

    private By getByField(final String variable) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $$(byClassName("preference-group__preference-row"))
                        .stream()
                        .filter(element -> element.has(exactText(variable)))
                        .map(e -> e.find(".ant-input-sm"))
                        .collect(toList());
            }
        };
    }

    private By getPreferenceState(String preference) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $$(byClassName("preference-group__preference-row"))
                        .stream()
                        .filter(element -> element.has(exactText(preference)))
                        .map(e -> e.find(byCssSelector("i")))
                        .collect(toList());
            }
        };
    }

    private By getByCheckbox(final String variable) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return $$(byClassName("preference-group__preference-row"))
                        .stream()
                        .filter(element -> element.has(text(variable)))
                        .map(e -> e.find(".ant-checkbox-wrapper"))
                        .collect(toList());
            }
        };
    }

    public PreferencesAO setSystemSshDefaultRootUserEnabled() {
        setValue(SEARCH, "system.ssh.default.root.user.enabled").enter();
        SelenideElement checkBox = context().shouldBe(visible)
                .find(byXpath(".//span[.='Enabled']/preceding-sibling::span"));
        if (!checkBox.has(cssClass("ant-checkbox-checked"))) {
            checkBox.click();
        }
        if (context().find(byClassName("anticon-eye-o")).isDisplayed()) {
            context().find(byClassName("anticon-eye-o")).click();
        }
        return this;
    }

    public String[] getAmisFromClusterNetworksConfigPreference(String region) {
        final String[] ami = new String[2];
        searchPreference("cluster.networks.config");
        $(byClassName("CodeMirror-code")).shouldBe(visible);
        sleep(1, SECONDS);
        final String[] strings = $$(byClassName("CodeMirror-line")).texts().toArray(new String[0]);
        try {
            JsonNode instance = new ObjectMapper().readTree(String.join("", strings)
                    .replaceAll("\\u00a0", "")).get("regions");
            for (JsonNode node1 : instance) {
                if (node1.get("name").asText().equals(region)) {
                    for (JsonNode node : node1.get("amis")) {
                        if (node.path("instance_mask").asText().equals("*")) {
                            ami[0] = node.path("ami").asText();
                        } else {
                            ami[1] = node.path("ami").asText();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(format("Could not deserialize JSON content %s, cause: %s",
                    String.join("", strings), e.getMessage()), e);
        }
        return ami;
    }

    public PreferencesAO save() {
        /* click(SAVE) method has been replaced by pressEnter() method due to the problem of pressing
           the Save button at case of big size json or a lot of preferences on the page for current Chromium version
        */
        actions().moveToElement(get(SAVE)).perform();
        get(SAVE).pressEnter();
        get(SAVE).shouldBe(disabled);
        return this;
    }

    public PreferencesAO saveIfNeeded() {
        actions().moveToElement($(By.id("edit-preference-form-ok-button")))
                 .perform();
        sleep(10, SECONDS);
        if(get(SAVE).isEnabled()) {
            save();
        }
        return this;
    }

    public PreferencesAO updatePreference(String preference, String value, boolean eyeIsChecked) {
        return setPreference(preference, value, eyeIsChecked)
                .saveIfNeeded();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public class ClusterTabAO extends PreferencesAO {

        private final By dockerExtraMulti = getByField("cluster.docker.extra_multi");
        private final By instanceHddExtraMulti = getByField("cluster.instance.extra_multi");
        private final By clusterAllowedInstanceTypes = getByField("cluster.allowed.instance.types");
        private final By clusterAllowedInstanceTypesDocker = getByField(
                "cluster.allowed.instance.types.docker");

        public static final String CLUSTER_AWS_EBS_TYPE = "cluster.aws.ebs.type";

        ClusterTabAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public PreferencesAO setDockerExtraMulti(final String value) {
            setByVariable(value, dockerExtraMulti);
            return this;
        }

        public PreferencesAO setInstanceHddExtraMulti(final String value) {
            setByVariable(value, instanceHddExtraMulti);
            return this;
        }

        public String getDockerExtraMulti() {
            return getClusterValue(dockerExtraMulti);
        }

        public String getInstanceHddExtraMulti() {
            return getClusterValue(instanceHddExtraMulti);
        }

        public PreferencesAO setClusterAllowedStringPreference(String mask, String value) {
            return setClusterValue(mask, value);
        }

        public ClusterTabAO checkClusterAllowedInstanceTypes(final String value) {
            ensure(clusterAllowedInstanceTypes, value(value));
            return this;
        }

        public ClusterTabAO checkClusterAllowedInstanceTypesDocker(final String value) {
            ensure(clusterAllowedInstanceTypesDocker, value(value));
            return this;
        }

        public ClusterTabAO checkClusterAwsEbsType(final String value) {
            ensure(getByField(CLUSTER_AWS_EBS_TYPE), value(value));
            ensure(getPreferenceState(CLUSTER_AWS_EBS_TYPE), cssClass("anticon-eye"));
            return this;
        }

        private ClusterTabAO setClusterValue(final String clusterPref, final String value) {
            By clusterVariable = getByField(clusterPref);
            setByVariable(value, clusterVariable);
            while(!$(clusterVariable).attr("value").equals(value)) {
                sleep(500, MILLISECONDS);
            }
            return this;
        }

        private void setByVariable(final String value, final By clusterVariable) {
            click(clusterVariable);
            clear(clusterVariable);
            setValue(clusterVariable, value);
        }

        private String getClusterValue(final By clusterVariable) {
            return $(clusterVariable).getValue();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class SystemTabAO extends PreferencesAO {

        private final By maxIdleTimeout = getByField("system.max.idle.timeout.minutes");
        private final By idleActionTimeout = getByField("system.idle.action.timeout.minutes");
        private final By idleCpuThreshold = getByField("system.idle.cpu.threshold");
        private final By idleAction = getByField("system.idle.action");
        private final By ldapUserBlockMonitor = getByCheckbox("system.ldap.user.block.monitor.enable");
        private final By userMonitor = getByCheckbox("system.user.monitor.enable");
        private final By systemMaintenanceMode = getByCheckbox("system.maintenance.mode");
        private final By systemMaintenanceModeBanner = getByField("system.maintenance.mode.banner");

        SystemTabAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public SystemTabAO setMaxIdleTimeout(final String value) {
            return setSystemValue(maxIdleTimeout, value);
        }

        public String getMaxIdleTimeout() {
            return getSystemValue(maxIdleTimeout);
        }

        public SystemTabAO setIdleActionTimeout(final String value) {
            return setSystemValue(idleActionTimeout, value);
        }

        public String getIdleActionTimeout() {
            return getSystemValue(idleActionTimeout);
        }

        public SystemTabAO setIdleCpuThreshold(final String value) {
            return setSystemValue(idleCpuThreshold, value);
        }

        public String getIdleCpuThreshold() {
            return getSystemValue(idleCpuThreshold);
        }

        public SystemTabAO setIdleAction(final String value) {
            return setSystemValue(idleAction, value);
        }

        public String getIdleAction() {
            return getSystemValue(idleAction);
        }

        public SystemTabAO setSystemMaintenanceModeBanner(final String value) {
            return setSystemValue(systemMaintenanceModeBanner, value);
        }

        public String getSystemMaintenanceModeBanner() {
            return getSystemValue(systemMaintenanceModeBanner);
        }

        public SystemTabAO setEmptySystemMaintenanceModeBanner() {
            click(systemMaintenanceModeBanner);
            PreferencesAO.this.clearByKey(systemMaintenanceModeBanner);
            return this;
        }

        private SystemTabAO setSystemValue(final By systemVariable, final String value) {
            click(systemVariable);
            clear(systemVariable);
            setValue(systemVariable, value);
            return this;
        }

        private String getSystemValue(final By systemVariable) {
            return $(systemVariable).getValue();
        }

        public boolean getLdapUserBlockMonitor() {
            return $(ldapUserBlockMonitor).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
        }

        public SystemTabAO enableLdapUserBlockMonitor() {
            if (!getLdapUserBlockMonitor()) {
                click(ldapUserBlockMonitor);
            }
            return this;
        }

        public SystemTabAO disableLdapUserBlockMonitor() {
            if (getLdapUserBlockMonitor()) {
                click(ldapUserBlockMonitor);
            }
            return this;
        }

        public boolean getUserMonitor() {
            return $(userMonitor).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
        }

        public SystemTabAO enableUserMonitor() {
            if (!getUserMonitor()) {
                click(userMonitor);
            }
            return this;
        }

        public SystemTabAO disableUserMonitor() {
            if (getUserMonitor()) {
                click(userMonitor);
            }
            return this;
        }

        public boolean getSystemMaintenanceMode() {
            return $(systemMaintenanceMode).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
        }

        public SystemTabAO enableSystemMaintenanceMode() {
            if (!getSystemMaintenanceMode()) {
                click(systemMaintenanceMode);
            }
            return this;
        }

        public SystemTabAO disableSystemMaintenanceMode() {
            if (getSystemMaintenanceMode()) {
                click(systemMaintenanceMode);
            }
            return this;
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class DockerSecurityAO extends PreferencesAO {

        private final By policyDenyNotScanned = getByCheckbox("security.tools.policy.deny.not.scanned");
        private final By graceHours = getByField("security.tools.grace.hours");

        DockerSecurityAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public DockerSecurityAO enablePolicyDenyNotScanned() {
            if (!$(policyDenyNotScanned).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"))) {
                clickPolicyDenyNotScanned();
            }
            return this;
        }

        public DockerSecurityAO disablePolicyDenyNotScanned() {
            if ($(policyDenyNotScanned).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"))) {
                clickPolicyDenyNotScanned();
            }
            return this;
        }

        public DockerSecurityAO clickPolicyDenyNotScanned() {
            click(policyDenyNotScanned);
            return this;
        }

        public String getGraceHours() {
            return getDockerSecurityValue(graceHours);
        }

        public boolean getPolicyDenyNotScanned() {
            return getDockerSecurityCheckbox(policyDenyNotScanned).equals("Enable");
        }

        public DockerSecurityAO setGraceHours(final String value) {
            click(graceHours);
            clear(graceHours);
            setValue(graceHours, value);
            return this;
        }

        private String getDockerSecurityValue(final By dockerSecurityVar) {
            return $(dockerSecurityVar).getValue();
        }

        private String getDockerSecurityCheckbox(final By dockerSecurityVar) {
            return $(dockerSecurityVar).getText();
        }
    }

    public class AutoscalingTabAO extends PreferencesAO {

        private final By scaleDownTimeout = getByField("ge.autoscaling.scale.down.timeout");
        private final By scaleUpTimeout = getByField("ge.autoscaling.scale.up.timeout");

        AutoscalingTabAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public AutoscalingTabAO setScaleDownTimeout(final String value) {
            click(scaleDownTimeout);
            clear(scaleDownTimeout);
            setValue(scaleDownTimeout, value);
            return this;
        }

        public AutoscalingTabAO setScaleUpTimeout(final String value) {
            click(scaleUpTimeout);
            clear(scaleUpTimeout);
            setValue(scaleUpTimeout, value);
            return this;
        }
    }

    public class UserInterfaceAO extends PreferencesAO {

        public static final String SUPPORT_TEMPLATE = "ui.support.template";

        private final By supportTemplateValue = getByField(SUPPORT_TEMPLATE);
        private final By supportTemplateState = getPreferenceState(SUPPORT_TEMPLATE);

        UserInterfaceAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public UserInterfaceAO checkSupportTemplate(final String value) {
            ensure(supportTemplateValue, value(value));
            ensure(supportTemplateState, cssClass("anticon-eye"));
            return this;
        }

        public String getSupportTemplate() {
            return $(supportTemplateValue).getValue();
        }
    }

    public class LustreFSAO extends PreferencesAO {

        private final By lustreFSMountOptions = getByField("lustre.fs.mount.options");

        LustreFSAO(final PipelinesLibraryAO parentAO) {
            super(parentAO);
        }

        public LustreFSAO checkLustreFSMountOptionsValue(final String value) {
            ensure(lustreFSMountOptions, value(value));
            return this;
        }
    }

    public class LaunchAO extends PreferencesAO {

        public static final String LAUNCH_PARAMETERS = "launch.system.parameters";
        public static final String LAUNCH_CONTAINER_CPU_RESOURCES = "launch.container.cpu.resource";

        LaunchAO(PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        public LaunchAO checkLaunchSystemParameters(final String value) {
            final String launchSystemParameters = $$(byClassName("preference-group__preference-row")).stream()
                    .map(SelenideElement::getText)
                    .filter(e -> e.startsWith(LAUNCH_PARAMETERS))
                    .map(e -> e.replaceAll("\\n[0-9]*\\n", "\n"))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(format(
                            "%s preference was not found.", LAUNCH_PARAMETERS
                    )));
            assertTrue(launchSystemParameters.contains(value),
                    format("Value %s isn't found in '%s' preference", value, launchSystemParameters));
            return this;
        }

        public LaunchAO checkLaunchContainerCpuResource(final String value) {
            ensure(getByField(LAUNCH_CONTAINER_CPU_RESOURCES), value(value));
            return this;
        }

        public String getLaunchSystemParameters() {
            return $$(byClassName("preference-group__preference-row")).stream()
                    .map(SelenideElement::getText)
                    .filter(e -> e.startsWith(LAUNCH_PARAMETERS))
                    .map(e -> e.replaceAll("\\n[0-9]*\\n", "\n")
                            .replaceFirst(LAUNCH_PARAMETERS + "\n", ""))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(format(
                            "%s preference was not found.", LAUNCH_PARAMETERS
                    )));
        }

        public LaunchAO setLaunchSystemParameters(final UnaryOperator<String> action) {
            final String launchSystemParameters = getLaunchSystemParameters();
            final String edited = action.apply(launchSystemParameters);
            if (launchSystemParameters.equals(edited)) {
                return this;
            }
            final SelenideElement launchSystemParameter = $$(byClassName("preference-group__preference-row")).stream()
                    .filter(s -> s.getText().startsWith(LAUNCH_PARAMETERS))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(format(
                            "%s preference was not found.", LAUNCH_PARAMETERS
                    )));
            return this;
        }
    }
}
