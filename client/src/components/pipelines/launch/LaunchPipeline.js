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
import {observable, isObservableArray} from 'mobx';
import {Card, Modal, message, Alert} from 'antd';
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
import {LaunchFormPlugin} from '../../plugins';
import {LoadingUtilities} from './utilities/loading-utilities';
import {getRunLaunchPayload, getToolLaunchPayload} from './utilities/payload-utilities';

const DTS_ENVIRONMENT = 'DTS';

const TOOL_STATE_KEY = 'tool';
const PIPELINE_STATE_KEY = 'pipeline';
const CONFIGURATIONS_STATE_KEY = 'configurations';
const VERSIONED_STORAGE_STATE_KEY = 'versionedStorage';
const RUN_STATE_KEY = 'run';
const PREFERENCES_STATE_KEY = 'preferences';

const PAYLOAD_STATE_KEY = 'parameters';

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
      version: components.tool && components.version
        ? components.version
        : undefined
    };
  }
  const continueRun = `${components.continue || 'false'}`.trim().toLowerCase() === 'true';
  return {
    allowedInstanceTypes,
    preferences,
    pipelineId: params.id,
    version: params.version,
    runId: params.runId,
    configurationName: params.configuration,
    image: params.image,
    toolVersion: params.image ? components.version : undefined,
    isVersionedStorage,
    versionedStorageLaunchInfo,
    continueRun: continueRun && params.runId ? params.runId : undefined
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
    currentMetadataEntity: null,
    pending: false
  };

  @observable allowedInstanceTypes;

  loadingUtilities = new LoadingUtilities();

  get currentMetadataEntity () {
    const {currentMetadataEntity} = this.state;
    if (currentMetadataEntity) {
      return currentMetadataEntity.map(m => m);
    }
    return [];
  }

  get runConfigurationId () {
    const {value: run} = this.loadingUtilities.getLoadingState(RUN_STATE_KEY, this.state);
    if (run?.configurationId) {
      return `${run.configurationId}`;
    }
    return null;
  }

  get configurationName () {
    if (this.state.configName) {
      return this.state.configName;
    }
    return this.props.configurationName;
  }

  get configurations () {
    const {value: configurations} = this.loadingUtilities
      .getLoadingState(CONFIGURATIONS_STATE_KEY, this.state);
    if (
      configurations &&
      (Array.isArray(configurations) || isObservableArray(configurations))
    ) {
      return configurations;
    }
    return undefined;
  }

  get currentConfiguration () {
    const {configurations, configurationName} = this;
    if (configurations) {
      const defaultConfiguration = configurations.find((cfg) => cfg.default) || configurations[0];
      if (configurationName) {
        const cfgName = configurationName.toLowerCase();
        return configurations.find((cfg) => (cfg.name || '').toLowerCase() === cfgName) ||
          defaultConfiguration;
      }
      return defaultConfiguration;
    }
    return undefined;
  };

  getCurrentProject = async () => {
    const folderProjectRequest = new FolderProject(this.runConfigurationId, 'CONFIGURATION');
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

  launch = async (payloads, hostedApplicationConfiguration, platform, skipCheck) => {
    this.setState({pending: true}, async () => {
      const {currentConfiguration} = this;
      const payloadsArray = Array.isArray(payloads) ? payloads : [payloads];
      let runResolved;
      if (payloadsArray.length > 0) {
        payloadsArray.forEach((p) => {
          p.configurationName = currentConfiguration
            ? currentConfiguration.name
            : this.configurationName;
        });
        runResolved = await run(this)(
          payloadsArray,
          true,
          undefined,
          undefined,
          this.allowedInstanceTypes,
          hostedApplicationConfiguration,
          platform,
          skipCheck
        );
      }
      this.setState({pending: false});
      if (runResolved) {
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      }
    });
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
    const configurationRequest = new ConfigurationLoad(this.runConfigurationId);
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
        id: this.runConfigurationId,
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

  onPipelineChanged = (pipelineId, pipelineVersion) => {
    const {router, continueRun, configurationName = 'default'} = this.props;
    if (continueRun) {
      // eslint-disable-next-line max-len
      router.push(`/launch/${pipelineId}/${pipelineVersion}/${configurationName}/${continueRun}?continue=true`);
    } else {
      router.push(`/launch/${pipelineId}/${pipelineVersion}`);
    }
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState) {
    const propChanged = (prop) => prevProps[prop] !== this.props[prop];
    const imageChanged = propChanged('image');
    const toolVersionChanged = propChanged('toolVersion');
    const pipelineIdChanged = propChanged('pipelineId');
    const versionChanged = propChanged('version');

    const configurationName = this.state.configName || this.props.configurationName;
    const prevConfigurationName = prevState.configName || prevProps.configurationName;
    const configurationChanged = configurationName !== prevConfigurationName;

    const runIdChanged = propChanged('runId');
    const versionedStorageChanged = propChanged('isVersionedStorage') ||
      !versionedStorageLaunchInfoEqual(
        prevProps.versionedStorageLaunchInfo,
        this.props.versionedStorageLaunchInfo
      );
    if (
      imageChanged ||
      toolVersionChanged ||
      pipelineIdChanged ||
      versionChanged ||
      configurationChanged ||
      runIdChanged ||
      versionedStorageChanged
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      image,
      toolVersion,
      pipelineId,
      version: pipelineVersion,
      runId,
      isVersionedStorage,
      pipelines,
      versionedStorageLaunchInfo = {},
      preferences
    } = this.props;
    const {
      toolId: vsToolId,
      version: vsVersion
    } = versionedStorageLaunchInfo;
    (async () => {
      try {
        const setState = (s) => this.setState(s);
        const preferencesRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          PREFERENCES_STATE_KEY,
          'preferences',
          async () => preferences.fetchIfNeededOrWait(),
          setState
        );
        const toolInfoRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          TOOL_STATE_KEY,
          image,
          async () => {
            if (!image) {
              return undefined;
            }
            console.log('Launch Form: fetching tool info', image);
            const settingsRequest = new LoadToolVersionSettings(image);
            const toolRequest = new LoadTool(image);
            await Promise.all([
              settingsRequest.fetch(),
              toolRequest.fetch()
            ]);
            if (toolRequest.error) {
              throw new Error(toolRequest.error);
            }
            const tool = toolRequest.value;
            const settings = settingsRequest.value;
            return {
              tool,
              settings
            };
          },
          setState
        );
        const runIdRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          RUN_STATE_KEY,
          runId,
          async () => {
            if (!runId) {
              return undefined;
            }
            console.log('Launch Form: fetching run info', runId);
            const request = pipelineRun.run(runId, {refresh: true});
            await request.fetch();
            if (request.error) {
              throw new Error(request.error);
            }
            return request.value;
          },
          setState
        );
        const pipelineRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          PIPELINE_STATE_KEY,
          pipelineId,
          async () => {
            if (!pipelineId) {
              return undefined;
            }
            console.log('Launch Form: fetching pipeline info', pipelineId);
            const request = pipelines.getPipeline(pipelineId);
            await request.fetch();
            if (request.error) {
              throw new Error(request.error);
            }
            return request.value;
          },
          setState
        );
        const configurationsRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          CONFIGURATIONS_STATE_KEY,
          pipelineId && pipelineVersion && !isVersionedStorage
            ? `${pipelineId}:${pipelineVersion}`
            : undefined,
          async () => {
            if (!pipelineId || !pipelineVersion || isVersionedStorage) {
              return [];
            }
            console.log('Launch Form: fetching pipeline configurations', pipelineId);
            const request = new PipelineConfigurations(
              pipelineId,
              pipelineVersion
            );
            await request.fetch();
            if (request.error) {
              throw new Error(request.error);
            }
            return (request.value || []).map((c) => ({
              ...c
            }));
          },
          setState
        );
        const versionedStorageToolRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          'vsTool',
          vsToolId,
          async () => {
            if (!vsToolId) {
              return undefined;
            }
            console.log('Launch Form: fetching vs tool info', vsToolId);
            const toolRequest = new LoadTool(vsToolId);
            await toolRequest.fetch();
            if (toolRequest.error) {
              throw new Error(toolRequest.error);
            }
            return toolRequest.value;
          },
          setState
        );
        const versionedStoragePayloadRequest = this.loadingUtilities.loadWithSetStateCallbacks(
          VERSIONED_STORAGE_STATE_KEY,
          vsToolId && vsVersion ? `${vsToolId}:${vsVersion}` : undefined,
          async () => {
            const tool = await versionedStorageToolRequest;
            if (!tool) {
              return undefined;
            }
            console.log('Launch Form: fetching vs payload', vsToolId, vsVersion);
            return getToolLaunchingOptions(
              this.props,
              tool,
              vsVersion
            );
          },
          setState
        );
        const payloadRequest = await this.loadingUtilities.loadWithSetStateCallbacks(
          PAYLOAD_STATE_KEY,
          {},
          async () => {
            console.log('Launch Form: updating payload');
            const [
              // eslint-disable-next-line no-unused-vars
              _,
              toolInfo,
              runInfo,
              // eslint-disable-next-line no-unused-vars
              pipelineInfo,
              // eslint-disable-next-line no-unused-vars
              configurations = [],
              vsPayload
            ] = await Promise.all([
              preferencesRequest,
              toolInfoRequest,
              runIdRequest,
              pipelineRequest,
              configurationsRequest,
              versionedStoragePayloadRequest
            ]);
            const {
              tool,
              settings
            } = toolInfo || {};
            const {currentConfiguration} = this;
            const configuration = currentConfiguration
              ? currentConfiguration.configuration
              : undefined;
            if (tool && settings) {
              return getToolLaunchPayload({
                tool,
                settings,
                toolVersion
              });
            }
            if (runInfo) {
              console.log('runInfo', runInfo)
              return getRunLaunchPayload({
                run: runInfo,
                configuration,
                preferences
              });
            }
            if (configuration) {
              console.log('configuration', configuration)
              return configuration;
            }
            if (vsPayload) {
              return {
                cmd_template: vsPayload.cmdTemplate,
                docker_image: vsPayload.dockerImage,
                is_spot: vsPayload.isSpot,
                instance_size: vsPayload.instanceType,
                instance_disk: vsPayload.hddSize,
                node_count: vsPayload.nodeCount,
                timeout: vsPayload.timeout,
                parameters: {...(vsPayload.params || {})},
                cloudRegionId: vsPayload.cloudRegionId,
                pipelineId: pipelineId !== undefined &&
                pipelineId !== null &&
                !Number.isNaN(pipelineId)
                  ? Number(pipelineId)
                  : undefined,
                pipelineVersion
              };
            }
            return {
              parameters: {}
            };
          },
          setState
        );
        console.log(`Launch Form: payload updated`, payloadRequest);
        if (!this.allowedInstanceTypes) {
          this.allowedInstanceTypes = image
            ? this.props.allowedInstanceTypes.getAllowedTypes(image)
            : new AllowedInstanceTypes();
        }
        this.allowedInstanceTypes.setParameters({
          isSpot: payloadRequest.is_spot,
          regionId: payloadRequest.cloudRegionId,
          toolId: image,
          requestAllRegionsForProviders: ['GCP']
        });
      } catch (error) {
        message.error(error.message, 5);
        console.error(error);
      }
    })();
  };

  render () {
    return (
      <LaunchFormPlugin
        style={{width: '100%', height: '100%', overflow: 'auto'}}
        pipelineId={this.props.pipelineId}
        pipelineVersion={this.props.version}
        pipelineConfiguration={this.props.configurationName}
        runId={this.props.runId}
        toolId={this.props.image}
        toolVersion={this.props.toolVersion}
      >
        {this.renderDefault()}
      </LaunchFormPlugin>
    );
  }

  getLoadingStateErrors = () => {
    const getErrors = (...states) => states
      .map((st) => this.loadingUtilities.getLoadingState(st, this.state).error)
      .filter(Boolean);
    return getErrors(
      PIPELINE_STATE_KEY,
      TOOL_STATE_KEY,
      CONFIGURATIONS_STATE_KEY,
      RUN_STATE_KEY,
      VERSIONED_STORAGE_STATE_KEY,
      PREFERENCES_STATE_KEY,
      PAYLOAD_STATE_KEY
    );
  };

  renderDefault () {
    const {
      pending: payloadPending,
      value: parameters = {},
      error
    } = this.loadingUtilities.getLoadingState(PAYLOAD_STATE_KEY, this.state);
    const {
      value: pipeline
    } = this.loadingUtilities.getLoadingState(PIPELINE_STATE_KEY, this.state);
    // const parametersMock = {
    //   ...parameters,
    //   parameters: {
    //     ...parameters.parameters,
    //     'GENOME': {
    //       'type': 'metadata_entity',
    //       'value': '',
    //       'multiple': false,
    //       'metadataConfig': {
    //         'folderId': '',
    //         'metadataClass': 'genomes',
    //         'nameField': 'Name',
    //         'params': {
    //           'PARAM_FASTA': 'FASTA',
    //           'PARAM_GTF': 'GTF',
    //           'PARAM_GTF2': 'ADDITIONAL_GTF',
    //           'PARAM_ASDASDADSASD': 'GTF'
    //         }
    //       }
    //     }
    //   }
    // };
    console.log('parameters', parameters)
    const errors = this.getLoadingStateErrors();
    if (payloadPending) {
      return <LoadingView />;
    }
    if (error) {
      return <Alert type="error" message={error} />;
    }
    const alerts = errors.map((er) => ({
      message: er,
      type: 'warning'
    }));
    if (!this.allowedInstanceTypes) {
      return <LoadingView />;
    }
    const {
      configurations,
      currentConfiguration
    } = this;
    const pipelineType = pipeline?.pipelineType || '';
    const isVersioned = pipelineType.toLowerCase() === 'versioned_storage';
    if (isVersioned) {
      alerts.push({
        message: [
          'You are going to launch a versioned storage.',
          'The latest revision of the VS will be cloned.'
        ].join(' '),
        type: 'info'
      });
    }
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
          pending={this.state.pending}
          defaultPriceTypeIsSpot={this.props.preferences.useSpot}
          editConfigurationMode={false}
          currentConfigurationName={
            currentConfiguration
              ? currentConfiguration.name
              : this.configurationName
          }
          pipeline={pipeline}
          allowedInstanceTypes={this.allowedInstanceTypes}
          version={this.props.version}
          parameters={parameters}
          configurations={configurations}
          alerts={alerts}
          onConfigurationChanged={this.onConfigurationChanged}
          onPipelineChanged={this.onPipelineChanged}
          onLaunch={this.launch}
          runConfiguration={this.prepareRunPayload}
          runConfigurationId={this.runConfigurationId}
          isDetachedConfiguration={false}
          continueRun={this.props.continueRun}
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
