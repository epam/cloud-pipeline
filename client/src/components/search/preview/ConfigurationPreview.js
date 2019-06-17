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
import {computed, observable} from 'mobx';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';
import AWSRegionTag from '../../special/AWSRegionTag';
import {getSpotTypeName} from '../../special/spot-instance-names';

const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';

@inject('cloudProviders')
@inject((stores, params) => {
  const {
    cloudProviders,
    configurations,
    dtsList,
    pipelines,
    preferences,
    runDefaultParameters
  } = stores;
  const [configId, entryName] = `${params.item.id}`.split('-');
  const configuration = configurations.getConfiguration(configId);

  configuration.fetch();
  runDefaultParameters.fetch();

  return {
    cloudProviders,
    configuration,
    entryName,
    dtsList,
    pipelines,
    preferences,
    runDefaultParameters
  };
})
@observer
export default class ConfigurationPreview extends React.Component {

  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @observable
  _pipeline = null;

  _systemParams = null;

  componentDidUpdate () {
    if (this.configurationEntry &&
      (!this.pipeline || this.pipeline.id !== this.configurationEntry.pipelineId)) {
      this._pipeline = this.props.pipelines.getPipeline(this.configurationEntry.pipelineId);
    }
    if (this.props.runDefaultParameters.loaded && this.props.runDefaultParameters.value.length &&
      !this._systemParams) {
      this._systemParams = this.props.runDefaultParameters.value.map(param => param.name);
    }
  }

  @computed
  get pipeline () {
    if (!this._pipeline || !this._pipeline.loaded) {
      return null;
    }

    return this._pipeline.value;
  }

  @computed
  get name () {
    if (!this.props.item) {
      return null;
    }
    return this.props.item.name;
  }

  @computed
  get description () {
    if (!this.props.item) {
      return null;
    }
    if (this.props.configuration && this.props.configuration.loaded &&
      this.props.configuration.value.description) {
      return this.props.configuration.value.description;
    }
    return this.props.item.description;
  }

  @computed
  get configurationEntry () {
    if (!this.props.configuration || !this.props.configuration.loaded || !this.props.entryName) {
      return null;
    }

    return this.props.configuration.value.entries
      .filter(e => e.name === this.props.entryName)[0];
  }

  @computed
  get isSpot() {
    if (this.configurationEntry && this.configurationEntry.configuration) {
      return this.configurationEntry.configuration.is_spot;
    }
    return null;
  }

  @computed
  get currentCloudProvider() {
    if (this.configurationEntry && this.configurationEntry.configuration && this.props.cloudProviders.loaded) {
      const [provider] = (this.props.cloudProviders.value || [])
        .filter(p => p.id === this.configurationEntry.configuration.cloudProviderId);
      return provider;
    }
    return null;
  }

  @computed
  get configurationEntryParameters () {
    if (!this.configurationEntry ||
      (!this.configurationEntry.configuration && !this.configurationEntry.parameters)) {
      return null;
    }

    return (
      this.configurationEntry.configuration && this.configurationEntry.configuration.parameters
      ) || this.configurationEntry.parameters;
  }

  @computed
  get dtsList () {
    if (this.props.dtsList.loaded) {
      return (this.props.dtsList.value || []).map(i => i);
    }
    return [];
  }

  @computed
  get isDtsEnvironment () {
    return this.configurationEntry &&
      this.configurationEntry.executionEnvironment === DTS_ENVIRONMENT;
  }

  @computed
  get isFireCloudEnvironment () {
    return this.configurationEntry &&
      this.configurationEntry.executionEnvironment === FIRE_CLOUD_ENVIRONMENT;
  }

  @computed
  get ExecEnvString () {
    if (!this.configurationEntry) {
      return null;
    }
    let environment;
    if (this.isDtsEnvironment) {
      const dts = this.dtsList.filter(dts => dts.id === this.configurationEntry.dtsId)[0];
      environment = dts ? `${dts.name}` : `${this.configurationEntry.dtsId}`;
    } else if (this.isFireCloudEnvironment) {
      environment = 'FireCloud';
    } else {
      environment = this.props.preferences.deploymentName || 'EPAM Cloud Pipeline';
    }

    return environment;
  };

  @computed
  get pipelineRow () {
    if (!this.configurationEntry) {
      return null;
    }

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding,
      fontWeight: 'bold'
    };

    let inputValue;
    if (this.pipeline) {
      inputValue = this.pipeline.name;
      if (this.configurationEntry.pipelineVersion && !this.pipeline.unknown) {
        inputValue = `${inputValue} (${this.configurationEntry.pipelineVersion})`;
      }
    } else if (this.isFireCloudEnvironment &&
      this.configurationEntry.methodName &&
      this.configurationEntry.methodSnapshot) {
      if (this.configurationEntry.methodConfigurationName) {
        inputValue = `${this.configurationEntry.methodName} (${this.configurationEntry.methodConfigurationName})`;
      } else {
        inputValue = `${this.configurationEntry.methodName}`;
      }
    }

    return inputValue
      ? (<tr>
        <td style={firstCellStyle}>
          {this.isFireCloudEnvironment ? 'FireCloud method' : 'Pipeline'}
        </td>
        <td>
          {inputValue}
        </td>
      </tr>)
      : null;
  }

  isSystemParameter = (parameterName) => {
    if (this.props.runDefaultParameters.pending) {
      return false;
    }
    return this._systemParams && this._systemParams.includes(parameterName);
  };

  renderExecEnvSection = () => {
    if (this.props.configuration.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (!this.configurationEntry) {
      return null;
    }

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding,
      fontWeight: 'bold'
    };

    const configuration = this.configurationEntry;

    const dockerImage = this.isDtsEnvironment
      ? configuration.docker_image
      : configuration.configuration && configuration.configuration.docker_image;
    const cloudRegion = !this.isDtsEnvironment && !this.isFireCloudEnvironment
      ? <AWSRegionTag
        regionId={configuration.configuration.cloudRegionId}
        displayName
        style={{marginLeft: -5, verticalAlign: 'top'}} />
      : null;

    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {this.pipelineRow}
            <tr>
              <td style={firstCellStyle}>
                Execution environment
              </td>
              <td>
                {this.ExecEnvString}
              </td>
            </tr>
            {
              this.isDtsEnvironment && configuration.coresNumber &&
              <tr>
                <td style={firstCellStyle}>
                  Cores
                </td>
                <td>
                  {configuration.coresNumber}
                </td>
              </tr>
            }
            {
              dockerImage &&
              <tr>
                <td style={firstCellStyle}>
                  Docker image
                </td>
                <td>
                  {dockerImage}
                </td>
              </tr>
            }
            {
              configuration.configuration && configuration.configuration.instance_size &&
              <tr>
                <td style={firstCellStyle}>
                  Instance type
                </td>
                <td>
                  {configuration.configuration.instance_size}
                </td>
              </tr>
            }
            {
              cloudRegion &&
              <tr>
                <td style={firstCellStyle}>
                  Cloud region
                </td>
                <td>
                  {cloudRegion}
                </td>
              </tr>
            }
            {
              configuration.configuration && configuration.configuration.instance_disk &&
              <tr>
                <td style={firstCellStyle}>
                  Disk size (Gb)
                </td>
                <td>
                  {configuration.configuration.instance_disk}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    );
  };

  renderAdvancedSection = () => {
    if (this.props.configuration.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (!this.configurationEntry) {
      return null;
    }
    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding,
      fontWeight: 'bold'
    };

    const configuration = this.configurationEntry;

    const cmdTemplate = this.isDtsEnvironment
      ? configuration.cmd_template : configuration.configuration.cmd_template;
    const timeout = !this.isDtsEnvironment && !this.isFireCloudEnvironment
      ? configuration.configuration.timeout : null;

    if ((this.isDtsEnvironment || this.isFireCloudEnvironment) &&
      timeout === null && !cmdTemplate) {
      return null;
    }

    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            { !this.isDtsEnvironment && !this.isFireCloudEnvironment &&
              <tr>
                <td style={firstCellStyle}>
                  Price type
                </td>
                <td>
                  {getSpotTypeName(this.isSpot, this.currentCloudProvider)}
                </td>
              </tr>
            }
            {
              timeout !== null &&
              <tr>
                <td style={firstCellStyle}>
                  Timeout (minutes)
                </td>
                <td>
                  {timeout}
                </td>
              </tr>
            }
            {
              cmdTemplate &&
              <tr>
                <td style={firstCellStyle}>
                  Cmd template
                </td>
                <td>
                  {cmdTemplate}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    );
  };

  renderParameters = (isSystemSection = false) => {
    if (this.props.configuration.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (!this.configurationEntryParameters) {
      return null;
    }

    const parameters = this.configurationEntryParameters;

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding,
      fontWeight: 'bold'
    };

    const items = [];
    for (let key in parameters) {
      if (parameters.hasOwnProperty(key)) {
        if ((isSystemSection && !this.isSystemParameter(key)) ||
          (!isSystemSection && this.isSystemParameter(key))) {
          continue;
        }
        items.push(
          <tr key={key}>
            <td style={firstCellStyle}>
              {key}
            </td>
            <td>
              {parameters[key].value}
            </td>
          </tr>
        );
      }
    }

    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {items}
          </tbody>
        </table>
      </div>
    );
  };

  renderFireCloudIOList = (listPropName = 'methodInputs') => {
    if (this.props.configuration.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (!this.configurationEntry) {
      return null;
    }

    const parameters = this.configurationEntry[listPropName];

    const padding = 20;
    const firstCellStyle = {
      paddingRight: padding,
      fontWeight: 'bold'
    };

    return parameters
      ? (<div className={styles.contentPreview}>
        <table>
          <tbody>
            {
              parameters.map(param =>
                (<tr key={param.name}>
                  <td style={firstCellStyle}>
                    {param.name}
                  </td>
                  <td>
                    {param.value}
                  </td>
                </tr>))
            }
          </tbody>
        </table>
      </div>)
      : null;
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);

    const execEnvSection = this.renderExecEnvSection();
    const advanced = this.renderAdvancedSection();
    const systemParameters = this.renderParameters(true);
    const parameters = this.renderParameters();
    const fireCloudInputs = this.renderFireCloudIOList();
    const fireCloudOutputs = this.renderFireCloudIOList('methodOutputs');

    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.name}</span>
          </Row>
          {
            this.description &&
            <Row className={styles.description}>
              {this.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {execEnvSection && renderSeparator()}
          {execEnvSection}
          {!this.isFireCloudEnvironment && advanced && renderSeparator()}
          {!this.isFireCloudEnvironment && advanced}
          {!this.isFireCloudEnvironment && systemParameters && renderSeparator()}
          {!this.isFireCloudEnvironment && systemParameters}
          {!this.isFireCloudEnvironment && parameters && renderSeparator()}
          {!this.isFireCloudEnvironment && parameters}
          {this.isFireCloudEnvironment && fireCloudInputs && renderSeparator()}
          {this.isFireCloudEnvironment && fireCloudInputs}
          {this.isFireCloudEnvironment && fireCloudOutputs && renderSeparator()}
          {this.isFireCloudEnvironment && fireCloudOutputs}
        </div>
      </div>
    );
  }
}
