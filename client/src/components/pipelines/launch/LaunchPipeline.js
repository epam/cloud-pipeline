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
import {observable} from 'mobx';
import {Alert, Card} from 'antd';
import connect from '../../../utils/connect';
import localization from '../../../utils/localization';
import pipelines from '../../../models/pipelines/Pipelines';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import preferences from '../../../models/preferences/PreferencesLoad';
import LoadTool from '../../../models/tools/LoadTool';
import AllowedInstanceTypes from '../../../models/utils/AllowedInstanceTypes';
import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';
import PipelineConfigurations from '../../../models/pipelines/PipelineConfigurations';
import {submitsRun, run, runPipelineActions} from '../../runs/actions';
import styles from './LaunchPipeline.css';
import LoadingView from '../../special/LoadingView';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import queryParameters from '../../../utils/queryParameters';
import LaunchPipelineForm from './form/LaunchPipelineForm';

@connect({
  pipelines, preferences
})
@localization.localizedComponent
@submitsRun
@runPipelineActions
@inject(({allowedInstanceTypes, routing, pipelines, preferences}, {params}) => {
  const components = queryParameters(routing);
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
    configurations: params.id && params.version
      ? new PipelineConfigurations(params.id, params.version) : undefined
  };
})
@observer
class LaunchPipeline extends localization.LocalizedReactComponent {
  state = {launching: false, configName: null};

  @observable allowedInstanceTypes;

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
          : undefined
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

  launch = async (payload) => {
    payload.configurationName = this.currentConfiguration
      ? this.currentConfiguration.name
      : this.configurationName;
    if (await run(this)(payload)) {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    }
  };

  onConfigurationChanged = (name) => {
    this.setState({configName: name});
  };

  componentDidUpdate () {
    const parameters = this.getParameters();
    if (!this.allowedInstanceTypes) {
      this.allowedInstanceTypes = this.props.image
        ? this.props.allowedInstanceTypes.getAllowedTypes(this.props.image)
        : new AllowedInstanceTypes();
    }
    if (parameters) {
      this.allowedInstanceTypes.setParameters({
        isSpot: parameters.is_spot,
        regionId: parameters.cloudRegionId
      });
    }
  }

  render () {
    if (this.pipelinePending ||
      this.configurationsPending ||
      this.runPending ||
      this.toolPending ||
      this.toolSettingsPending ||
      (!this.props.preferences.loaded && this.props.preferences.pending) ||
      !this.allowedInstanceTypes) {
      return <LoadingView />;
    }
    if (this.props.pipeline && this.props.pipeline.error) {
      return <Alert type="warning" message={this.props.pipeline.error} />;
    }
    const errors = [];
    if (this.props.configurations && this.props.configurations.error) {
      errors.push(this.props.configurations.error);
    }
    if (this.props.run && this.props.run.error) {
      errors.push(this.props.run.error);
    }
    if (this.props.tool && this.props.tool.error) {
      errors.push(this.props.tool.error);
    }
    if (this.props.toolSettings && this.props.toolSettings.error) {
      errors.push(this.props.toolSettings.error);
    }
    const parameters = this.getParameters();
    return (
      <Card
        bodyStyle={{padding: 0, margin: 0}}
        className={styles.container}>
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
          errors={errors}
          onConfigurationChanged={this.onConfigurationChanged}
          onLaunch={this.launch}
          isDetachedConfiguration={false} />
      </Card>
    );
  }
}

export default LaunchPipeline;
