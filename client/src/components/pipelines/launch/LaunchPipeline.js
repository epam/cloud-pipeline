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
import {inject, observer} from 'mobx-react';
import {observable, computed} from 'mobx';
import {Alert, Card, Modal, message} from 'antd';
import classNames from 'classnames';
import localization from '../../../utils/localization';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import LoadTool from '../../../models/tools/LoadTool';
import AllowedInstanceTypes from '../../../models/utils/AllowedInstanceTypes';
import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';
import PipelineConfigurations from '../../../models/pipelines/PipelineConfigurations';
import FolderProject from '../../../models/folders/FolderProject';
import ConfigurationLoad from '../../../models/configuration/ConfigurationLoad';
import ConfigurationRun from '../../../models/configuration/ConfigurationRun';
import MetadataEntityFields from '../../../models/folderMetadata/MetadataEntityFields';
import MetadataBrowser from './dialogs/MetadataBrowser';
import {submitsRun, run, runPipelineActions} from '../../runs/actions';
import styles from './LaunchPipeline.css';
import LoadingView from '../../special/LoadingView';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import queryParameters from '../../../utils/queryParameters';
import LaunchPipelineForm from './form/LaunchPipelineForm';
import getToolLaunchingOptions from './utilities/get-tool-launching-options';
import versionedStorageLaunchInfoEqual from './utilities/versioned-storage-launch-info-equal';
import roleModel from '../../../utils/roleModel';

const DTS_ENVIRONMENT = 'DTS';

@localization.localizedComponent
@submitsRun
@runPipelineActions
@roleModel.authenticationInfo
@inject('awsRegions', 'pipelines', 'preferences', 'dockerRegistries', 'usersInfo')
@inject(({allowedInstanceTypes, routing, pipelines, preferences}, {params}) => {
  const components = queryParameters(routing);
  const isVersionedStorage = components.vs;
  let versionedStorageLaunchInfo;
  if (isVersionedStorage) {
    versionedStorageLaunchInfo = {
      toolId: components.tool,
      tool: components.tool ? new LoadTool(components.tool) : undefined,
      version: components.tool && components.version
        ? components.version
        : undefined
    };
  }
  return {
    allowedInstanceTypes: allowedInstanceTypes,
    preferences,
    pipeline: params.id ? pipelines.getPipeline(params.id) : undefined,
    run: params.runId ? pipelineRun.run(params.runId, {refresh: true}) : undefined,
    pipelineId: params.id,
    version: params.version,
    runId: params.runId,
    configurationName: params.configuration,
    image: params.image,
    tool: params.image ? new LoadTool(params.image) : undefined,
    toolVersion: params.image ? components.version : undefined,
    toolSettings: params.image ? new LoadToolVersionSettings(params.image) : undefined,
    configurations: params.id && params.version && !isVersionedStorage
      ? new PipelineConfigurations(params.id, params.version)
      : undefined,
    isVersionedStorage,
    versionedStorageLaunchInfo
  };
})
@observer
class LaunchPipeline extends localization.LocalizedReactComponent {
  state = {
    configName: null,
    launching: false,
    runPayload: null,
    showMetadataBrowser: false,
    currentProjectId: null,
    currentMetadataEntity: null
  };

  @observable allowedInstanceTypes;
  @observable versionedStoragesLaunchPayload;

  get pipelinePending () {
    return !!this.props.pipeline && this.props.pipeline.pending;
  }

  get configurationsPending () {
    return !!this.props.configurations && this.props.configurations.pending;
  }

  get runPending () {
    return !!this.props.run && this.props.run.pending;
  }

  get toolPending () {
    return !!this.props.tool && this.props.tool.pending;
  }

  get toolSettingsPending () {
    return !!this.props.toolSettings && this.props.toolSettings.pending;
  }

  get versionedStoragesLaunchPayloadPending () {
    return this.props.isVersionedStorage &&
      (
        !this.versionedStoragesLaunchPayload ||
        (
          this.versionedStoragesLaunchPayload.pending &&
          !this.versionedStoragesLaunchPayload.loaded
        )
      );
  }

  get currentMetadataEntity () {
    const {currentMetadataEntity} = this.state;
    if (currentMetadataEntity) {
      return currentMetadataEntity.map(m => m);
    }
    return [];
  }

  getPipelineParameter = (parameterName) => {
    const configuration = this.getConfigurationParameters();
    if (configuration && configuration.parameters) {
      for (let key in configuration.parameters) {
        if (configuration.parameters.hasOwnProperty(key) && parameterName === key) {
          return configuration.parameters[key];
        }
      }
    }
    return null;
  };

  @computed
  get configurationId () {
    const {run} = this.props;
    if (run?.value?.configurationId) {
      return `${run.value.configurationId}`;
    }
    return null;
  }

  get configurationName () {
    if (this.state.configName) {
      return this.state.configName;
    }
    return this.props.configurationName;
  }

  get currentConfiguration () {
    if (!this.props.configurations ||
      this.props.configurations.pending ||
      this.props.configurations.error) {
      return undefined;
    }
    let configuration;
    if (this.configurationName) {
      [configuration] = (this.props.configurations.value || []).filter(c => {
        return c.name.toLowerCase() === this.configurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value || []).filter(c => c.default);
    }
    if (!configuration && (this.props.configurations.value || []).length > 0) {
      configuration = this.props.configurations.value[0];
    }
    return configuration;
  };

  getConfigurationParameters = () => {
    if (!this.props.configurations ||
      this.props.configurations.pending ||
      this.props.configurations.error) {
      return undefined;
    }
    let configuration;
    if (this.configurationName) {
      [configuration] = (this.props.configurations.value || []).filter(c => {
        return c.name.toLowerCase() === this.configurationName.toLowerCase();
      });
    }
    if (!configuration) {
      [configuration] = (this.props.configurations.value || []).filter(c => c.default);
    }
    if (!configuration) {
      return undefined;
    }
    return {
      allowedInstanceTypes: this.props.allowedInstanceTypes,
      ...configuration.configuration
    };
  };

  getParameters = () => {
    if (this.props.tool &&
      !this.toolPending &&
      !this.toolSettingsPending &&
      !this.props.tool.error) {
      const toolVersion = (this.props.toolVersion || 'latest').toLowerCase();
      const [versionSettings] = (this.props.toolSettings.value || [])
        .filter(v => (v.version || '').toLowerCase() === toolVersion);
      const [defaultVersionSettings] = (this.props.toolSettings.value || [])
        .filter(v => (v.version || '').toLowerCase() === 'latest');
      const versionSettingValue = (settingName) => {
        if (versionSettings &&
          versionSettings.settings &&
          versionSettings.settings.length &&
          versionSettings.settings[0].configuration) {
          return versionSettings.settings[0].configuration[settingName];
        }
        if (defaultVersionSettings &&
          defaultVersionSettings.settings &&
          defaultVersionSettings.settings.length &&
          defaultVersionSettings.settings[0].configuration) {
          return defaultVersionSettings.settings[0].configuration[settingName];
        }
        return null;
      };
      const parameterIsNotEmpty = (parameter, additionalCriteria) =>
        parameter !== null &&
        parameter !== undefined &&
        `${parameter}`.trim().length > 0 &&
        (!additionalCriteria || additionalCriteria(parameter));
      const image = `${this.props.tool.value.registry}/${this.props.tool.value.image}`;
      return {
        cmd_template: versionSettingValue('cmd_template') || this.props.tool.value.defaultCommand,
        docker_image: this.props.toolVersion
          ? `${image}:${this.props.toolVersion}`
          : image,
        instance_disk: +versionSettingValue('instance_disk') || this.props.tool.value.disk,
        instance_size: versionSettingValue('instance_size') || this.props.tool.value.instanceType,
        is_spot: versionSettingValue('is_spot'),
        parameters: versionSettingValue('parameters'),
        node_count: parameterIsNotEmpty(versionSettingValue('node_count'))
          ? +versionSettingValue('node_count')
          : undefined,
        cloudRegionId: parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
          ? versionSettingValue('cloudRegionId')
          : undefined,
        notifications: versionSettingValue('notifications') || []
      };
    } else if (this.props.run && !this.runPending && !this.props.run.error) {
      const parameters = {
        cmd_template: this.props.run.value.cmdTemplate,
        docker_image: this.props.run.value.dockerImage,
        is_spot: this.props.preferences.useSpot
      };
      if (this.props.run.value.instance) {
        parameters.instance_size = this.props.run.value.instance.nodeType;
        parameters.instance_disk = this.props.run.value.instance.nodeDisk;
        parameters.is_spot = this.props.run.value.instance.spot;
        parameters.cloudRegionId = this.props.run.value.instance.cloudRegionId;
      }
      parameters.parameters = {};
      if (this.props.run.value.pipelineRunParameters) {
        for (let i = 0; i < this.props.run.value.pipelineRunParameters.length; i++) {
          const param = this.props.run.value.pipelineRunParameters[i];
          if (param.name && param.value) {
            const parameterInfo = this.getPipelineParameter(param.name);
            const type = param.type
              ? param.type
              : (parameterInfo && parameterInfo.type ? parameterInfo.type : 'string');
            const required = parameterInfo && parameterInfo.required
              ? parameterInfo.required : false;
            parameters.parameters[param.name] = {
              value: param.value,
              resolvedValue: param.resolvedValue,
              type,
              required,
              enum: param.enum
            };
          }
        }
      }
      return parameters;
    } else if (this.getConfigurationParameters()) {
      return this.getConfigurationParameters();
    } else if (
      this.props.isVersionedStorage &&
      this.versionedStoragesLaunchPayload &&
      this.versionedStoragesLaunchPayload.loaded &&
      this.versionedStoragesLaunchPayload.value
    ) {
      const payload = this.versionedStoragesLaunchPayload.value;
      return {
        cmd_template: payload.cmdTemplate,
        docker_image: payload.dockerImage,
        is_spot: payload.isSpot,
        instance_size: payload.instanceType,
        instance_disk: payload.hddSize,
        node_count: payload.nodeCount,
        timeout: payload.timeout,
        parameters: {...(payload.params || {})},
        cloudRegionId: payload.cloudRegionId,
        allowedInstanceTypes: this.props.allowedInstanceTypes,
        pipelineId: this.props.pipelineId ? +(this.props.pipelineId) : undefined,
        pipelineVersion: this.props.version
      };
    }
    return {
      allowedInstanceTypes: this.props.allowedInstanceTypes,
      parameters: {}
    };
  };

  getConfigurations = () => {
    if (!!this.props.configurations &&
      !this.props.configurations.pending &&
      !this.props.configurations.error) {
      return (this.props.configurations.value || []).map(c => c);
    }
    return [];
  };

  getCurrentProject = async () => {
    const folderProjectRequest = new FolderProject(this.configurationId, 'CONFIGURATION');
    await folderProjectRequest.fetch();
    if (folderProjectRequest.error) {
      message.error(folderProjectRequest.error, 5);
      return null;
    }
    return folderProjectRequest.value;
  };

  getCurrentMetadataEntity = async (projectId) => {
    const metadataEntityFieldsRequest = new MetadataEntityFields(projectId);
    await metadataEntityFieldsRequest.fetch();
    if (metadataEntityFieldsRequest.error) {
      message.error(metadataEntityFieldsRequest.error, 5);
      return null;
    }
    return metadataEntityFieldsRequest.value;
  };

  launch = async (payload, hostedApplicationConfiguration, skipCheck) => {
    payload.configurationName = this.currentConfiguration
      ? this.currentConfiguration.name
      : this.configurationName;
    if (await run(this)(
      payload,
      true,
      undefined,
      undefined,
      undefined,
      hostedApplicationConfiguration,
      skipCheck
    )) {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    }
  };

  showMetadataBrowser = () => {
    this.setState({showMetadataBrowser: true});
  };

  closeMetadataBrowser = () => {
    this.setState({showMetadataBrowser: false}, () => {
      this.clearRunPayload();
    });
  };

  clearRunPayload = () => {
    this.setState({
      runPayload: null,
      currentProjectId: null,
      currentMetadataEntity: null
    });
  };

  prepareRunPayload = async (payload) => {
    const hide = message.loading('Preparing run payload...', 0);
    const currentProject = await this.getCurrentProject();
    if (currentProject?.id) {
      const currentProjectId = currentProject.id;
      const metadataEntity = await this.getCurrentMetadataEntity(currentProjectId) || [];
      this.setState({
        runPayload: payload,
        showMetadataBrowser: true,
        currentProjectId,
        currentMetadataEntity: metadataEntity
      });
    }
    hide();
  };

  selectMetadataConfirm = async (entitiesIds) => {
    const {runPayload} = this.state;
    let configuration;
    const configurationRequest = new ConfigurationLoad(this.configurationId);
    await configurationRequest.fetch();
    if (configurationRequest.error) {
      message.error(configurationRequest.error, 5);
      return;
    } else {
      const entries = ((configurationRequest.value || {}).entries || []).slice();
      configuration = entries.find(entry => entry.default) || entries.pop();
      let title = `Launch ${configuration.name} configuration ?`;
      Modal.confirm({
        title: title,
        style: {
          wordWrap: 'break-word'
        },
        onOk: () => {
          launchFn();
        }
      });
    }
    const launchFn = async () => {
      const metadataControlParameter = (parameter = {}) => {
        const {value} = parameter;
        const controlPrefixes = ['this.', 'project.'];
        if (value && typeof value === 'string') {
          const normalizedValue = value.trim().toLowerCase();
          return controlPrefixes.some(prefix => normalizedValue.startsWith(prefix));
        }
        return false;
      };
      const applyMetadataParamsOverRunParams = (runParams, configParams) => {
        const mappedParams = {...runParams};
        for (const key in mappedParams) {
          if (
            configParams.hasOwnProperty(key) &&
            metadataControlParameter(configParams[key])
          ) {
            mappedParams[key] = configParams[key];
          }
        }
        return mappedParams;
      };
      if (configuration) {
        configuration.configName = configuration.name;
        configuration.pipelineId = null;
        configuration.pipelineVersion = null;
        configuration.methodName = null;
        configuration.methodSnapshot = null;
        configuration.methodConfigurationName = null;
        configuration.methodConfigurationSnapshot = null;
        configuration.methodInputs = null;
        configuration.methodOutputs = null;
        configuration.executionEnvironment = runPayload.executionEnvironment;
        configuration.rootEntityId = runPayload.rootEntityId;
        configuration.endpointName = runPayload.endpointName;
        configuration.stopAfter = runPayload.stopAfter;
        runPayload.pipelineId = undefined;
        runPayload.pipelineVersion = undefined;
        runPayload.configName = undefined;
        runPayload.configuration = undefined;
        runPayload.rootEntityId = undefined;
        runPayload.methodName = undefined;
        runPayload.methodSnapshot = undefined;
        runPayload.methodConfigurationName = undefined;
        runPayload.methodConfigurationSnapshot = undefined;
        runPayload.methodInputs = undefined;
        runPayload.methodOutputs = undefined;
        runPayload.executionEnvironment = undefined;
        runPayload.endpointName = undefined;
        runPayload.stopAfter = undefined;
        if (configuration.executionEnvironment === DTS_ENVIRONMENT) {
          for (const key in runPayload) {
            if (runPayload.hasOwnProperty(key) && runPayload[key] !== undefined) {
              configuration[key] = runPayload[key];
            }
          }
        } else {
          const runParams = runPayload.parameters || {};
          const configParams = configuration.configuration?.parameters || {};
          runPayload.parameters = applyMetadataParamsOverRunParams(runParams, configParams);
          configuration.configuration = runPayload;
        }
      }
      const hide = message.loading('Launching...', 0);
      const request = new ConfigurationRun();
      await request.send({
        id: this.configurationId,
        entries: [configuration],
        entitiesIds: entitiesIds
      });
      hide();
      if (request.error) {
        message.error(request.error);
      } else {
        this.clearRunPayload();
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      }
    };
  };

  onConfigurationChanged = (name) => {
    this.setState({configName: name});
  };

  componentDidMount () {
    this.loadVersionedStorageLaunchPayload();
  }

  componentDidUpdate (prevProps) {
    const parameters = this.getParameters();
    if (!this.allowedInstanceTypes) {
      this.allowedInstanceTypes = this.props.image
        ? this.props.allowedInstanceTypes.getAllowedTypes(this.props.image)
        : new AllowedInstanceTypes();
    }
    if (parameters) {
      this.allowedInstanceTypes.setParameters({
        isSpot: parameters.is_spot,
        regionId: parameters.cloudRegionId,
        toolId: this.props.image
      });
    }
    if (
      (!this.versionedStoragesLaunchPayload && this.props.versionedStorageLaunchInfo) ||
      !versionedStorageLaunchInfoEqual(
        prevProps.versionedStorageLaunchInfo,
        this.props.versionedStorageLaunchInfo
      )
    ) {
      this.loadVersionedStorageLaunchPayload();
    }
  }

  loadVersionedStorageLaunchPayload = () => {
    const {
      versionedStorageLaunchInfo,
      isVersionedStorage
    } = this.props;
    if (!isVersionedStorage) {
      this.versionedStoragesLaunchPayload = undefined;
    } else if (versionedStorageLaunchInfo && versionedStorageLaunchInfo.tool) {
      this.versionedStoragesLaunchPayload = {
        loaded: false,
        pending: true
      };
      versionedStorageLaunchInfo.tool
        .fetchIfNeededOrWait()
        .then(() => {
          if (versionedStorageLaunchInfo.tool.loaded) {
            return getToolLaunchingOptions(
              this.props,
              versionedStorageLaunchInfo.tool.value,
              versionedStorageLaunchInfo.version
            );
          } else {
            throw new Error(versionedStorageLaunchInfo.tool.error || '');
          }
        })
        .then(launchPayload => {
          this.versionedStoragesLaunchPayload = {
            loaded: true,
            pending: false,
            value: launchPayload
          };
        })
        .catch((e) => {
          this.versionedStoragesLaunchPayload = {
            pending: false,
            loaded: false,
            error: e.message
          };
        });
    } else {
      this.versionedStoragesLaunchPayload = {
        loaded: true
      };
    }
  };

  render () {
    if (this.pipelinePending ||
      this.configurationsPending ||
      this.runPending ||
      this.toolPending ||
      this.toolSettingsPending ||
      this.versionedStoragesLaunchPayloadPending ||
      (!this.props.preferences.loaded && this.props.preferences.pending) ||
      !this.allowedInstanceTypes) {
      return <LoadingView />;
    }
    if (this.props.pipeline && this.props.pipeline.error) {
      return <Alert type="warning" message={this.props.pipeline.error} />;
    }
    const pipelineType = this.props.pipeline?.value?.pipelineType || '';
    const isVersioned = pipelineType.toLowerCase() === 'versioned_storage';
    const alerts = [];
    if (this.props.configurations && this.props.configurations.error) {
      alerts.push({
        message: this.props.configurations.error,
        type: 'warning'
      });
    }
    if (this.props.run && this.props.run.error) {
      alerts.push({
        message: this.props.run.error,
        type: 'warning'
      });
    }
    if (this.props.tool && this.props.tool.error) {
      alerts.push({
        message: this.props.tool.error,
        type: 'warning'
      });
    }
    if (this.props.toolSettings && this.props.toolSettings.error) {
      alerts.push({
        message: this.props.toolSettings.error,
        type: 'warning'
      });
    }
    if (this.versionedStoragesLaunchPayload && this.versionedStoragesLaunchPayload.error) {
      alerts.push({
        message: this.versionedStoragesLaunchPayload.error,
        type: 'warning'
      });
    }
    if (isVersioned) {
      alerts.push({
        message: [
          'You are going to launch a versioned storage.',
          'The latest revision of the VS will be cloned.'
        ].join(' '),
        type: 'info'
      });
    }
    const parameters = this.getParameters();
    return (
      <Card
        bodyStyle={{padding: 0, margin: 0}}
        className={
          classNames(
            styles.container,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        <LaunchPipelineForm
          defaultPriceTypeIsSpot={this.props.preferences.useSpot}
          editConfigurationMode={false}
          currentConfigurationName={
            this.currentConfiguration
              ? this.currentConfiguration.name
              : this.configurationName
          }
          pipeline={this.props.pipeline ? this.props.pipeline.value : undefined}
          allowedInstanceTypes={this.allowedInstanceTypes}
          version={this.props.version}
          parameters={parameters}
          configurations={this.getConfigurations()}
          alerts={alerts}
          onConfigurationChanged={this.onConfigurationChanged}
          onLaunch={this.launch}
          runConfiguration={this.prepareRunPayload}
          runConfigurationId={this.configurationId}
          isDetachedConfiguration={false}
        />
        <MetadataBrowser
          multiple={false}
          readOnly
          onCancel={this.closeMetadataBrowser}
          onSelect={this.selectMetadataConfirm}
          visible={
            this.state.showMetadataBrowser &&
            !!this.state.currentProjectId
          }
          initialFolderId={this.state.currentProjectId}
          currentMetadataEntity={this.currentMetadataEntity}
        />
      </Card>
    );
  }
}

export default LaunchPipeline;
