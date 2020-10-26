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

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import LoadTool from '../../../../models/tools/LoadTool';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import {names} from '../../../../models/utils/ContextualPreference';
import ToolImage from '../../../../models/tools/ToolImage';
import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';
import LoadToolScanTags from '../../../../models/tools/LoadToolScanTags';
import LoadToolScanPolicy from '../../../../models/tools/LoadToolScanPolicy';
import PipelineRunEstimatedPrice from '../../../../models/pipelines/PipelineRunEstimatedPrice';
import {getVersionRunningInfo} from '../../../tools/utils';
import LoadingView from '../../../special/LoadingView';
import roleModel from '../../../../utils/roleModel';
import highlightText from '../../../special/highlightText';
import JobEstimatedPriceInfo from '../../../special/job-estimated-price-info';
import {Alert, Button, Col, Icon, message, Modal, Row} from 'antd';
import {
  getInputPaths,
  getOutputPaths,
  performAsyncCheck,
  submitsRun,
  modifyPayloadForAllowedInstanceTypes,
  run,
  runPipelineActions,
  RunConfirmation
} from '../../../runs/actions';
import {autoScaledClusterEnabled} from '../../../pipelines/launch/form/utilities/launch-cluster';
import {CP_CAP_LIMIT_MOUNTS} from '../../../pipelines/launch/form/utilities/parameters';
import CardsPanel from './components/CardsPanel';
import {getDisplayOnlyFavourites} from '../utils/favourites';
import styles from './Panel.css';

const findGroupByNameSelector = (name) => (group) => {
  return group.name.toLowerCase() === name.toLowerCase();
};
const findGroupByName = (groups, name) => {
  return groups.filter(findGroupByNameSelector(name))[0];
};

@roleModel.authenticationInfo
@submitsRun
@inject('awsRegions', 'dataStorageAvailable', 'dockerRegistries', 'preferences', 'authenticatedUserInfo')
@runPipelineActions
@observer
export default class PersonalToolsPanel extends React.Component {

  static propTypes = {
    completedRuns: PropTypes.object,
    panelKey: PropTypes.string,
    onInitialize: PropTypes.func
  };

  get usesCompletedRuns () {
    return true;
  }

  state = {
    runToolInfo: null
  };

  isAdmin = () => {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    return this.props.authenticatedUserInfo.value.admin;
  };

  getDefaultGroups = (groups) => {
    const result = [];
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
      const adGroups = (authenticatedUserInfo.value.groups || []).map(g => g);
      const performGroupName = (groupName) => {
        if (groupName && groupName.toLowerCase().indexOf('role_') === 0) {
          return groupName.substring('role_'.length);
        }
        return groupName;
      };
      const rolesGroups = (authenticatedUserInfo.value.roles || []).map(r => performGroupName(r.name));
      const candidates = [...adGroups, ...rolesGroups];
      for (let i = 0; i < candidates.length; i++) {
        const group = findGroupByName(groups, candidates[i]);
        if (group) {
          result.push(group);
        }
      }
    }
    const personal = (groups || []).filter(g => g.privateGroup)[0];
    if (personal) {
      result.push(personal);
    }
    return result;
  };

  searchToolFn = (tool, search) => {
    if (!search) {
      return true;
    }
    return (tool.image || '').toLowerCase().indexOf(search.toLowerCase()) >= 0;
  };

  @computed
  get awsRegions () {
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || []).map(r => r);
    }
    return [];
  }

  @computed
  get defaultCloudRegionId () {
    const [defaultRegion] = this.awsRegions.filter(r => r.default);
    if (defaultRegion) {
      return `${defaultRegion.id}`;
    }
    return null;
  }

  @computed
  get tools () {
    if (this.props.dockerRegistries.loaded) {
      const result = [];
      const registries = (this.props.dockerRegistries.value.registries || []).map(r => r);
      for (let i = 0; i < registries.length; i++) {
        const registry = registries[i];
        const groups = (registry.groups || []).map(g => g);
        const defaultGroups = this.getDefaultGroups(groups);
        if (defaultGroups && defaultGroups.length > 0) {
          for (let g = 0; g < defaultGroups.length; g++) {
            const tools = (defaultGroups[g].tools || []).map(t => t);
            for (let t = 0; t < tools.length; t++) {
              result.push({...tools[t], registry, isGlobal: false});
            }
          }
        }
        for (let j = 0; j < groups.length; j++) {
          const group = groups[j];
          if (!defaultGroups || defaultGroups.filter(g => g.id === group.id).length === 0) {
            const tools = (group.tools || []).map(t => t);
            for (let t = 0; t < tools.length; t++) {
              result.push({...tools[t], registry, isGlobal: true});
            }
          }
        }
      }
      if (this.props.completedRuns.loaded) {
        const allRuns = (this.props.completedRuns.value || []).map(a => a)
          .map(r => r.dockerImage)
          .filter(r => !!r)
          .map(r => {
            const [registry, group, imageWithVersion] = r.toLowerCase().split('/');
            if (registry && group && imageWithVersion) {
              const [image] = imageWithVersion.split(':');
              return `${registry}/${group}/${image}`;
            } else {
              return r.toLowerCase();
            }
          });
        result.sort((t1, t2) => {
          let i1 = allRuns.indexOf(`${t1.registry.path.toLowerCase()}/${t1.image}`);
          let i2 = allRuns.indexOf(`${t2.registry.path.toLowerCase()}/${t2.image}`);
          if (i1 === -1) {
            i1 = Infinity;
          }
          if (i2 === -1) {
            i2 = Infinity;
          }
          return i1 - i2;
        });
      }
      return result;
    }
    return [];
  }

  runToolWithDefaultSettings = async () => {
    const payload = this.state.runToolInfo.payload;
    if (this.state.runToolInfo.isSpot !== undefined) {
      payload.isSpot = this.state.runToolInfo.isSpot;
    }
    if (this.state.runToolInfo.instanceType !== undefined) {
      payload.instanceType = this.state.runToolInfo.instanceType;
    }
    if (this.state.runToolInfo.hddSize !== undefined) {
      payload.hddSize = this.state.runToolInfo.hddSize;
    }
    if (this.state.runToolInfo.limitMounts !== undefined) {
      if (!payload.params) {
        payload.params = {};
      }
      if (this.state.runToolInfo.limitMounts.value) {
        payload.params[CP_CAP_LIMIT_MOUNTS] = this.state.runToolInfo.limitMounts;
      } else if (payload.params[CP_CAP_LIMIT_MOUNTS]) {
        delete payload.params[CP_CAP_LIMIT_MOUNTS];
      }
    }
    if (await run(this)(payload, false)) {
      this.setState({
        runToolInfo: null
      }, this.props.refresh);
    }
  };

  runToolWithCustomSettings = (toolId, version, warning) => {
    const navigate = () => {
      if (version) {
        this.props.router.push(`/launch/tool/${toolId}?version=${version}`);
      } else {
        this.props.router.push(`/launch/tool/${toolId}`);
      }
    };
    if (warning) {
      Modal.confirm({
        title: warning,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          navigate();
        }
      });
    } else {
      navigate();
    }
  };

  onRunToolClicked = async (tool) => {
    const hide = message.loading('Fetching tool info...', 0);
    const toolRequest = new LoadTool(tool.id);
    await toolRequest.fetch();
    const toolTagRequest = new LoadToolScanTags(tool.id);
    await toolTagRequest.fetch();
    const scanPolicy = new LoadToolScanPolicy();
    const toolSettings = new LoadToolVersionSettings(tool.id);
    await toolSettings.fetch();
    await this.props.dockerRegistries.fetchIfNeededOrWait();
    const [registry] = (this.props.dockerRegistries.value.registries || [])
      .filter(r => r.id === tool.registryId);
    if (toolRequest.error) {
      hide();
      message.error(toolRequest.error);
    } else if (toolTagRequest.error) {
      hide();
      message.error(toolTagRequest.error);
    } else if (scanPolicy.error) {
      hide();
      message.error(scanPolicy.error);
    } else if (toolSettings.error) {
      hide();
      message.error(toolSettings.error);
    } else {
      const toolValue = toolRequest.value;
      const versions = toolTagRequest.value.toolVersionScanResults;

      let defaultTag;
      let anyTag;
      if (versions['latest']) {
        defaultTag = 'latest';
      } else if (Object.keys(versions).length === 1) {
        defaultTag = Object.keys(versions)[0];
      } else {
        anyTag = Object.keys(versions)[0];
      }
      const parameterIsNotEmpty = (parameter, additionalCriteria) =>
        parameter !== null &&
        parameter !== undefined &&
        `${parameter}`.trim().length > 0 &&
        (!additionalCriteria || additionalCriteria(parameter));
      const [versionSettings] = (toolSettings.value || [])
        .filter(v => (v.version || '').toLowerCase() === defaultTag);
      const versionSettingValue = (settingName) => {
        if (versionSettings &&
          versionSettings.settings &&
          versionSettings.settings.length &&
          versionSettings.settings[0].configuration) {
          return versionSettings.settings[0].configuration[settingName];
        }
        return null;
      };
      const defaultCommandIsNotEmpty =
        parameterIsNotEmpty(versionSettingValue('cmd_template')) ||
        parameterIsNotEmpty(toolValue.defaultCommand) ||
        parameterIsNotEmpty(this.props.preferences.getPreferenceValue('launch.cmd.template'));
      const instanceTypeIsNotEmpty =
        parameterIsNotEmpty(versionSettingValue('instance_size')) ||
        parameterIsNotEmpty(toolValue.instanceType) ||
        parameterIsNotEmpty(this.props.preferences.getPreferenceValue('cluster.instance.type'));
      const diskIsNotEmpty =
        parameterIsNotEmpty(versionSettingValue('instance_disk'), p => +p > 0) ||
        parameterIsNotEmpty(toolValue.disk, p => +p > 0) ||
        parameterIsNotEmpty(this.props.preferences.getPreferenceValue('cluster.instance.hdd'), p => +p > 0);
      if (!defaultTag ||
        !defaultCommandIsNotEmpty ||
        !instanceTypeIsNotEmpty ||
        !diskIsNotEmpty) {
        const version = defaultTag || anyTag;
        const {
          allowedToExecute,
          tooltip,
          launchTooltip
        } = getVersionRunningInfo(
          version,
          versions,
          scanPolicy.value,
          this.isAdmin(),
          this.props.preferences,
          registry);
        hide();
        if (allowedToExecute) {
          this.runToolWithCustomSettings(tool.id, defaultTag, launchTooltip);
        } else {
          message.error(tooltip);
        }
      } else {
        const chooseDefaultValue = (versionSettingsValue, toolValue, settingsValue, additionalCriteria) => {
          if (parameterIsNotEmpty(versionSettingsValue, additionalCriteria)) {
            return versionSettingsValue;
          }
          if (parameterIsNotEmpty(toolValue, additionalCriteria)) {
            return toolValue;
          }
          return settingsValue;
        };
        const version = defaultTag;
        const prepareParameters = (parameters) => {
          const result = {};
          for (let key in parameters) {
            if (parameters.hasOwnProperty(key)) {
              result[key] = {
                type: parameters[key].type,
                value: parameters[key].value,
                required: parameters[key].required,
                defaultValue: parameters[key].defaultValue
              };
            }
          }
          return result;
        };
        const cloudRegionIdValue = parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
          ? versionSettingValue('cloudRegionId')
          : this.defaultCloudRegionId;
        const allowedInstanceTypesRequest = new AllowedInstanceTypes(
          tool.id,
          cloudRegionIdValue,
          parameterIsNotEmpty(versionSettingValue('is_spot'))
            ? versionSettingValue('is_spot')
            : this.props.preferences.useSpot
        );
        await allowedInstanceTypesRequest.fetch();
        let availableInstanceTypes = [];
        let availablePriceTypes = [];
        if (allowedInstanceTypesRequest.loaded) {
          availableInstanceTypes = (allowedInstanceTypesRequest.value[names.allowedToolInstanceTypes] || []).map(i => i);
          availablePriceTypes = (allowedInstanceTypesRequest.value[names.allowedPriceTypes] || []).map(p => {
            if (p === 'spot') {
              return true;
            } else if (p === 'on_demand') {
              return false;
            }
            return undefined;
          }).filter(p => p !== undefined);
        }
        const defaultPayload = modifyPayloadForAllowedInstanceTypes({
          instanceType: chooseDefaultValue(
            versionSettingValue('instance_size'),
            toolValue.instanceType,
            this.props.preferences.getPreferenceValue('cluster.instance.type')
          ),
          hddSize: +chooseDefaultValue(
            versionSettingValue('instance_disk'),
            toolValue.disk,
            this.props.preferences.getPreferenceValue('cluster.instance.hdd'),
            p => +p > 0
          ),
          timeout: +(toolValue.timeout || 0),
          cmdTemplate: chooseDefaultValue(
            versionSettingValue('cmd_template'),
            toolValue.defaultCommand,
            this.props.preferences.getPreferenceValue('launch.cmd.template')
          ),
          dockerImage: tool.registry
            ? `${tool.registry.path}/${toolValue.image}${version ? `:${version}` : ''}`
            : `${toolValue.image}${version ? `:${version}` : ''}`,
          params: parameterIsNotEmpty(versionSettingValue('parameters'))
            ? prepareParameters(versionSettingValue('parameters'))
            : {},
          isSpot: parameterIsNotEmpty(versionSettingValue('is_spot'))
            ? versionSettingValue('is_spot')
            : this.props.preferences.useSpot,
          nodeCount: parameterIsNotEmpty(versionSettingValue('node_count'))
            ? +versionSettingValue('node_count')
            : undefined,
          cloudRegionId: cloudRegionIdValue
        }, allowedInstanceTypesRequest);
        const parts = (tool.image || '').toLowerCase().split('/');
        const [image] = parts[parts.length - 1].split(':');
        const {
          allowedToExecute,
          tooltip,
          launchTooltip
        } = getVersionRunningInfo(
          defaultTag,
          versions,
          scanPolicy.value,
          this.isAdmin(),
          this.props.preferences,
          registry);
        const estimatedPriceRequest = new PipelineRunEstimatedPrice();
        await estimatedPriceRequest.send({
          instanceType: defaultPayload.instanceType,
          instanceDisk: defaultPayload.hddSize,
          spot: defaultPayload.isSpot,
          regionId: defaultPayload.cloudRegionId
        });
        if (allowedToExecute) {
          const inputs = getInputPaths(null, defaultPayload.params);
          const outputs = getOutputPaths(null, defaultPayload.params);
          const {errors: permissionErrors} = await performAsyncCheck({
            inputs,
            outputs,
            dockerImage: defaultPayload.dockerImage,
            dockerRegistries: this.props.dockerRegistries,
            dataStorages: this.props.dataStorageAvailable
          });
          this.setState({
            runToolInfo: {
              tool,
              image,
              tag: defaultTag,
              payload: defaultPayload,
              warning: launchTooltip,
              pricePerHour: estimatedPriceRequest.loaded ? estimatedPriceRequest.value.pricePerHour : false,
              nodeCount: defaultPayload.nodeCount || 0,
              availableInstanceTypes,
              availablePriceTypes,
              permissionErrors
            }
          });
        } else {
          message.error(tooltip);
        }
        hide();
      }
    }
  };

  renderTool = (tool, search) => {
    const renderMainInfo = () => {
      let name = (tool.image || '');
      let group;
      const imageParts = (tool.image || '').split('/');
      if (imageParts.length === 2) {
        [group, name] = imageParts;
      }
      return [
        <Row key="name">
          <span type="main" style={{fontSize: 'larger', fontWeight: 'bold'}}>
            {highlightText(name, search)}
          </span>
        </Row>,
        <Row key="description">
          <span style={{fontSize: 'smaller'}}>
            {tool.shortDescription}
          </span>
        </Row>,
        <Row key="group">
          <span style={{fontSize: 'smaller'}}>
            <span>{tool.registry.description || tool.registry.path}</span>
            <Icon type="caret-right" style={{fontSize: 'smaller', margin: '0 2px'}} />
            <span>{highlightText(group, search)}</span>
          </span>
        </Row>
      ];
    };
    if (tool.iconId) {
      return (
        <Row type="flex" align="middle" justify="start" style={{height: '100%'}}>
          <div style={{marginRight: 10, overflow: 'hidden', width: 44, height: 44}}>
            <img src={ToolImage.url(tool.id, tool.iconId)} style={{width: '100%'}} />
          </div>
          <div style={{flex: '1 1 auto', display: 'flex', flexDirection: 'column'}}>
            {renderMainInfo()}
          </div>
        </Row>
      );
    } else {
      return (
        <div
          style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
          {renderMainInfo()}
        </div>
      );
    }
  };

  getToolActions = (tool) => {
    if (roleModel.executeAllowed(tool)) {
      return [{
        title: 'RUN',
        icon: 'play-circle-o',
        action: this.onRunToolClicked
      }];
    }
    return [];
  };

  onChange = (e) => {
    this.setState({
      search: e.target.value
    });
  };

  renderContent = () => {
    const navigate = ({id}) => {
      this.props.router && this.props.router.push(`/tool/${id}`);
    };
    return (
      <div key="cards" style={{flex: '1 1 auto', overflow: 'auto'}}>
        <CardsPanel
          key="cards panel"
          search={{
            placeholder: 'Search tools',
            searchFn: this.searchToolFn
          }}
          panelKey={this.props.panelKey}
          onClick={navigate}
          cardClassName={styles.toolContainer}
          favouriteEnabled
          displayOnlyFavourites={getDisplayOnlyFavourites()}
          emptyMessage={search =>
            search && search.length
              ? `No personal tools found for '${search}'`
              : 'There are no personal tools'}
          actions={this.getToolActions}
          childRenderer={this.renderTool}>
          {this.tools}
        </CardsPanel>
      </div>
    );
  };

  cancelRunTool = () => {
    this.setState({
      runToolInfo: null
    });
  };

  onChangePriceType = async (isSpot) => {
    if (this.state.runToolInfo) {
      const runToolInfo = this.state.runToolInfo;
      runToolInfo.isSpot = isSpot;
      const estimatedPriceRequest = new PipelineRunEstimatedPrice();
      await estimatedPriceRequest.send({
        instanceType: runToolInfo.instanceType !== undefined
          ? runToolInfo.instanceType
          : runToolInfo.payload.instanceType,
        instanceDisk: runToolInfo.hddSize !== undefined
          ? runToolInfo.hddSize
          : runToolInfo.payload.hddSize,
        spot: isSpot,
        regionId: runToolInfo.payload.cloudRegionId
      });
      runToolInfo.pricePerHour = estimatedPriceRequest.value.pricePerHour;
      this.setState({
        runToolInfo
      });
    }
  };

  onChangeInstanceType = async (instanceType) => {
    if (this.state.runToolInfo) {
      const runToolInfo = this.state.runToolInfo;
      runToolInfo.instanceType = instanceType;
      const estimatedPriceRequest = new PipelineRunEstimatedPrice();
      await estimatedPriceRequest.send({
        instanceType: instanceType,
        instanceDisk: runToolInfo.hddSize !== undefined
          ? runToolInfo.hddSize
          : runToolInfo.payload.hddSize,
        spot: runToolInfo.isSpot !== undefined
          ? runToolInfo.isSpot
          : runToolInfo.payload.isSpot,
        regionId: runToolInfo.payload.cloudRegionId
      });
      runToolInfo.pricePerHour = estimatedPriceRequest.value.pricePerHour;
      this.setState({
        runToolInfo
      });
    }
  };

  onChangeDiskSize = async (diskSize) => {
    if (this.state.runToolInfo) {
      const runToolInfo = this.state.runToolInfo;
      runToolInfo.hddSize = diskSize;
      const estimatedPriceRequest = new PipelineRunEstimatedPrice();
      await estimatedPriceRequest.send({
        instanceType: runToolInfo.instanceType !== undefined
          ? runToolInfo.instanceType
          : runToolInfo.payload.instanceType,
        instanceDisk: runToolInfo.hddSize !== undefined
          ? runToolInfo.hddSize
          : runToolInfo.payload.hddSize,
        spot: runToolInfo.isSpot !== undefined
          ? runToolInfo.isSpot
          : runToolInfo.payload.isSpot,
        regionId: runToolInfo.payload.cloudRegionId
      });
      runToolInfo.pricePerHour = estimatedPriceRequest.value.pricePerHour;
      this.setState({
        runToolInfo
      });
    }
  };

  onChangeLimitMounts = (mounts) => {
    if (this.state.runToolInfo) {
      const runToolInfo = this.state.runToolInfo;
      runToolInfo.limitMounts = {
        type: 'string',
        required: false,
        value: mounts
      };
      this.setState({
        runToolInfo
      });
    }
  };

  render () {
    if (!this.props.dockerRegistries.loaded && this.props.dockerRegistries.pending) {
      return <LoadingView />;
    }
    if (this.props.dockerRegistries.error) {
      return <Alert type="warning" message={this.props.dockerRegistries.error} />;
    }
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container} style={{display: 'flex', flexDirection: 'column'}}>
        {this.renderContent()}
        <Modal
          title={
            this.state.runToolInfo && this.state.runToolInfo.warning
              ? `Run ${this.state.runToolInfo ? this.state.runToolInfo.image : undefined} with default settings?`
              : false
          }
          visible={!!this.state.runToolInfo}
          onCancel={this.cancelRunTool}
          width="50%"
          footer={
            <Row type="flex" align="middle" justify="space-between">
              <Button onClick={() => {
                this.runToolWithCustomSettings(this.state.runToolInfo.tool.id, this.state.runToolInfo.tag);
                this.cancelRunTool();
              }}>Run custom</Button>
              <Col>
                <Button
                  onClick={this.cancelRunTool}>
                  Cancel
                </Button>
                <Button
                  disabled={
                    !this.state.runToolInfo ||
                    !this.state.runToolInfo.payload ||
                    !this.state.runToolInfo.payload.instanceType ||
                    (
                      this.state.runToolInfo.permissionErrors &&
                      this.state.runToolInfo.permissionErrors.length > 0
                    )
                  }
                  onClick={this.runToolWithDefaultSettings}
                  type="primary">
                  RUN
                </Button>
              </Col>
            </Row>
          }>
          {
            this.state.runToolInfo && !this.state.runToolInfo.warning &&
            <span style={{fontWeight: 'bold', margin: '20px 0px'}}>
              Run {this.state.runToolInfo ? this.state.runToolInfo.image: ''} with default settings?
            </span>
          }
          {
            this.state.runToolInfo &&
              <RunConfirmation
                cloudRegions={this.props.awsRegions.loaded ? (this.props.awsRegions.value || []).map(r => r) : []}
                cloudRegionId={this.state.runToolInfo.payload.cloudRegionId}
                onChangePriceType={this.onChangePriceType}
                onChangeInstanceType={this.onChangeInstanceType}
                warning={this.state.runToolInfo.warning}
                isSpot={this.state.runToolInfo.payload.isSpot}
                isCluster={
                  this.state.runToolInfo.payload.nodeCount > 0 ||
                  autoScaledClusterEnabled(this.state.runToolInfo.payload.params)
                }
                instanceType={this.state.runToolInfo.payload.instanceType}
                showInstanceTypeSelection={!this.state.runToolInfo.payload.instanceType}
                instanceTypes={this.state.runToolInfo.availableInstanceTypes}
                onDemandSelectionAvailable={this.state.runToolInfo.availablePriceTypes.indexOf(false) >= 0}
                dataStorages={
                  this.props.dataStorageAvailable.loaded
                    ? (this.props.dataStorageAvailable.value || []).map(d => d)
                    : undefined
                }
                limitMounts={
                  this.state.runToolInfo.payload.params &&
                  this.state.runToolInfo.payload.params[CP_CAP_LIMIT_MOUNTS]
                    ? this.state.runToolInfo.payload.params[CP_CAP_LIMIT_MOUNTS].value
                    : undefined
                }
                onChangeLimitMounts={this.onChangeLimitMounts}
                onChangeHddSize={this.onChangeDiskSize}
                nodeCount={+this.state.runToolInfo.payload.nodeCount || 0}
                hddSize={this.state.runToolInfo.payload.hddSize}
                parameters={this.state.runToolInfo.payload.params}
                permissionErrors={this.state.runToolInfo.permissionErrors}
                preferences={this.props.preferences}
              />
          }
          {
            this.state.runToolInfo && this.state.runToolInfo.pricePerHour &&
            <Alert
              type="success"
              style={{margin: 2}}
              message={
                <Row>
                  <JobEstimatedPriceInfo>
                    Estimated price: <b>{
                      Math.ceil(this.state.runToolInfo.pricePerHour * (this.state.runToolInfo.nodeCount + 1)* 100.0) / 100.0
                    }$</b> per hour.
                  </JobEstimatedPriceInfo>
                </Row>
              } />
          }
        </Modal>
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
    this.props.dockerRegistries.fetch();
  }
}
