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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import PropTypes from 'prop-types';
import {Alert, Collapse, Icon, Row} from 'antd';
import LoadingView from '../../special/LoadingView';
import connect from '../../../utils/connect';
import configurations from '../../../models/configuration/Configurations';
import pipelines from '../../../models/pipelines/Pipelines';
import MetadataClassLoadAll from '../../../models/folderMetadata/MetadataClassLoadAll';
import styles from './PreviewConfiguration.css';

const EXEC_ENVIRONMENT = 'exec';
const ADVANCED = 'advanced';
const PARAMETERS = 'parameters';
const SYSTEM_PARAMETERS = 'systemParameters';

@connect({configurations, pipelines})
@inject(({configurations, runDefaultParameters, pipelines, onDemandInstanceTypes, spotInstanceTypes}, params) => {
  return {
    configuration: configurations.getConfiguration(params.configurationId),
    configurationsCache: configurations,
    entitiesTypes: new MetadataClassLoadAll(),
    runDefaultParameters,
    pipelines,
    onDemandInstanceTypes,
    spotInstanceTypes
  };
})
@observer
export default class PreviewConfiguration extends Component {
  static propTypes = {
    configurationId: PropTypes.number
  };

  state = {
    openedPanels: [PARAMETERS]
  };
  @observable
  selectedEntry = null;
  @observable
  selectedPipeline = null;
  @observable
  selectedPipelineConfiguration = null;
  @observable
  selectedRootEntity = null;

  getPanelHeader = (key) => {
    let title;
    let icon;
    switch (key) {
      case EXEC_ENVIRONMENT: title = 'Exec environment'; icon = 'code-o'; break;
      case ADVANCED: title = 'Advanced'; icon = 'setting'; break;
      case SYSTEM_PARAMETERS: title = 'System parameters'; icon = 'bars'; break;
      case PARAMETERS: title = 'Parameters'; icon = 'bars'; break;
    }
    return (
      <Row className={styles.panelHeader} type="flex" justify="space-between" align="middle">
        <span className={styles.itemHeader}>
          <Icon type={icon} /> {title}
        </span>
      </Row>
    );
  };

  renderExecEnvironmentSection = () => {
    const res = [];
    if (!this.selectedEntry ||
      this.props.onDemandInstanceTypes.pending || this.props.spotInstanceTypes.pending ||
      (this.selectedEntry.pipelineId && this.selectedPipeline && this.selectedPipeline.pending)) {
      return res;
    }
    let pipeValue = '';
    if (this.selectedEntry.pipelineId && this.selectedPipeline.value) {
      pipeValue = this.selectedPipeline.value.name;
      if (this.selectedEntry.pipelineVersion) {
        pipeValue = `${pipeValue} (${this.selectedEntry.pipelineVersion})`;
      }
    }
    res.push(
      <tr key={'pipeline_key'} className={styles.keyRow}>
        <td
          id={'key-column-pipeline'}
          colSpan={6}
          className={styles.keyCell}>
          Pipeline
        </td>
      </tr>,
      <tr key={'pipeline_value'} className={styles.valueRow}>
        <td id={'value-column-pipeline'} colSpan={6}>
          {pipeValue}
        </td>
      </tr>,
      this.getDivider('pipeline_divider', 6)
    );
    res.push(
      <tr key={'docker_image_key'} className={styles.keyRow}>
        <td
          id={'key-column-docker_image'}
          colSpan={6}
          className={styles.keyCell}>
          Docker image
        </td>
      </tr>,
      <tr key={'docker_image_value'} className={styles.valueRow}>
        <td id={'value-column-docker_image'} colSpan={6}>
          {this.selectedEntry.configuration.docker_image}
        </td>
      </tr>,
      this.getDivider('docker_image_divider', 6)
    );
    let instance = this.selectedEntry.configuration.instance_size;
    const [instanceType] = (this.selectedEntry.configuration.is_spot
      ? this.props.spotInstanceTypes.value
      : this.props.onDemandInstanceTypes.value).filter(i => i.name === instance);
    if (instanceType) {
      instance = `${instanceType.name} (CPU: ${instanceType.vcpu}, RAM: ${instanceType.memory}`;
      if (instanceType.gpu > 0) {
        instance = `${instance}, GPU: ${instanceType.gpu})`;
      } else {
        instance = `${instance})`;
      }
    }
    res.push(
      <tr key={'instance_size_key'} className={styles.keyRow}>
        <td
          id={'key-column-instance_size'}
          colSpan={6}
          className={styles.keyCell}>
          Instance
        </td>
      </tr>,
      <tr key={'instance_size_value'} className={styles.valueRow}>
        <td id={'value-column-instance_size'} colSpan={6}>
          {instance}
        </td>
      </tr>,
      this.getDivider('instance_size_divider', 6)
    );
    res.push(
      <tr key={'instance_disk_key'} className={styles.keyRow}>
        <td
          id={'key-column-instance_disk'}
          colSpan={6}
          className={styles.keyCell}>
          Disk
        </td>
      </tr>,
      <tr key={'instance_disk_value'} className={styles.valueRow}>
        <td id={'value-column-instance_disk'} colSpan={6}>
          {`${this.selectedEntry.configuration.instance_disk} Gb`}
        </td>
      </tr>,
      this.getDivider('instance_disk_divider', 6)
    );
    if (this.selectedEntry.configuration.node_count) {
      res.push(
        <tr key={'node_count_key'} className={styles.keyRow}>
          <td
            id={'key-column-node_count'}
            colSpan={6}
            className={styles.keyCell}>
            Run type
          </td>
        </tr>,
        <tr key={'node_count_value'} className={styles.valueRow}>
          <td id={'value-column-node_count'} colSpan={6}>
            {`cluster (${this.selectedEntry.configuration.node_count} nodes)`}
          </td>
        </tr>,
        this.getDivider('node_count_divider', 6)
      );
    }

    return res;
  };

  renderAdvancedSection = () => {
    const res = [];
    if (!this.selectedEntry) {
      return res;
    }

    res.push(
      <tr key={'is_spot_key'} className={styles.keyRow}>
        <td
          id={'key-column-is_spot'}
          colSpan={6}
          className={styles.keyCell}>
          Price type
        </td>
      </tr>,
      <tr key={'is_spot_value'} className={styles.valueRow}>
        <td id={'value-column-is_spot'} colSpan={6}>
          {this.selectedEntry.configuration.is_spot ? 'Spot' : 'On-demand'}
        </td>
      </tr>,
      this.getDivider('is_spot_divider', 6)
    );
    res.push(
      <tr key={'timeout_key'} className={styles.keyRow}>
        <td
          id={'key-column-timeout'}
          colSpan={6}
          className={styles.keyCell}>
          Timeout
        </td>
      </tr>,
      <tr key={'timeout_value'} className={styles.valueRow}>
        <td id={'value-column-timeout'} colSpan={6}>
          {`${this.selectedEntry.configuration.timeout} min`}
        </td>
      </tr>,
      this.getDivider('timeout_divider', 6)
    );
    res.push(
      <tr key={'cmd_template_key'} className={styles.keyRow}>
        <td
          id={'key-column-cmd_template'}
          colSpan={6}
          className={styles.keyCell}>
          Cmd template
        </td>
      </tr>,
      <tr key={'cmd_template_value'} className={styles.valueRow}>
        <td id={'value-column-cmd_template'} colSpan={6}>
          {`"${this.selectedEntry.configuration.cmd_template}"`}
        </td>
      </tr>,
      this.getDivider('cmd_template_divider', 6)
    );

    return res;
  };

  isSystemParameter = (parameter) => {
    if (this.props.runDefaultParameters.loaded) {
      return (this.props.runDefaultParameters.value || [])
        .filter(p => p.name === parameter.name).length > 0;
    }
    return false;
  };

  buildDefaultParameters = (system = false) => {
    const parameters = {
      keys: [],
      params: {}}
    ;
    let keyIndex = 0;
    if (this.selectedEntry.configuration.parameters) {
      for (let key in this.selectedEntry.configuration.parameters) {
        if (this.selectedEntry.configuration.parameters.hasOwnProperty(key)) {
          if (this.isSystemParameter({name: key}) !== system) {
            continue;
          }
          parameters.keys.push(`param_${keyIndex}`);
          let value;
          let type = 'string';
          let required = false;
          let readOnly = false;
          const parameter = this.selectedEntry.configuration.parameters[key];
          if (parameter.value !== undefined ||
            parameter.type !== undefined ||
            parameter.required !== undefined) {
            let prevValue;
            if (this.prevParameters && this.prevParameters.params) {
              for (let paramKey in this.prevParameters.params) {
                if (this.prevParameters.params[paramKey].name === key) {
                  prevValue = this.prevParameters.params[paramKey].value;
                }
              }
            }
            value = prevValue && !parameter.value ? prevValue : parameter.value;
            type = parameter.type || 'string';
            required = parameter.required;
            readOnly = parameter.readOnly || false;
          } else {
            value = parameter;
          }

          parameters.params[`param_${keyIndex}`] = {
            name: key,
            key: `param_${keyIndex}`,
            type: type,
            value: value,
            required: required,
            readOnly: readOnly,
            system: system
          };
          keyIndex += 1;
        }
      }
    }
    return parameters;
  };

  renderParameters = (isSystemParametersSection) => {
    const res = [];
    if (!this.props.runDefaultParameters.loaded && this.props.runDefaultParameters.pending) {
      return res;
    }
    const parameters = this.buildDefaultParameters(isSystemParametersSection);

    (parameters.keys || []).forEach((key) => {
      const parameter = parameters.params[key];
      res.push(
        <tr key={`${parameter.key}_key`} className={styles.keyRow}>
          <td
            id={`key-column-${parameter.key}`}
            colSpan={6}
            className={styles.keyCell}>
            {`${parameter.name}`}
          </td>
        </tr>,
        <tr key={`${parameter.key}_value`} className={styles.valueRow}>
          <td id={`value-column-${parameter.key}`} colSpan={6}>
            {parameter.value || ''}
          </td>
        </tr>,
        this.getDivider(`${parameter.key}_divider`, 6)
      );
    });

    return res;
  };

  renderConfigurationEntryItem = () => {
    const systemParameters = this.renderParameters(true);
    return (
      <Collapse
        bordered={false}
        onChange={(tabs) => this.setState({openedPanels: tabs})}
        activeKey={this.state.openedPanels}>
        <Collapse.Panel
          id="configuration-preview-exec-environment-panel"
          key={EXEC_ENVIRONMENT}
          className={styles.section}
          header={this.getPanelHeader(EXEC_ENVIRONMENT)}>
          <table key="body" className={styles.sectionTableBody}>
            <tbody>
              {this.renderExecEnvironmentSection()}
            </tbody>
          </table>
        </Collapse.Panel>
        <Collapse.Panel
          id="configuration-preview-advanced-panel"
          key={ADVANCED}
          className={styles.section}
          header={this.getPanelHeader(ADVANCED)}>
          <table key="body" className={styles.sectionTableBody}>
            <tbody>
              {this.renderAdvancedSection()}
            </tbody>
          </table>
        </Collapse.Panel>
        {
          systemParameters.length &&
          <Collapse.Panel
            id="configuration-preview-system-parameters-panel"
            key={SYSTEM_PARAMETERS}
            className={styles.section}
            header={this.getPanelHeader(SYSTEM_PARAMETERS)}>
            <table key="body" className={styles.sectionTableBody}>
              <tbody>
                {systemParameters}
              </tbody>
            </table>
          </Collapse.Panel>
        }
        <Collapse.Panel
          id="configuration-preview-parameters-panel"
          key={PARAMETERS}
          className={styles.section}
          header={this.getPanelHeader(PARAMETERS)}>
          <table key="body" className={styles.sectionTableBody}>
            <tbody>
              {[
                ...this.renderRootEntity(),
                ...this.renderParameters(false)
              ]}
            </tbody>
          </table>
        </Collapse.Panel>
      </Collapse>
    );
  };

  renderRootEntity = () => {
    if (!this.selectedRootEntity) {
      return [];
    }
    return [
      <tr key={'root_entity_key'} className={styles.keyRow}>
        <td
          id={'key-column-root_entity'}
          colSpan={6}
          className={styles.keyCell}>
          Root entity
        </td>
      </tr>,
      <tr key={'root_entity_value'} className={styles.valueRow}>
        <td id={'value-column-root_entity'} colSpan={6}>
          {this.selectedRootEntity.name}
        </td>
      </tr>,
      this.getDivider('root_entity_divider', 6)
    ];
  };

  renderEmptyPlaceholder = () => {
    return (
      <table key="body" className={styles.sectionTableBody}>
        <tbody>
          <tr style={{height: 40, color: '#777'}}>
            <td colSpan={3} style={{textAlign: 'center'}}>
              No configuration entry presented
            </td>
          </tr>
        </tbody>
      </table>
    );
  };

  renderConfigurationPreview = () => {
    if (this.props.configuration.pending) {
      return;
    }
    const header = this.selectedEntry
      ? <table key="header" style={{width: '100%'}}>
        <thead className={styles.previewHeader}>
          <tr style={{}}>
            <td colSpan={3}>
              <Row type="flex" justify="space-between" align="middle">
                <div>
                  Name:
                </div>
                <div>
                  <b key="entry name">{this.selectedEntry.name}</b>
                </div>
              </Row>
            </td>
          </tr>
        </thead>
      </table>
      : undefined;
    return [
      header,
      <div key="body" style={{width: '100%', overflowY: 'auto'}}>
        {
          this.selectedEntry
            ? this.renderConfigurationEntryItem()
            : this.renderEmptyPlaceholder()
        }
      </div>
    ];
  };

  getDivider = (key, span) => {
    return (
      <tr key={key} className={styles.divider}>
        <td colSpan={span || 3}><div /></td>
      </tr>
    );
  };
  initialize = () => {
    if (!this.props.configuration.pending && this.props.configuration.loaded) {
      [this.selectedEntry] = this.props.configuration.value.entries.filter(e => e.default);
      if (!this.props.entitiesTypes.pending &&
        this.selectedEntry && this.selectedEntry.rootEntityId) {
        [this.selectedRootEntity] = this.props.entitiesTypes.value
          .filter(e => e.id === this.selectedEntry.rootEntityId);
      }
      if (this.selectedEntry && this.selectedEntry.pipelineId) {
        this.selectedPipeline = this.props.pipelines.getPipeline(this.selectedEntry.pipelineId);
        if (this.selectedEntry.pipelineVersion) {
          this.selectedPipelineConfiguration = this.props.pipelines.getConfiguration(
            this.selectedEntry.pipelineId,
            this.selectedEntry.pipelineVersion
          );
        }
      }
    }
  };

  render () {
    if (this.props.configuration.pending ||
      this.props.onDemandInstanceTypes.pending || this.props.spotInstanceTypes.pending ||
      this.props.entitiesTypes.pending ||
      (this.selectedPipeline && this.selectedPipeline.pending)) {
      return <LoadingView />;
    }
    if (this.props.configuration.error || this.props.onDemandInstanceTypes.error || this.props.spotInstanceTypes.error ||
      this.props.entitiesTypes.error ||
      (this.selectedPipeline && this.selectedPipeline.error)) {
      const errors = [
        this.props.configuration.error || false,
        this.props.onDemandInstanceTypes.error || false,
        this.props.spotInstanceTypes.error || false,
        this.props.entitiesTypes.error || false,
        this.selectedPipeline.error || false
      ];
      return (
        <Row type="flex" style={{padding: 7, flex: 1, display: 'flex', flexDirection: 'column'}}>
          <Alert
            type="error"
            message={
              <ul style={{listStyle: 'disc'}}>
                {
                  errors.filter(e => !!e).map(
                    (error, index) => <li key={`error_${index}`}>{error}</li>)
                }
              </ul>
            } />
        </Row>
      );
    }
    return (
      <Row type="flex" style={{padding: 7, flex: 1, display: 'flex', flexDirection: 'column'}}>
        {this.renderConfigurationPreview()}
      </Row>
    );
  }

  componentDidUpdate () {
    this.initialize();
  }
}

