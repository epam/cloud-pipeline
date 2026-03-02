/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {autorun, computed, makeObservable} from 'mobx';
import {observer,
  inject} from 'mobx-react';
import {Alert,
  Button,
  Spin,
  message
} from 'antd';
import {LoadingOutlined} from '@ant-design/icons';
import {Environment, LaunchFormInfo, ParameterGroup} from './index';
import LaunchFormStore from './launch-form-store';
import {getDockerImage} from '../../../../utils/get-docker-image';
import styles from './launch-form.css';
import LoadTool from '../../../../models/tools/LoadTool';
import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';
import {withCurrentUserAttributes} from '../../../../utils/current-user-attributes';
import {modifyPayloadForAllowedInstanceTypes, run} from '../../../runs/actions';
import {getVersionRunningInfo} from '../../../tools/utils';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import LoadToolAttributes from '../../../../models/tools/LoadToolAttributes';
import {LAUNCH_MODES} from './utils';
import GetPipelineVersions from '../../../../models/pipelines/Version';
import PipelineConfigurations from '../../../../models/pipelines/PipelineConfigurations';

const DEFAULT_REGISTRY_ID = 1;

@inject(
  'allowedInstanceTypes',
  'dockerRegistries',
  'awsRegions',
  'router',
  'authenticatedUserInfo',
  'preferences',
  'pipelines'
)
@withCurrentUserAttributes()
@observer
export default class LaunchForm extends React.Component {
  constructor (props) {
    super(props);
    makeObservable(this, {
      isAdmin: computed,
      registries: computed,
      dockerRegistry: computed,
      defaultTag: computed,
      awsRegions: computed,
      defaultCloudRegionId: computed
    });
    this.formStore = new LaunchFormStore();
  }

  state = {
    launchPending: false
  };

  formStore;
  dispose;

  componentDidMount () {
    this.dispose = autorun(() => {
      if (this.props.dockerRegistries.loaded) {
        this.initializeData();
      }
    });
  }

  componentDidUpdate (prevProps) {
    if (prevProps.data !== this.props.data && this.props.dockerRegistries.loaded) {
      this.initializeData();
    }
  }

  componentWillUnmount () {
    if (typeof this.dispose === 'function') {
      this.dispose();
    }
  }

  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return this.props.dockerRegistries.value.registries;
    }
    return [];
  }

  get dockerRegistry () {
    if (this.registries.length > 0 && this.formStore.toolInfo) {
      return this.registries
        .find(r => r.id === this.formStore.toolInfo.registryId);
    }
    return null;
  }

  get defaultTag () {
    if (this.fotmStore.versions?.length) {
      const latest = this.formStore.versions.find(v => v.version === 'latest');
      const first = this.formStore.versions[0];
      if (this.fotmStore.versions.length === 1) {
        return first;
      }
      return latest;
    }
    return null;
  }

  get awsRegions () {
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || []).map(r => r);
    }
    return [];
  }

  get defaultCloudRegionId () {
    const [defaultRegion] = this.awsRegions.filter(r => r.default);
    if (defaultRegion) {
      return `${defaultRegion.id}`;
    }
    return null;
  }

  initializeToolData = async () => {
    const {data} = this.props;
    if (!this.props.dockerRegistries.loaded || !data) {
      return;
    }
    const registry = this.registries.find(r => r.id === DEFAULT_REGISTRY_ID);
    let dockerImage = data.dockerImage || '';
    if (dockerImage.split('/').length === 1) {
      const defaultGroup = registry.groups[0]?.name;
      dockerImage = `${defaultGroup}/${dockerImage}`;
    } else if (dockerImage.split('/').length === 2) {
      dockerImage = `${registry.name}/${dockerImage}`;
    }
    const {tool} = getDockerImage(
      dockerImage,
      this.props.dockerRegistries
    ) || {};
    if (!tool) {
      this.formStore.error = `Tool ${data.dockerImage} not found!`;
      return;
    }
    const [toolVersions, toolInfo, versions] = [
      new LoadToolVersionSettings(tool.id),
      new LoadTool(tool.id),
      new LoadToolAttributes(tool.id)
    ];
    await Promise.all([
      toolVersions,
      toolInfo,
      versions
    ].map(request => request.fetch()));
    if (
      !toolInfo.loaded ||
      toolInfo.error ||
      !toolVersions.loaded ||
      toolVersions.error ||
      !versions.loaded ||
      versions.error
    ) {
      const error = toolVersions.error || toolInfo.error || versions.error;
      this.formStore.error = `Error loading tool info. ${error}`;
      return;
    }
    this.formStore.initializeData({
      data,
      toolInfo: toolInfo.value,
      toolVersion: toolVersions.value
        .find(({version}) => version === (toolInfo.value.version || 'latest')),
      versions: versions.value
    });
  }

  initializePipelineData = async () => {
    const {data, pipelines} = this.props;
    const {pipelineId, pipelineName} = data;
    await pipelines.fetchIfNeededOrWait();
    const rawPipeline = pipelines.value.find(pipeline => {
      return pipelineId
        ? pipeline.id === pipelineId
        : pipeline.name === pipelineName;
    });
    if (!rawPipeline) {
      // eslint-disable-next-line max-len
      this.formStore._error = `Pipeline ${pipelineId || pipelineName} not found!`;
      return;
    }
    const [pipelineRequest, versionsRequest] = [
      pipelines.getPipeline(rawPipeline.id),
      new GetPipelineVersions(rawPipeline.id)
    ];
    await Promise.all([
      pipelineRequest,
      versionsRequest
    ].map(request => request.fetchIfNeededOrWait
      ? request.fetchIfNeededOrWait()
      : request.fetch()
    ));
    const pipeline = pipelineRequest.value;
    const versions = versionsRequest.value;
    let version = versions.find(v => v.name === data.version);
    if (!version) {
      version = versions.find(v => v.name === pipeline.currentVersion?.name);
    }
    const configurations = new PipelineConfigurations(pipeline.id, version.name);
    await configurations.fetch();
    let configuration = (configurations.value || []).find(c => c.name === data.configuration);
    if (!configuration) {
      configuration = (configurations.value || []).find(c => c.default);
    }
    this.formStore.initializeData({
      data,
      pipeline,
      pipelineVersion: version,
      pipelineConfiguration: configuration
    });
  };

  initializeData = async () => {
    const {data} = this.props;
    if (!data) {
      return;
    }
    if (!!data.pipelineId || !!data.pipelineName) {
      return this.initializePipelineData();
    }
    return this.initializeToolData();
  };

  runTool = async () => {
    this.setState({launchPending: true}, async () => {
      const {onRunSuccess} = this.props;
      const {toolVersion, environment} = this.formStore;
      const {version} = toolVersion;
      const hide = message.loading('Fetching tool info...', 0);
      const chooseDefaultValue = (
        versionSettingsValue,
        toolValue,
        settingsValue,
        additionalCriteria
      ) => {
        if (parameterIsNotEmpty(versionSettingsValue, additionalCriteria)) {
          return versionSettingsValue;
        }
        if (parameterIsNotEmpty(toolValue, additionalCriteria)) {
          return toolValue;
        }
        return settingsValue;
      };
      const registry = this.registries.find(r => r.id === this.formStore.toolInfo.registryId);
      const getVersionRunningInformation = (version) => {
        return getVersionRunningInfo(
          version,
          this.props.versions?.loaded ? this.versionsScanResObject : null,
          this.props.scanPolicy?.loaded ? this.props.scanPolicy.value : null,
          this.isAdmin,
          this.props.preferences,
          this.dockerRegistry
        );
      };
      const info = getVersionRunningInformation(
        this.formStore.toolVersion.version || this.defaultTag
      );
      const titleFn = (runName) => ([
        <span key="launch">
          Are you sure you want to launch
        </span>,
        runName,
        <span key="question">
          with default settings?
        </span>
      ]);
      const parameterIsNotEmpty = (parameter, additionalCriteria) =>
        parameter !== null &&
        parameter !== undefined &&
        `${parameter}`.trim().length > 0 &&
        (!additionalCriteria || additionalCriteria(parameter));
      const versionSettingValue = (settingName) => {
        if (this.formStore.toolVersion?.settings &&
          this.formStore.toolVersion?.settings[0]?.configuration
        ) {
          return this.formStore.toolVersion?.settings[0]?.configuration[settingName];
        }
        return null;
      };
      const cloudRegionIdValue = parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
        ? versionSettingValue('cloudRegionId')
        : this.defaultCloudRegionId;
      const allowedInstanceTypesRequest = new AllowedInstanceTypes({
        toolId: this.formStore.toolInfo.id,
        regionId: cloudRegionIdValue,
        spot: environment.isSpot
      });
      await allowedInstanceTypesRequest.fetch();
      const platform = this.formStore.toolVersion.platform;
      const payload = modifyPayloadForAllowedInstanceTypes({
        instanceType: environment.instanceType || chooseDefaultValue(
          versionSettingValue('instance_size'),
          this.formStore.toolInfo.instanceType,
          this.props.preferences.getPreferenceValue('cluster.instance.type')
        ),
        hddSize: +environment.disk || +chooseDefaultValue(
          versionSettingValue('instance_disk'),
          this.formStore.toolInfo.disk,
          this.props.preferences.getPreferenceValue('cluster.instance.hdd'),
          p => +p > 0
        ),
        cmdTemplate: environment.cmd,
        dockerImage: registry
          ? `${registry.path}/${this.formStore.toolInfo.image}${version ? `:${version}` : ''}`
          : `${this.formStore.toolInfo.image}${version ? `:${version}` : ''}`,
        params: this.formStore.parameters,
        isSpot: environment.isSpot
      }, allowedInstanceTypesRequest);
      const runInfo = await run(this)(
        payload,
        true,
        titleFn,
        info.launchTooltip,
        allowedInstanceTypesRequest,
        undefined,
        platform
      );
      hide();
      this.setState({launchPending: false});
      if (runInfo) {
        this.formStore.setRunLaunched(true);
        onRunSuccess && onRunSuccess(runInfo);
      }
    });
  };

  runPipeline = () => {
    this.setState({launchPending: true}, async () => {
      const {onRunSuccess} = this.props;
      const {id} = this.formStore.pipeline;
      const {environment, pipelineConfiguration} = this.formStore;
      const {configuration} = pipelineConfiguration;
      const cloudRegionIdValue = this.defaultCloudRegionId;
      const payload = {
        cloudRegionId: +configuration.cloudRegionId || cloudRegionIdValue,
        pipelineId: id,
        dockerImage: configuration.docker_image,
        instanceType: environment.instanceType,
        hddSize: Number(environment.disk || configuration.instance_disk),
        cmdTemplate: environment.cmd,
        params: this.formStore.parameters,
        isSpot: environment.isSpot
      };
      const allowedInstanceTypesRequest = new AllowedInstanceTypes();
      allowedInstanceTypesRequest.setParameters({
        isSpot: this.formStore.is_spot,
        regionId: configuration.cloudRegionId,
        requestAllRegionsForProviders: ['GCP']
      });
      await allowedInstanceTypesRequest.fetchIfNeededOrWait();
      const platform = this.formStore.pipelineVersion.platform;
      const runInfo = await run(this)(
        payload,
        true,
        undefined,
        undefined,
        allowedInstanceTypesRequest,
        undefined,
        platform
      );
      this.setState({launchPending: false});
      if (runInfo) {
        this.formStore.setRunLaunched(true);
        onRunSuccess && onRunSuccess(runInfo);
      }
    });
  };

  onLaunch = async () => {
    if (this.formStore.mode === LAUNCH_MODES.tool) {
      this.runTool();
    }
    if (this.formStore.mode === LAUNCH_MODES.pipeline) {
      this.runPipeline();
    }
  };

  render () {
    if (this.formStore?.error) {
      return <Alert type="error" message={this.formStore.error} />;
    }
    if (this.formStore.pending) {
      return <Spin />;
    }

    return (
      <div className={classNames(styles.launchForm, 'cp-panel', this.props.className)}>
        <LaunchFormInfo formStore={this.formStore} />
        <Environment formStore={this.formStore} />
        <ParameterGroup formStore={this.formStore} />
        <div className={styles.controlBtn}>
          <Button
            type="primary"
            onClick={this.onLaunch}
            disabled={this.state.launchPending || this.formStore.runLaunched}>
            {this.state.launchPending ? (<LoadingOutlined />) : null}
            {this.formStore.runLaunched ? (
              'LAUNCHED'
            ) : (
              'LAUNCH'
            )}
          </Button>
        </div>
      </div>
    );
  }
}

LaunchForm.propTypes = {
  data: PropTypes.object,
  onRunSuccess: PropTypes.func,
  className: PropTypes.string
};
