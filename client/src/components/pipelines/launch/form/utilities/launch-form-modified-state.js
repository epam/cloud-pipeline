/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  ADVANCED,
  EXEC_ENVIRONMENT,
  PARAMETERS,
  SYSTEM_PARAMETERS
} from './launch-form-sections';
import {
  autoScaledClusterEnabled,
  hybridAutoScaledClusterEnabled,
  getSkippedSystemParametersList,
  gridEngineEnabled,
  sparkEnabled,
  slurmEnabled,
  kubeEnabled
} from './launch-cluster';
import {
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_LIMIT_MOUNTS
} from './parameters';
import {
  dinDEnabled,
  noMachineEnabled,
  singularityEnabled,
  systemDEnabled
} from './additional-run-preference';

function modified (form, parameters, formName, parametersName, defaultValue) {
  return `${form.getFieldValue(formName) || defaultValue}` !==
    `${parameters[parametersName] || defaultValue}`;
}

function clusterModified (parameters, state) {
  const autoScaledCluster = autoScaledClusterEnabled(parameters.parameters);
  const hybridAutoScaledCluster = hybridAutoScaledClusterEnabled(parameters.parameters);
  const gridEngineEnabledValue = gridEngineEnabled(parameters.parameters);
  const sparkEnabledValue = sparkEnabled(parameters.parameters);
  const slurmEnabledValue = slurmEnabled(parameters.parameters);
  const kubeEnabledValue = kubeEnabled(parameters.parameters);
  const initial = {
    autoScaledCluster,
    hybridAutoScaledClusterEnabled: hybridAutoScaledCluster,
    gridEngineEnabled: gridEngineEnabledValue,
    sparkEnabled: sparkEnabledValue,
    slurmEnabled: slurmEnabledValue,
    kubeEnabled: kubeEnabledValue,
    launchCluster: +(parameters.node_count) > 0 || autoScaledCluster,
    nodesCount: +(parameters.node_count),
    maxNodesCount: parameters.parameters && parameters.parameters[CP_CAP_AUTOSCALE_WORKERS]
      ? +(parameters.parameters[CP_CAP_AUTOSCALE_WORKERS].value)
      : 0
  };
  return initial.autoScaledCluster !== state.autoScaledCluster ||
    initial.hybridAutoScaledClusterEnabled !== state.hybridAutoScaledClusterEnabled ||
    initial.gridEngineEnabled !== state.gridEngineEnabled ||
    initial.sparkEnabled !== state.sparkEnabled ||
    initial.slurmEnabled !== state.slurmEnabled ||
    initial.kubeEnabled !== state.kubeEnabled ||
    initial.launchCluster !== state.launchCluster ||
    `${initial.nodesCount || 0}` !== `${state.nodesCount || 0}` ||
    `${initial.maxNodesCount || 0}` !== `${state.maxNodesCount || 0}`;
}

function pipelineCheck (props, state) {
  const {
    pipeline,
    version,
    pipelineConfiguration,
    fireCloudMethod
  } = props;
  const initial = {
    pipeline: pipeline ? +pipeline.id : null,
    version,
    pipelineConfiguration,
    fireCloudMethodName: fireCloudMethod
      ? fireCloudMethod.name : null,
    fireCloudMethodNamespace: fireCloudMethod
      ? fireCloudMethod.namespace : null,
    fireCloudMethodSnapshot: fireCloudMethod
      ? fireCloudMethod.snapshot : null,
    fireCloudMethodConfiguration: fireCloudMethod
      ? fireCloudMethod.configuration : null,
    fireCloudMethodConfigurationSnapshot: fireCloudMethod
      ? fireCloudMethod.configurationSnapshot : null
  };
  const current = {
    pipeline: state.pipeline ? +state.pipeline.id : null,
    version: state.version,
    pipelineConfiguration: state.pipelineConfiguration,
    fireCloudMethodName: state.fireCloudMethodName,
    fireCloudMethodNamespace: state.fireCloudMethodNamespace,
    fireCloudMethodSnapshot: state.fireCloudMethodSnapshot,
    fireCloudMethodConfiguration: state.fireCloudMethodConfiguration,
    fireCloudMethodConfigurationSnapshot: state.fireCloudMethodConfigurationSnapshot,
    fireCloudInputsLength: Object.keys(state.fireCloudInputs).length,
    fireCloudOutputsLength: Object.keys(state.fireCloudOutputs).length
  };
  return (
    initial.pipeline !== current.pipeline ||
    initial.version !== current.version ||
    initial.pipelineConfiguration !== current.pipelineConfiguration ||
    initial.fireCloudMethodName !== current.fireCloudMethodName ||
    initial.fireCloudMethodNamespace !== current.fireCloudMethodNamespace ||
    initial.fireCloudMethodSnapshot !== current.fireCloudMethodSnapshot ||
    initial.fireCloudMethodConfiguration !== current.fireCloudMethodConfiguration ||
    initial.fireCloudMethodConfigurationSnapshot !== current.fireCloudMethodConfigurationSnapshot ||
    current.fireCloudInputsLength > 0 ||
    current.fireCloudInputsLength > 0
  );
}

function executionEnvironmentCheck (props, state, {execEnvSelectValue}) {
  const {
    fireCloudMethod
  } = props;
  const initial = {
    isDts: !!fireCloudMethod,
    execEnvSelectValue
  };
  return initial.isDts !== state.isDts ||
    initial.execEnvSelectValue !== state.execEnvSelectValue;
}

function spotOnDemandCheck (form, {spotInitialValue}) {
  return `${form.getFieldValue(`${ADVANCED}.is_spot`)}` !== `${spotInitialValue}`;
}

function autoPauseCheck (form, state) {
  return `${form.getFieldValue(`${ADVANCED}.is_spot`)}` === 'false' && !state.autoPause;
}

function limitMountsCheck (form, parameters) {
  const getDefaultValue = () => {
    if (parameters.parameters && parameters.parameters[CP_CAP_LIMIT_MOUNTS]) {
      return parameters.parameters[CP_CAP_LIMIT_MOUNTS].value;
    }
    return null;
  };
  const initial = getDefaultValue();
  return form.getFieldValue(`${ADVANCED}.limitMounts`) !== initial;
}
function cmdTemplateCheck (state, parameters, {cmdTemplateValue, toolDefaultCmd}) {
  let code = cmdTemplateValue;
  if (state.startIdle) {
    code = 'sleep infinity';
  } else if (state.useDefaultCmd && toolDefaultCmd) {
    code = toolDefaultCmd;
  }
  if (code === undefined) {
    return false;
  }
  return code !== parameters['cmd_template'];
}

function parametersCheck (form, parameters, state) {
  const formParams = form.getFieldValue(PARAMETERS);
  const formSystemParams = form.getFieldValue(SYSTEM_PARAMETERS);
  const formValue = {};
  if (formParams && formParams.keys) {
    for (let i = 0; i < formParams.keys.length; i++) {
      const key = formParams.keys[i];
      if (!formParams.params || !formParams.params.hasOwnProperty(key)) {
        continue;
      }
      const parameter = formParams.params[key];
      if (parameter && parameter.name) {
        formValue[parameter.name] = parameter.value || '';
      }
    }
  } else {
    // 'form' value was not initialized yet -
    // so it wasn't modified
    return false;
  }
  if (formSystemParams && formSystemParams.keys) {
    for (let i = 0; i < formSystemParams.keys.length; i++) {
      const key = formSystemParams.keys[i];
      if (!formSystemParams.params || !formSystemParams.params.hasOwnProperty(key)) {
        continue;
      }
      const parameter = formSystemParams.params[key];
      if (parameter && parameter.name) {
        formValue[parameter.name] = parameter.value || '';
      }
    }
  }
  const initialValue = Object.keys(parameters.parameters || {})
    .filter(key => [
      CP_CAP_LIMIT_MOUNTS,
      ...getSkippedSystemParametersList({state})
    ].indexOf(key) === -1)
    .map(key => ({key, value: parameters.parameters[key].value || ''}))
    .reduce((r, c) => ({...r, [c.key]: c.value}), {});
  const check = (source, test) => {
    const sourceEntries = Object.entries(source);
    for (let i = 0; i < sourceEntries.length; i++) {
      const [name, value] = sourceEntries[i];
      if (!test.hasOwnProperty(name) || `${test[name]}` !== `${value}`) {
        return true;
      }
    }
    return false;
  };
  return Object.keys(formValue).length !== Object.keys(initialValue).length ||
    check(formValue, initialValue) ||
    check(initialValue, formValue);
}

function runCapabilitiesCheck (state, parameters) {
  const dinD = dinDEnabled(parameters.parameters);
  const singularity = singularityEnabled(parameters.parameters);
  const systemD = systemDEnabled(parameters.parameters);
  const noMachine = noMachineEnabled(parameters.parameters);
  return dinD !== state.dinD || singularity !== state.singularity ||
    systemD !== state.systemD || noMachine !== state.noMachine;
}

export default function (props, state, options) {
  const {form, parameters} = props;
  const {
    defaultCloudRegionId
  } = options;
  // configuration name check
  return modified(form, props, 'configuration.name', 'currentConfigurationName') ||
    // pipeline check
    pipelineCheck(props, state) ||
    // execution environment check
    executionEnvironmentCheck(props, state, options) ||
    // instance type check
    modified(form, parameters, `${EXEC_ENVIRONMENT}.type`, 'instance_size') ||
    // docker image check
    modified(form, parameters, `${EXEC_ENVIRONMENT}.dockerImage`, 'docker_image') ||
    // disk check
    modified(form, parameters, `${EXEC_ENVIRONMENT}.disk`, 'instance_disk') ||
    // cluster state check
    clusterModified(parameters, state) ||
    // cloud region check
    modified(
      form,
      parameters,
      `${EXEC_ENVIRONMENT}.cloudRegionId`,
      'cloudRegionId',
      defaultCloudRegionId
    ) ||
    // cores number check
    modified(form, parameters, `${EXEC_ENVIRONMENT}.coresNumber`, 'coresNumber') ||
    // spot/on-demand check
    spotOnDemandCheck(form, options) ||
    // auto-pause check
    autoPauseCheck(form, state) ||
    // pretty url check
    modified(form, parameters, `${ADVANCED}.prettyUrl`, 'prettyUrl') ||
    // timeout check
    modified(form, parameters, `${ADVANCED}.timeout`, 'timeout') ||
    // stopAfter check
    modified(form, parameters, `${ADVANCED}.stopAfter`, 'stopAfter') ||
    // endpointName check
    modified(form, parameters, `${ADVANCED}.endpointName`, 'endpointName') ||
    // limit mounts check
    limitMountsCheck(form, parameters) ||
    // cmd template check
    cmdTemplateCheck(state, parameters, options) ||
    // check general parameters
    parametersCheck(form, parameters) ||
    // check additional run capabilities
    runCapabilitiesCheck(state, parameters);
}
