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
import {autorun, computed} from 'mobx';
import {observer, inject} from 'mobx-react';
import {Alert, Icon, Button, Spin, message} from 'antd';
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
import SessionStorageWrapper from '../../../special/SessionStorageWrapper';
import LoadToolAttributes from '../../../../models/tools/LoadToolAttributes';

const DEFAULT_REGISTRY_ID = 1; // Default registry

@inject('dockerRegistries', 'awsRegions', 'router', 'authenticatedUserInfo', 'preferences')
@withCurrentUserAttributes()
@observer
export default class LaunchForm extends React.Component {
  constructor (props) {
    super(props);
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

  @computed
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return this.props.dockerRegistries.value.registries;
    }
    return [];
  }

  @computed
  get dockerRegistry () {
    if (this.registries.length > 0 && this.formStore.toolInfo) {
      return this.registries
        .find(r => r.id === this.formStore.toolInfo.registryId);
    }
    return null;
  }

  @computed
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

  initializeData = async () => {
    const {data} = this.props;
    if (!this.props.dockerRegistries.loaded || !data) {
      return;
    }
    const registry = this.registries.find(r => r.id === DEFAULT_REGISTRY_ID);
    let dockerImage = data.dockerImage || '';
    if (dockerImage.split('/').length === 1) {
      const defaultGroup = registry.groups[0]?.name;
      dockerImage = `${defaultGroup}/${dockerImage}`;
    }
    const {tool} = getDockerImage(
      `${registry.name}/${dockerImage}`,
      this.props.dockerRegistries
    ) || {};
    if (!tool) {
      this.formStore.error = `Tool ${data.dockerImage} not found!`;
      return;
    };
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
    };
    this.formStore.initializeData({
      data,
      toolInfo: toolInfo.value,
      toolVersion: toolVersions.value
        .find(({version}) => version === (toolInfo.value.version || 'latest')),
      versions: versions.value
    });
  };

  runTool = async (version) => {
    this.setState({launchPending: true}, async () => {
      const {currentUserAttributes} = this.props;
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
      const prepareParameters = (parameters) => {
        const result = {};
        if (parameters) {
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
        }
        return currentUserAttributes.extendLaunchParameters(
          result,
          this.formStore.toolInfo.allowSensitive
        );
      };
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
        spot: this.formStore.isSpot
      });
      await allowedInstanceTypesRequest.fetch();
      const platform = this.formStore.toolVersion.platform;
      const payload = modifyPayloadForAllowedInstanceTypes({
        instanceType: this.formStore.instanceType || chooseDefaultValue(
          versionSettingValue('instance_size'),
          this.formStore.toolInfo.instanceType,
          this.props.preferences.getPreferenceValue('cluster.instance.type')
        ),
        hddSize: +this.formStore.disk || +chooseDefaultValue(
          versionSettingValue('instance_disk'),
          this.formStore.toolInfo.disk,
          this.props.preferences.getPreferenceValue('cluster.instance.hdd'),
          p => +p > 0
        ),
        timeout: +(this.formStore.toolInfo.timeout || 0),
        cmdTemplate: this.formStore.cmd || chooseDefaultValue(
          versionSettingValue('cmd_template'),
          this.formStore.toolInfo.cmd,
          this.props.preferences.getPreferenceValue('launch.cmd.template')
        ),
        dockerImage: registry
          ? `${registry.path}/${this.formStore.toolInfo.image}${version ? `:${version}` : ''}`
          : `${this.formStore.toolInfo.image}${version ? `:${version}` : ''}`,
        params: prepareParameters(this.formStore.parameters),
        isSpot: this.formStore.isSpot,
        nodeCount: parameterIsNotEmpty(versionSettingValue('node_count'))
          ? +versionSettingValue('node_count')
          : undefined,
        cloudRegionId: cloudRegionIdValue
      }, allowedInstanceTypesRequest);
      const runResolved = await run(this)(
        payload,
        true,
        titleFn,
        info.launchTooltip,
        allowedInstanceTypesRequest,
        undefined,
        platform
      );
      hide();
      this.setState({launchPending: undefined});
      if (runResolved) {
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      }
    });
  };

  onLaunch = () => {
    this.runTool(this.formStore.toolVersion?.version);
  };

  render () {
    if (this.formStore?.error) {
      return <Alert type="error" message={this.formStore.error} />;
    }
    if (this.formStore.pending) {
      return <Spin />;
    }

    return (
      <div className={classNames(styles.launchForm, 'cp-panel')}>
        <LaunchFormInfo
          formStore={this.formStore}
        />
        <Environment formStore={this.formStore} />
        <ParameterGroup formStore={this.formStore} />
        <div className={styles.controlBtn}>
          <Button
            type="primary"
            onClick={this.onLaunch}
            disabled={this.state.launchPending}
          >
            {this.state.launchPending ? (<Icon type="loading" />) : null}
            LAUNCH
          </Button>
        </div>
      </div>
    );
  }
}

LaunchForm.propTypes = {
  data: PropTypes.shape({
    dockerImage: PropTypes.string,
    disk: PropTypes.string,
    cmd: PropTypes.string,
    instanceType: PropTypes.string,
    is_spot: PropTypes.bool,
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string,
        value: PropTypes.any
      })
    )
  })
};
