/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import {inject, observer, Provider} from 'mobx-react';
import {computed, observable} from 'mobx';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Icon,
  message,
  Modal,
  Row,
  Select,
  Tooltip
} from 'antd';
import EstimatedDiskSizeWarning from './estimated-disk-size-warning';
import PipelineRunner from '../../../models/pipelines/PipelineRunner';
import PipelineRunKubeServices from '../../../models/pipelines/PipelineRunKubeServices';
import PipelineRunEstimatedPrice from '../../../models/pipelines/PipelineRunEstimatedPrice';
import {names} from '../../../models/utils/ContextualPreference';
import {autoScaledClusterEnabled} from '../../pipelines/launch/form/utilities/launch-cluster';
import {CP_CAP_LIMIT_MOUNTS} from '../../pipelines/launch/form/utilities/parameters';
import AllowedInstancesCountWarning from
  '../../pipelines/launch/form/utilities/allowed-instances-count-warning';
import RunName from '../run-name';
import '../../../staticStyles/tooltip-nowrap.css';
import AWSRegionTag from '../../special/AWSRegionTag';
import JobEstimatedPriceInfo from '../../special/job-estimated-price-info';
import {getSpotTypeName} from '../../special/spot-instance-names';
import awsRegions from '../../../models/cloudRegions/CloudRegions';
import {
  getInputPaths,
  getOutputPaths,
  performAsyncCheck,
  PermissionErrors,
  PermissionErrorsTitle
} from './execution-allowed-check';
import CreateRunSchedules from '../../../models/runSchedule/CreateRunSchedules';
import SensitiveBucketsWarning from './sensitive-buckets-warning';
import OOMCheck from '../../pipelines/launch/form/utilities/oom-check';
import {filterNFSStorages} from '../../pipelines/launch/dialogs/AvailableStoragesBrowser';
import RunCapabilities, {
  RUN_CAPABILITIES_MODE,
  getEnabledCapabilities,
  updateCapabilities,
  applyCustomCapabilitiesParameters,
  CapabilitiesDisclaimer,
  checkRequiredCapabilitiesErrors
} from '../../pipelines/launch/form/utilities/run-capabilities';
import ToolLayersCheckWarning from './check/tool-layers/warning';
import DiskSizeWarning from "./warnings/disk-size-warning";

// Mark class with @submitsRun if it may launch pipelines / tools
export const submitsRun = (...opts) => {
  return inject('spotInstanceTypes', 'onDemandInstanceTypes')(...opts);
};

export function run (parent, callback) {
  if (!parent) {
    console.warn('"run" function should be called with parent component passed to arguments:');
    console.warn('"run(parent)"');
    console.warn('Parent component should be marked with @runPipelineActions');
    throw new Error('"run" function should be called with parent component passed to arguments:');
  }
  return function (
    payload,
    confirm = true,
    title,
    warning,
    allowedInstanceTypesRequest,
    hostedApplicationConfiguration,
    skipCheck = false
  ) {
    return runFn(
      payload,
      confirm,
      title,
      warning,
      parent.props,
      callback,
      allowedInstanceTypesRequest,
      hostedApplicationConfiguration,
      skipCheck
    );
  };
}

export function modifyPayloadForAllowedInstanceTypes (payload, allowedInstanceTypesRequest) {
  if (allowedInstanceTypesRequest && allowedInstanceTypesRequest.loaded) {
    let availableInstanceTypes = [];
    if (payload.dockerImage) {
      availableInstanceTypes = (
        allowedInstanceTypesRequest.value[names.allowedToolInstanceTypes] || []
      ).map(i => i.name);
    } else {
      availableInstanceTypes = (
        allowedInstanceTypesRequest.value[names.allowedInstanceTypes] || []
      ).map(i => i.name);
    }
    const availablePriceTypes = (
      allowedInstanceTypesRequest.value[names.allowedPriceTypes] || []
    ).map(p => {
      if (p === 'spot') {
        return true;
      } else if (p === 'on_demand') {
        return false;
      }
      return undefined;
    }).filter(p => p !== undefined);
    const getAvailableValue = (value, values, chooseDefault = true) => {
      const [v] = values.filter(v => v === value);
      if (v !== undefined) {
        return v;
      } else if (values.length > 0 && chooseDefault) {
        return values[0];
      }
      return null;
    };
    payload.instanceType = getAvailableValue(payload.instanceType, availableInstanceTypes, false);
    payload.isSpot = getAvailableValue(`${payload.isSpot}` === 'true', availablePriceTypes);
    return payload;
  }
  return payload;
}

async function saveRunSchedule (runId, scheduleRules) {
  const payload = scheduleRules.filter(r => !r.removed)
    .map(({scheduleId, action, cronExpression, timeZone}) => (
      {scheduleId, action, cronExpression, timeZone}
    ));

  const request = new CreateRunSchedules(runId);
  await request.send(payload);

  if (request.error) {
    message.error(request.error);
  }
}

async function runHostedApp (runId, configuration) {
  if (configuration) {
    const {service, ports = []} = configuration;
    const request = new PipelineRunKubeServices(runId, service);
    await request.send(ports);
    if (request.error) {
      message.error(request.error);
    }
  }
}

function runFn (
  payload,
  confirm,
  title,
  warning,
  stores,
  callbackFn,
  allowedInstanceTypesRequest,
  hostedApplicationConfiguration,
  skipCheck
) {
  return new Promise(async (resolve) => {
    let launchName;
    let launchVersion;
    let availableInstanceTypes = [];
    let availablePriceTypes = [true, false];
    const {
      dataStorageAvailable,
      authenticatedUserInfo
    } = stores;
    allowedInstanceTypesRequest && await allowedInstanceTypesRequest.fetchIfNeededOrWait();
    if (allowedInstanceTypesRequest && allowedInstanceTypesRequest.loaded) {
      if (payload.dockerImage) {
        availableInstanceTypes = (
          allowedInstanceTypesRequest.value[names.allowedToolInstanceTypes] || []
        ).map(i => i);
      } else {
        availableInstanceTypes = (
          allowedInstanceTypesRequest.value[names.allowedInstanceTypes] || []
        ).map(i => i);
      }
      availablePriceTypes = (
        allowedInstanceTypesRequest.value[names.allowedPriceTypes] || []
      ).map(p => {
        if (p === 'spot') {
          return true;
        } else if (p === 'on_demand') {
          return false;
        }
        return undefined;
      }).filter(p => p !== undefined);
    } else if (stores && (stores.spotInstanceTypes || stores.onDemandInstanceTypes)) {
      let storeName = 'onDemandInstanceTypes';
      if (payload.isSpot) {
        storeName = 'spotInstanceTypes';
      }
      await stores[storeName].fetchIfNeededOrWait();
      if (stores[storeName].loaded) {
        availableInstanceTypes = (stores[storeName].value || []).map(i => i);
      }
    }
    if (stores.preferences) {
      await stores.preferences.fetchIfNeededOrWait();
    }
    if (
      dataStorageAvailable &&
      stores.preferences &&
      stores.preferences.loaded &&
      /^skip$/i.test(stores.preferences.nfsSensitivePolicy) &&
      payload &&
      payload.params &&
      payload.params[CP_CAP_LIMIT_MOUNTS] &&
      payload.params[CP_CAP_LIMIT_MOUNTS].value &&
      !/^none$/i.test(payload.params[CP_CAP_LIMIT_MOUNTS].value)
    ) {
      dataStorageAvailable && await dataStorageAvailable.fetchIfNeededOrWait();
      if (dataStorageAvailable.loaded) {
        const ids = new Set(payload.params[CP_CAP_LIMIT_MOUNTS].value.split(',').map(i => +i));
        const selection = (dataStorageAvailable.value || []).filter(s => ids.has(+s.id));
        const hasSensitive = !!selection.find(s => s.sensitive);
        const filtered = selection
          .filter(
            filterNFSStorages(
              stores.preferences.nfsSensitivePolicy,
              hasSensitive
            )
          );
        if (filtered.length) {
          payload.params[CP_CAP_LIMIT_MOUNTS].value = filtered.map(s => s.id).join(',');
        } else {
          payload.params[CP_CAP_LIMIT_MOUNTS].value = 'None';
        }
      }
    }
    if (stores.awsRegions) {
      await stores.awsRegions.fetchIfNeededOrWait();
    }
    if (payload.pipelineId) {
      const {pipelines} = stores;
      await pipelines.fetchIfNeededOrWait();
      const [pipeline] = (pipelines.value || []).filter(p => `${p.id}` === `${payload.pipelineId}`);
      if (pipeline) {
        launchName = pipeline.name;
      }
    } else {
      const [, , imageName] = payload.dockerImage.split('/');
      const parts = imageName.split(':');
      if (parts.length === 2) {
        launchName = parts[0];
        launchVersion = parts[1];
      } else {
        launchName = imageName;
      }
    }
    let scheduleRules = null;
    if (payload.scheduleRules && payload.scheduleRules.length > 0) {
      scheduleRules = payload.scheduleRules;
      delete payload.scheduleRules;
    }
    payload.params = applyCustomCapabilitiesParameters(payload.params, stores.preferences);
    const launchFn = async () => {
      const messageVersion = payload.runNameAlias
        ? `${launchName}:${launchVersion}`
        : launchVersion;
      const hide = message
        .loading(`Launching ${payload.runNameAlias || launchName} (${messageVersion})...`, -1);
      if (payload.runNameAlias) {
        payload.tags = {
          alias: payload.runNameAlias
        };
        delete payload.runNameAlias;
      }
      await PipelineRunner.send({...payload, force: true});
      hide();
      if (PipelineRunner.error) {
        message.error(PipelineRunner.error);
        resolve(false);
        callbackFn && callbackFn(false);
      } else {
        if (scheduleRules && scheduleRules.length > 0) {
          await saveRunSchedule(PipelineRunner.value.id, scheduleRules);
        }
        await runHostedApp(PipelineRunner.value.id, hostedApplicationConfiguration);
        resolve(true);
        callbackFn && callbackFn(true);
      }
    };
    if (!confirm) {
      await launchFn();
    } else {
      const inputs = getInputPaths(null, payload.params);
      const outputs = getOutputPaths(null, payload.params);
      const {errors: permissionErrors} = await performAsyncCheck({
        ...stores,
        dataStorages: dataStorageAvailable,
        inputs,
        outputs,
        dockerImage: payload.dockerImage,
        skipCheck
      });
      let dataStorages;
      if (dataStorageAvailable) {
        await dataStorageAvailable.fetchIfNeededOrWait();
        if (dataStorageAvailable.loaded) {
          dataStorages = (dataStorageAvailable.value || []).map(d => d);
        }
      }
      let component;
      const ref = (element) => {
        component = element;
      };
      Modal.confirm({
        title: null,
        width: '50%',
        content: (
          <RunSpotConfirmationWithPrice
            runInfo={{
              name: launchName,
              alias: payload.runNameAlias,
              version: launchVersion,
              title,
              payload
            }}
            ref={ref}
            warning={warning}
            instanceType={payload.instanceType}
            hddSize={payload.hddSize}
            isSpot={payload.isSpot}
            isCluster={payload.nodeCount > 0 || autoScaledClusterEnabled(payload.params)}
            onDemandSelectionAvailable={availablePriceTypes.indexOf(false) >= 0}
            pipelineId={payload.pipelineId}
            pipelineVersion={payload.version}
            pipelineConfiguration={payload.configurationName}
            nodeCount={(+payload.nodeCount) || 0}
            cloudRegionId={payload.cloudRegionId}
            cloudRegions={(stores.awsRegions.value || []).map(p => p)}
            availableInstanceTypes={availableInstanceTypes}
            dataStorages={dataStorages}
            parameters={payload.params}
            permissionErrors={permissionErrors}
            limitMounts={
              payload.params && payload.params[CP_CAP_LIMIT_MOUNTS]
                ? payload.params[CP_CAP_LIMIT_MOUNTS].value
                : undefined
            }
            preferences={stores.preferences}
            dockerRegistries={stores.dockerRegistries}
            skipCheck={skipCheck}
            dockerImage={payload.dockerImage}
            authenticatedUserInfo={authenticatedUserInfo}
          />
        ),
        style: {
          wordWrap: 'break-word'
        },
        okText: 'Launch',
        onOk: async function () {
          if (component) {
            if (component.state.runCapabilities) {
              payload.params = updateCapabilities(
                payload.params,
                component.state.runCapabilities,
                stores.preferences
              );
            }
            payload.params = applyCustomCapabilitiesParameters(
              payload.params,
              stores.preferences
            );
            if (
              checkRequiredCapabilitiesErrors(
                getEnabledCapabilities(payload.params),
                stores.preferences
              )
            ) {
              const error = 'You need to specify required capabilities';
              message.error(error, 5);
              return Promise.reject(new Error(error));
            }
            payload.isSpot = component.state.isSpot;
            payload.instanceType = component.state.instanceType;
            payload.hddSize = component.state.hddSize;
            if (component.state.limitMounts !== component.props.limitMounts) {
              const {limitMounts} = component.state;
              if (limitMounts) {
                if (!payload.params) {
                  payload.params = {};
                }
                payload.params[CP_CAP_LIMIT_MOUNTS] = {
                  type: 'string',
                  required: false,
                  value: limitMounts
                };
              } else if (payload.params && payload.params[CP_CAP_LIMIT_MOUNTS]) {
                delete payload.params[CP_CAP_LIMIT_MOUNTS];
              }
            }
            if (component.state.runNameAlias) {
              payload.tags = {
                alias: component.state.runNameAlias
              };
            }
          }
          if (!payload.instanceType) {
            message.error('You should select instance type');
            resolve(false);
            callbackFn && callbackFn(false);
          } else {
            const version = payload.runNameAlias
              ? `${launchName}:${launchVersion}`
              : launchVersion;
            const hide = message
              .loading(`Launching ${payload.runNameAlias || launchName} (${version})...`, -1);
            if (payload.runNameAlias) {
              delete payload.runNameAlias;
            }
            await PipelineRunner.send({...payload, force: true});
            hide();
            if (PipelineRunner.error) {
              message.error(PipelineRunner.error);
              resolve(false);
              callbackFn && callbackFn(false);
            } else {
              if (scheduleRules && scheduleRules.length > 0) {
                await saveRunSchedule(PipelineRunner.value.id, scheduleRules);
              }
              await runHostedApp(PipelineRunner.value.id, hostedApplicationConfiguration);
              resolve(true);
              callbackFn && callbackFn(true);
            }
          }
        },
        onCancel () {
          resolve(false);
          callbackFn && callbackFn(false);
        }
      });
    }
  });
}

function isUniqueInArray (element, index, array) {
  return array.filter(e => e === element).length === 1;
}

function notUniqueInArray (element, index, array) {
  return array.filter(e => e === element).length > 1;
}

@observer
export class RunConfirmation extends React.Component {
  state = {
    isSpot: false,
    instanceType: null,
    limitMounts: null
  };

  static propTypes = {
    warning: PropTypes.string,
    isSpot: PropTypes.bool,
    cloudRegionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    cloudRegions: PropTypes.array,
    isCluster: PropTypes.bool,
    onDemandSelectionAvailable: PropTypes.bool,
    onChangePriceType: PropTypes.func,
    instanceType: PropTypes.string,
    instanceTypes: PropTypes.array,
    showInstanceTypeSelection: PropTypes.bool,
    onChangeInstanceType: PropTypes.func,
    dataStorages: PropTypes.array,
    limitMounts: PropTypes.string,
    onChangeLimitMounts: PropTypes.func,
    onChangeHddSize: PropTypes.func,
    nodeCount: PropTypes.number,
    hddSize: PropTypes.number,
    parameters: PropTypes.object,
    permissionErrors: PropTypes.array,
    preferences: PropTypes.object,
    skipCheck: PropTypes.bool,
    dockerImage: PropTypes.string,
    dockerRegistries: PropTypes.object,
    runCapabilities: PropTypes.array,
    onChangeRunCapabilities: PropTypes.func,
    showRunCapabilities: PropTypes.bool
  };

  static defaultProps = {
    onDemandSelectionAvailable: true
  };

  @computed
  get currentRegion () {
    const [currentRegion] = (this.props.cloudRegions || [])
      .filter(p => +p.id === +this.props.cloudRegionId);
    return currentRegion;
  }

  @computed
  get currentCloudProvider () {
    const [currentProvider] = (this.props.cloudRegions || [])
      .filter(p => +p.id === +this.props.cloudRegionId);
    return currentProvider ? currentProvider.provider : null;
  }

  @computed
  get gpuEnabled () {
    const [currentInstanceType] = (this.props.instanceTypes || [])
      .filter(i => i.name === this.state.instanceType);
    return currentInstanceType && currentInstanceType.hasOwnProperty('gpu') &&
      +currentInstanceType.gpu > 0;
  }

  @computed
  get initialSelectedDataStorageIndecis () {
    if (/^none$/i.test(this.props.limitMounts)) {
      return [];
    }
    return (
      this.props.limitMounts ||
      this.dataStorages.filter(d => !d.sensitive)
        .map(d => `${d.id}`).join(',')
    )
      .split(',')
      .map(d => +d);
  }

  @computed
  get selectedDataStorageIndecis () {
    if (/^none$/i.test(this.state.limitMounts)) {
      return [];
    }
    return (
      this.state.limitMounts ||
      this.dataStorages.filter(d => !d.sensitive)
        .map(d => `${d.id}`).join(',')
    )
      .split(',')
      .map(d => +d);
  }

  @computed
  get dataStorages () {
    return (this.props.dataStorages || []).map(d => d);
  }

  @computed
  get initialSelectedDataStorages () {
    return this.dataStorages
      .filter(d => this.initialSelectedDataStorageIndecis.indexOf(+d.id) >= 0);
  }

  @computed
  get conflicting () {
    return this.initialSelectedDataStorages
      .filter((d, i, a) =>
        !!d.mountPoint &&
        notUniqueInArray(d.mountPoint, i, a.map(aa => aa.mountPoint))
      );
  }

  @computed
  get notConflictingIndecis () {
    return this.initialSelectedDataStorages
      .filter((d, i, a) =>
        !d.mountPoint ||
        isUniqueInArray(d.mountPoint, i, a.map(aa => aa.mountPoint))
      )
      .map(d => +d.id);
  }

  @computed
  get initialLimitMountsHaveConflicts () {
    return this.dataStorages
      .filter(d => this.initialSelectedDataStorageIndecis.indexOf(+d.id) >= 0 && !!d.mountPoint)
      .map(d => d.mountPoint)
      .filter(notUniqueInArray)
      .length > 0;
  }

  @computed
  get limitMountsHaveConflicts () {
    return this.dataStorages
      .filter(d => this.selectedDataStorageIndecis.indexOf(+d.id) >= 0 && !!d.mountPoint)
      .map(d => d.mountPoint)
      .filter(notUniqueInArray)
      .length > 0;
  }

  getInstanceTypes = () => {
    if (!this.props.instanceTypes) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < this.props.instanceTypes.length; i++) {
      const instanceType = this.props.instanceTypes[i];
      if (!instanceTypes.find(t => t.name === instanceType.name)) {
        instanceTypes.push(instanceType);
      }
    }
    return instanceTypes.sort((typeA, typeB) => {
      const vcpuCompared = typeA.vcpu - typeB.vcpu;
      const skuCompare = (a, b) => {
        return a.instanceFamily > b.instanceFamily
          ? 1
          : a.instanceFamily < b.instanceFamily ? -1 : 0;
      };
      return vcpuCompared === 0 ? skuCompare(typeA, typeB) : vcpuCompared;
    });
  };

  get currentInstanceType () {
    if (!this.props.instanceTypes || !this.state.instanceType) {
      return undefined;
    }
    return this.getInstanceTypes().find(t => t.name === this.state.instanceType);
  }

  get runCapabilitiesErrorSolved () {
    if (this.props.runCapabilities && this.props.preferences) {
      return !checkRequiredCapabilitiesErrors(
        this.props.runCapabilities,
        this.props.preferences
      );
    }
    return false;
  }

  setOnDemand = (onDemand) => {
    this.setState({
      isSpot: !onDemand
    }, () => {
      this.props.onChangePriceType && this.props.onChangePriceType(this.state.isSpot);
    });
  };

  setInstanceType = (instanceType) => {
    this.setState({
      instanceType
    }, () => {
      this.props.onChangeInstanceType && this.props.onChangeInstanceType(this.state.instanceType);
    });
  };

  getSelectStructure = () => {
    const groups = [];
    for (let i = 0; i < this.conflicting.length; i++) {
      const dataStorage = this.conflicting[i];
      if (dataStorage.mountPoint) {
        let [group] = groups.filter(g => g.key === dataStorage.mountPoint);
        if (!group) {
          group = {
            key: dataStorage.mountPoint,
            storages: []
          };
          groups.push(group);
        }
        group.storages.push(dataStorage);
      }
    }
    return groups.map(g => (
      <Select.OptGroup key={g.key} label={g.key}>
        {
          g.storages.map(s => (
            <Select.Option key={s.id} value={s.id.toString()} name={s.name} pathMask={s.pathMask}>
              <Tooltip
                overlayClassName="limit-mounts-warning"
                title={s.pathMask}>
                <div
                  style={{
                    height: 30,
                    display: 'flex',
                    flexDirection: 'column',
                    position: 'relative'
                  }}
                >
                  <b style={{height: 20, lineHeight: '20px'}}>
                    <AWSRegionTag regionId={s.regionId} regionUID={s.regionName} /> {s.name}
                  </b>
                  <span
                    style={{
                      position: 'absolute',
                      height: 12,
                      lineHeight: '12px',
                      top: 20,
                      left: 5
                    }}
                  >
                    {s.pathMask}
                  </span>
                </div>
              </Tooltip>
            </Select.Option>
          ))
        }
      </Select.OptGroup>
    ));
  };

  onSelect = (selectedConflictingIds) => {
    const selectedIds = [...this.notConflictingIndecis, ...selectedConflictingIds];
    if (selectedIds.length > 0) {
      this.setState({
        limitMounts: selectedIds.join(',')
      }, () => {
        this.props.onChangeLimitMounts && this.props.onChangeLimitMounts(this.state.limitMounts);
      });
    }
  };

  renderLimitMountsSelector = () => {
    return (
      <Provider awsRegions={awsRegions}>
        <Select
          style={{width: '100%'}}
          mode="multiple"
          onChange={this.onSelect}
          notFoundContent="Mounts not found"
          filterOption={
            (input, option) =>
              option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0 ||
              option.props.pathMask.toLowerCase().indexOf(input.toLowerCase()) >= 0}
          value={
            this.selectedDataStorageIndecis
              .filter(i => this.notConflictingIndecis.indexOf(+i) === -1)
              .map(i => i.toString())
          }
        >
          {this.getSelectStructure()}
        </Select>
      </Provider>
    );
  };

  renderCapabilitiesDisclaimer = () => {
    const {
      preferences,
      parameters
    } = this.props;
    return (
      <Provider preferences={preferences}>
        <CapabilitiesDisclaimer
          style={{margin: 2}}
          showIcon
          parameters={parameters}
        />
      </Provider>
    );
  };

  onChangeRunCapabilities = (capabilities) => {
    const {onChangeRunCapabilities} = this.props;
    onChangeRunCapabilities && onChangeRunCapabilities(capabilities);
  };

  render () {
    return (
      <div>
        {
          this.props.warning &&
          <Alert
            style={{margin: 2}}
            key="warning"
            type="warning"
            showIcon
            message={this.props.warning} />
        }
        {
          this.props.onDemandSelectionAvailable && this.props.isSpot && this.state.isSpot &&
          <Alert
            style={{margin: 2}}
            key="spot warning"
            type="info"
            showIcon
            message={
              <div>
                <Row style={{marginBottom: 5}}>
                  <b>
                    {`You are going to launch a job using a ${
                      getSpotTypeName(true, this.currentCloudProvider)
                        .toUpperCase()
                    } instance.`}
                  </b>
                </Row>
                <Row style={{marginBottom: 5}}>
                  <b>
                    While this is much cheaper, this type of instance may be OCCASIONALLY STOPPED,
                    without a notification and you will NOT be able to PAUSE this run, only STOP.
                    Consider {
                      getSpotTypeName(true, this.currentCloudProvider)
                        .toUpperCase()
                    } instance for batch jobs and short living runs.
                  </b>
                </Row>
                <Row style={{marginBottom: 5}}>
                  To change this setting
                  use <b>ADVANCED {'->'} PRICE TYPE</b> option
                  within a launch form.
                </Row>
                <Row type="flex" justify="center" style={{marginBottom: 5}}>
                  <Button
                    onClick={() => this.setOnDemand(true)}
                    type="primary"
                    size="small"
                  >
                    Set {getSpotTypeName(false, this.currentCloudProvider)
                      .toUpperCase()
                    } price type
                  </Button>
                </Row>
              </div>
            } />
        }
        {
          this.props.isSpot && !this.state.isSpot &&
          <Alert
            style={{margin: 2}}
            key="spot warning"
            type="info"
            showIcon
            message={
              <div>
                <Row style={{marginBottom: 5}}>
                  <b>
                    You are going to launch a job using a {
                      getSpotTypeName(false, this.currentCloudProvider)
                        .toUpperCase()
                    } instance.
                  </b>
                </Row>
                <Row style={{marginBottom: 5}}>
                  You will be able to PAUSE this run.
                </Row>
                <Row type="flex" justify="center" style={{marginBottom: 5}}>
                  <Button
                    onClick={() => this.setOnDemand(false)}
                    size="small"
                  >
                    Set {getSpotTypeName(true, this.currentCloudProvider)
                      .toUpperCase()
                    } price type
                  </Button>
                </Row>
              </div>
            } />
        }
        {
          this.props.isCluster && !this.state.isSpot &&
          <Alert
            type="info"
            style={{margin: 2}}
            showIcon
            message={
              <Row>
                Note that clusters cannot be paused, even
                if {getSpotTypeName(false, this.currentCloudProvider)
                  .toLowerCase()
                } price is selected
              </Row>
            } />
        }
        {
          this.props.isCluster &&
          <Alert
            type="info"
            style={{margin: 2}}
            showIcon
            message={
              <Row>
                Note that you will not be able to commit a cluster. Commit feature is only available for single-node runs
              </Row>
            } />
        }
        {
          !this.props.isCluster && this.gpuEnabled &&
          <Alert
            type="info"
            style={{margin: 2}}
            showIcon
            message={
              <Row>
                <Row style={{marginBottom: 5}}>
                  <b>You are going to launch a job using GPU-enabled instance</b> - <b>{this.state.instanceType}.</b>
                </Row>
                <Row style={{marginBottom: 5}}>
                  Note that if you install any <b>NVIDIA packages</b> manually and commit it, that may produce an unusable image.
                </Row>
                <Row>
                  All cuda-based dockers shall be built using <b><a target="_blank" href="https://hub.docker.com/r/nvidia/cuda/">nvidia/cuda</a></b> base image instead.
                </Row>
              </Row>
            } />
        }
        {
          this.props.showInstanceTypeSelection && this.getInstanceTypes().length > 0 &&
          <Alert
            style={{margin: 2}}
            key="instance type warning"
            type="info"
            showIcon
            message={
              <div>
                <Row>
                  <b>You should select instance type:</b>
                </Row>
                <Select
                  value={this.state.instanceType}
                  style={{width: '100%'}}
                  showSearch
                  allowClear={false}
                  placeholder="Instance type"
                  optionFilterProp="children"
                  notFoundContent="Instance types not found"
                  onChange={this.setInstanceType}
                  filterOption={
                    (input, option) =>
                    option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}>
                  {
                    this.getInstanceTypes()
                      .map(t => t.instanceFamily)
                      .filter((familyName, index, array) => array.indexOf(familyName) === index)
                      .map(instanceFamily => {
                        return (
                          <Select.OptGroup
                            key={instanceFamily || 'Other'}
                            label={instanceFamily || 'Other'}
                          >
                            {
                              this.getInstanceTypes()
                                .filter(t => t.instanceFamily === instanceFamily)
                                .map(t =>
                                  <Select.Option
                                    key={t.sku}
                                    value={t.name}>
                                    {t.name} (CPU: {t.vcpu}, RAM: {t.memory}{t.gpu ? `, GPU: ${t.gpu}`: ''})
                                  </Select.Option>
                                )
                            }
                          </Select.OptGroup>
                        );
                      })
                  }
                </Select>
              </div>
            } />
        }
        {
          this.props.showInstanceTypeSelection && this.getInstanceTypes().length === 0 &&
          <Alert
            style={{margin: 2}}
            key="instance type missing warning"
            type="warning"
            showIcon
            message={
              <div>
                <Row>
                  <b>You have no instance types available.</b>
                </Row>
              </div>
            } />
        }
        {
          this.initialLimitMountsHaveConflicts && (
            <Alert
              style={{margin: 2}}
              type={this.limitMountsHaveConflicts ? 'warning' : 'success'}
              showIcon
              message={
                <div>
                  <Row style={{marginBottom: 5}}>
                    There is a number of data storages, that are going to be mounted to the same location within the compute node. This may lead to unexpected behavior.
                  </Row>
                  <Row style={{marginBottom: 5, fontWeight: 'bold'}}>
                    Please review the list of the mount points below and choose the data storage to be mounted:
                  </Row>
                  <Row style={{width: '100%'}}>
                    {this.renderLimitMountsSelector()}
                  </Row>
                </div>
              }
            />
          )
        }
        <OOMCheck
          style={{margin: 2}}
          limitMounts={this.state.limitMounts}
          dataStorages={this.props.dataStorages}
          preferences={this.props.preferences}
          instance={this.currentInstanceType}
        />
        {
          this.props.permissionErrors && this.props.permissionErrors.length > 0
            ? (
              <Alert
                type="error"
                style={{margin: 2}}
                message={
                  <div>
                    <PermissionErrorsTitle />
                    <PermissionErrors errors={this.props.permissionErrors} />
                  </div>
                }
              />
            )
            : undefined
        }
        {
          !this.currentRegion && (
            <Alert
              type="error"
              style={{margin: 2}}
              showIcon
              message={
                <div>
                  <b>Cloud region not available.</b>
                </div>
              }
            />
          )
        }
        <EstimatedDiskSizeWarning
          skipCheck={this.props.skipCheck}
          nodeCount={this.props.nodeCount}
          parameters={this.props.parameters}
          hddSize={this.props.hddSize}
          onDiskSizeChanged={this.props.onChangeHddSize}
        />
        <DiskSizeWarning
          disk={this.props.hddSize}
          style={{margin: 2}}
          showIcon
        />
        <SensitiveBucketsWarning
          parameters={this.props.parameters}
          style={{margin: 2}}
        />
        {
          this.renderCapabilitiesDisclaimer()
        }
        {this.props.showRunCapabilities ? (
          <Alert
            type={this.runCapabilitiesErrorSolved ? 'info' : 'error'}
            style={{margin: 2}}
            showIcon
            message={
              <div style={{display: 'flex', flexDirection: 'column'}}>
                <span style={{marginBottom: 5}}>
                  {this.runCapabilitiesErrorSolved
                    ? 'All required run capabilities are selected.'
                    : 'Some of the required run capabilities are missing.'
                  }
                </span>
                <Provider
                  awsRegions={awsRegions}
                  preferences={this.props.preferences}
                  dockerRegistries={this.props.dockerRegistries}
                >
                  <RunCapabilities
                    values={this.props.runCapabilities}
                    onChange={this.onChangeRunCapabilities}
                    platform={this.props.platform}
                    dockerImage={this.props.dockerImage}
                    provider={this.currentCloudProvider}
                    region={this.currentRegion}
                    mode={RUN_CAPABILITIES_MODE.launch}
                    tagStyle={{height: '20px'}}
                    style={{padding: '2px'}}
                    showError={false}
                    getPopupContainer={node => node}
                  />
                </Provider>
              </div>
            }
          />
        ) : null}
        <ToolLayersCheckWarning
          style={{margin: 2}}
          toolId={this.props.dockerImage}
          showIcon
        />
      </div>
    );
  }

  componentDidMount () {
    this.updateState(this.props);
  }

  updateState = (props) => {
    this.setState({
      isSpot: props.isSpot,
      instanceType: props.instanceType,
      limitMounts: props.limitMounts
    });
  };
}

@observer
export class RunSpotConfirmationWithPrice extends React.Component {
  static propTypes = {
    warning: PropTypes.string,
    isSpot: PropTypes.bool,
    isCluster: PropTypes.bool,
    onDemandSelectionAvailable: PropTypes.bool,
    onChangePriceType: PropTypes.func,
    pipelineId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    pipelineVersion: PropTypes.string,
    pipelineConfiguration: PropTypes.string,
    instanceType: PropTypes.string,
    availableInstanceTypes: PropTypes.array,
    hddSize: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    nodeCount: PropTypes.number,
    cloudRegionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    cloudRegions: PropTypes.array,
    dataStorages: PropTypes.array,
    limitMounts: PropTypes.string,
    onChangeHddSize: PropTypes.func,
    onChangeLimitMounts: PropTypes.func,
    parameters: PropTypes.object,
    permissionErrors: PropTypes.array,
    preferences: PropTypes.object,
    dockerRegistries: PropTypes.object,
    runInfo: PropTypes.shape({
      name: PropTypes.string,
      alias: PropTypes.string,
      version: PropTypes.string,
      title: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
      payload: PropTypes.object
    }),
    skipCheck: PropTypes.bool,
    authenticatedUserInfo: PropTypes.object,
    dockerImage: PropTypes.string
  };

  static defaultProps = {
    onDemandSelectionAvailable: true
  };

  @observable _estimatedPriceType = null;

  state = {
    isSpot: false,
    hddSize: 0,
    instanceType: null,
    limitMounts: null,
    runNameAlias: null,
    runCapabilities: null
  };

  get runCapabilitiesError () {
    if (this.props.parameters && this.props.preferences) {
      return checkRequiredCapabilitiesErrors(
        Object.keys(this.props.parameters),
        this.props.preferences
      );
    }
    return false;
  }

  onChangeSpotType = (isSpot) => {
    this.setState({
      isSpot
    }, async () => {
      await this._estimatedPriceType.send({
        instanceType: this.state.instanceType,
        instanceDisk: this.state.hddSize,
        spot: this.state.isSpot,
        regionId: this.props.cloudRegionId
      });
    });
  };

  onChangeInstanceType = (instanceType) => {
    this.setState({
      instanceType
    }, async () => {
      await this._estimatedPriceType.send({
        instanceType: this.state.instanceType,
        instanceDisk: this.state.hddSize,
        spot: this.state.isSpot,
        regionId: this.props.cloudRegionId
      });
    });
  };

  onChangeLimitMounts = (limitMounts) => {
    this.setState({
      limitMounts
    }, async () => {
      this.props.onChangeLimitMounts && this.props.onChangeLimitMounts(limitMounts);
    });
  };

  onChangeHddSize = (hddSize) => {
    this.setState({
      hddSize
    }, async () => {
      await this._estimatedPriceType.send({
        instanceType: this.state.instanceType,
        instanceDisk: this.state.hddSize,
        spot: this.state.isSpot,
        regionId: this.props.cloudRegionId
      });
    });
  };

  onChangeRunNameAlias = (alias) => {
    this.setState({runNameAlias: alias});
  };

  onChangeRunCapabilities = (capabilities) => {
    this.setState({runCapabilities: (capabilities || []).slice()});
  };

  renderModalTitle = () => {
    const {
      name,
      version,
      title
    } = this.props.runInfo || {};
    const {runNameAlias} = this.state;
    let titleFn = (runName) => ([
      (<span key="launch">Launch</span>),
      runName,
      (<span key="question">?</span>),
    ]);
    if (title && typeof title === 'function') {
      titleFn = title;
    }
    if (title && typeof title === 'string') {
      titleFn = () => title;
    }
    return (
      <div
        style={{
          marginTop: '-16px',
          marginBottom: '6px',
          fontWeight: 700,
          fontSize: '14px',
          display: 'flex',
          flexWrap: 'wrap',
          lineHeight: '32px'
        }}
      >
        {
          titleFn((
            <RunName
              key="run name"
              alias={runNameAlias}
              onChange={this.onChangeRunNameAlias}
              editable
            >
              {name}
              {version && `:${version}`}
            </RunName>
          ))
        }
      </div>
    );
  };

  render () {
    return (
      <div>
        {this.renderModalTitle()}
        <Row>
          <RunConfirmation
            warning={this.props.warning}
            onChangePriceType={this.onChangeSpotType}
            isSpot={this.props.isSpot}
            isCluster={this.props.isCluster}
            cloudRegionId={this.props.cloudRegionId}
            cloudRegions={this.props.cloudRegions}
            onDemandSelectionAvailable={this.props.onDemandSelectionAvailable}
            showInstanceTypeSelection={!this.props.instanceType}
            onChangeInstanceType={this.onChangeInstanceType}
            instanceType={this.props.instanceType}
            instanceTypes={this.props.availableInstanceTypes}
            dataStorages={this.props.dataStorages}
            limitMounts={this.props.limitMounts}
            onChangeLimitMounts={this.onChangeLimitMounts}
            onChangeHddSize={this.onChangeHddSize}
            runCapabilities={this.state.runCapabilities}
            onChangeRunCapabilities={this.onChangeRunCapabilities}
            nodeCount={this.props.nodeCount}
            hddSize={this.props.hddSize}
            parameters={this.props.parameters}
            permissionErrors={this.props.permissionErrors}
            showRunCapabilities={this.runCapabilitiesError}
            preferences={this.props.preferences}
            skipCheck={this.props.skipCheck}
            dockerImage={this.props.dockerImage}
            dockerRegistries={this.props.dockerRegistries}
          />
        </Row>
        {
          this._estimatedPriceType &&
          this._estimatedPriceType.loaded &&
          !!this._estimatedPriceType.value.pricePerHour &&
          <Alert
            type="success"
            style={{margin: 2}}
            message={
              this._estimatedPriceType.pending
                ? <Row>Estimated price: <Icon type="loading" /></Row>
                : <Row><JobEstimatedPriceInfo>Estimated price: <b>{
                (Math.ceil(this._estimatedPriceType.value.pricePerHour * 100.0) / 100.0 * (this.props.nodeCount + 1))
                  .toFixed(2)
                }$</b> per hour.</JobEstimatedPriceInfo></Row>
            } />
        }
        <Provider authenticatedUserInfo={this.props.authenticatedUserInfo}>
          <AllowedInstancesCountWarning
            payload={this.props.runInfo.payload}
            style={{margin: '4px 2px'}}
          />
        </Provider>
      </div>
    );
  }

  componentDidMount () {
    this.setState({
      runNameAlias: (this.props.runInfo || {}).alias,
      isSpot: this.props.isSpot,
      instanceType: this.props.instanceType,
      hddSize: this.props.hddSize,
      limitMounts: this.props.limitMounts,
      runCapabilities: getEnabledCapabilities(this.props.parameters)
    }, async () => {
      this._estimatedPriceType = new PipelineRunEstimatedPrice(
        this.props.pipelineId,
        this.props.pipelineVersion,
        this.props.pipelineConfiguration
      );
      await this._estimatedPriceType.send({
        instanceType: this.state.instanceType,
        instanceDisk: this.state.hddSize,
        spot: this.state.isSpot,
        regionId: this.props.cloudRegionId
      });
    });
  }
}
