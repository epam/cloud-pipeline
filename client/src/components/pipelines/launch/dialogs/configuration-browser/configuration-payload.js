/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  Checkbox,
  Collapse,
  InputNumber,
  Select
} from 'antd';
import ConfigurationLoad from '../../../../../models/configuration/ConfigurationLoad';
import AllowedInstanceTypes from '../../../../../models/utils/AllowedInstanceTypes';
import {names} from '../../../../../models/utils/ContextualPreference';
import MetadataLoad from '../../../../../models/metadata/MetadataLoad';
import {getSpotTypeName} from '../../../../special/spot-instance-names';
import CodeEditor from '../../../../special/CodeEditor';
import {getProjectEntityTypes} from './utilities/project-utilities';
import Parameters from './parameters';
import {ParameterName, ParameterRow, ParameterValue} from './parameters/parameter';
import ParametersRunCapabilities from './parameters/capabilities';
import styles from './configuration-browser.css';
import ParametersLimitMounts from './parameters/limit-mounts';
import JobNotifications from '../job-notifications';
import AWSRegionTag from '../../../../special/AWSRegionTag';
import {mapObservableNotification} from '../job-notifications/job-notification';
import instanceInfoString from '../../../../../utils/instanceInfoString';

function getConfigurationPayload (entry) {
  if (!entry) {
    return undefined;
  }
  const newPayload = {...((entry || {}).configuration || {})};
  newPayload.startIdle = /^sleep infinity$/.test(newPayload.cmd_template);
  return newPayload;
}

function validateDisk (instanceDisk) {
  if (instanceDisk === undefined) {
    throw new Error('Disk size is required');
  }
  if (
    Number.isNaN(Number(instanceDisk)) ||
    Number(instanceDisk) < 15 ||
    Number(instanceDisk) > 15360
  ) {
    throw new Error('Disk size should be a positive number (15...15360)');
  }
  return true;
}

function validateTimeout (timeout) {
  if (timeout === undefined) {
    throw new Error('Timeout is required');
  }
  if (
    Number.isNaN(Number(timeout)) ||
    Number(timeout) < 0
  ) {
    throw new Error('Timeout should be a positive number');
  }
  return true;
}

class ConfigurationPayload extends React.Component {
  state = {
    pending: false,
    error: undefined,
    name: undefined,
    configuration: undefined,
    entries: [],
    metadataPending: false,
    metadataError: undefined,
    instanceTypesPending: false,
    instanceTypesError: undefined,
    instanceTypes: [],
    priceTypes: [],
    tool: undefined,
    classes: [],
    projectMetadata: {},
    payload: {},
    parametersValid: true,
    rootEntityId: undefined
  };

  get pending () {
    const {
      pending,
      metadataPending
    } = this.state;
    return pending || metadataPending;
  }

  get error () {
    const {
      error,
      metadataError
    } = this.state;
    return error || metadataError;
  }

  get entry () {
    const {
      entryName
    } = this.props;
    const {
      entries = []
    } = this.state;
    return entries.find(o => o.name === entryName);
  }

  get pipelineSpecified () {
    const entry = this.entry;
    if (!entry) {
      return false;
    }
    const {
      pipelineId
    } = entry;
    return !!pipelineId;
  }

  @computed
  get cloudRegions () {
    const {cloudRegionsInfo} = this.props;
    if (cloudRegionsInfo.loaded) {
      return (cloudRegionsInfo.value || []).slice();
    }
    return [];
  }

  get currentCloudRegion () {
    const {
      payload = {}
    } = this.state;
    const {
      cloudRegionId
    } = payload;
    return this.cloudRegions.find(o => o.id === Number(cloudRegionId));
  }

  get currentProvider () {
    const region = this.currentCloudRegion;
    return region ? region.provider : undefined;
  }

  get currentEntityType () {
    const {
      classes = [],
      rootEntityId
    } = this.state;
    return classes
      .find(o => o.id === Number(rootEntityId));
  }

  get currentEntityTypeFields () {
    const currentEntityType = this.currentEntityType;
    if (!currentEntityType) {
      return [];
    }
    return currentEntityType.fields;
  }

  componentDidMount () {
    this.loadConfiguration();
    this.loadMetadata();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.configurationId !== this.props.configurationId) {
      this.loadConfiguration();
    } else if (prevProps.entryName !== this.props.entryName) {
      this.loadConfigurationEntry();
    }
    if (prevProps.folderId !== this.props.folderId) {
      this.loadMetadata();
    }
  }

  loadConfiguration = () => {
    const {
      configurationId
    } = this.props;
    if (configurationId) {
      this.setState({
        pending: true,
        error: undefined,
        name: undefined,
        payload: undefined,
        parametersValid: true,
        rootEntityId: undefined,
        configuration: undefined,
        entries: []
      }, async () => {
        const newState = {
          pending: false
        };
        try {
          const request = new ConfigurationLoad(configurationId);
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          }
          const name = (request.value || {}).name;
          const entries = ((request.value || {}).entries || [])
            .filter(entry => !!entry.rootEntityId);
          newState.name = name;
          newState.entries = entries;
          newState.configuration = request.value;
        } catch (e) {
          newState.error = e.message;
        } finally {
          this.setState(newState, this.loadConfigurationEntry);
        }
      });
    } else {
      this.setState({
        pending: false,
        error: undefined,
        configuration: undefined,
        entries: [],
        payload: undefined,
        parametersValid: true,
        rootEntityId: undefined
      }, this.loadConfigurationEntry);
    }
  };

  loadConfigurationEntry = () => {
    const {
      entryName
    } = this.props;
    const {
      entries = []
    } = this.state;
    if (entryName && entries.length > 0) {
      const newState = {};
      try {
        const selected = entries.find(o => o.name === entryName);
        if (!selected) {
          throw new Error('Configuration is empty');
        }
        newState.name = name;
        newState.rootEntityId = selected.rootEntityId;
        newState.payload = getConfigurationPayload(selected);
      } catch (e) {
        newState.error = e.message;
      } finally {
        this.setState(newState, this.onPayloadChanged);
      }
    } else {
      this.setState({
        pending: false,
        error: undefined,
        payload: undefined,
        parametersValid: true,
        rootEntityId: undefined
      });
    }
  };

  loadMetadata = () => {
    const {
      folderId
    } = this.props;
    if (folderId) {
      this.setState({
        metadataPending: true,
        metadataError: undefined,
        classes: [],
        projectMetadata: {}
      }, async () => {
        const newState = {
          metadataPending: false
        };
        try {
          const metadataRequest = new MetadataLoad(folderId, 'FOLDER');
          await metadataRequest.fetch();
          if (metadataRequest.error) {
            throw new Error(metadataRequest.error);
          }
          const folderMetadata = (metadataRequest.value || [])
            .map(o => o.data)[0];
          newState.classes = await getProjectEntityTypes(folderId);
          newState.projectMetadata = {...(folderMetadata || {})};
        } catch (e) {
          newState.metadataError = e.message;
        } finally {
          this.setState(newState);
        }
      });
    } else {
      this.setState({
        metadataPending: true,
        metadataError: undefined,
        classes: [],
        projectMetadata: {}
      });
    }
  };

  onPayloadChanged = () => {
    this.reportChanged();
    const {
      payload
    } = this.state;
    if (!payload) {
      return;
    }
    const {
      dockerRegistries
    } = this.props;
    const {
      docker_image: dockerImage,
      is_spot: isSpot,
      cloudRegionId
    } = payload;
    this.setState({
      instanceTypesPending: true,
      instanceTypesError: undefined
    }, async () => {
      const newState = {
        instanceTypesPending: false
      };
      try {
        let tool;
        if (dockerImage) {
          await dockerRegistries.fetchIfNeededOrWait();
          if (dockerRegistries.error) {
            throw new Error(dockerRegistries.error);
          }
          const [
            registryPath,
            groupName,
            imageWithVersion = ''
          ] = (dockerImage || '').split('/');
          const imageNameRegExp = new RegExp(
            `^${groupName}/${imageWithVersion.split(':')[0]}$`,
            'i'
          );
          const registry = ((dockerRegistries.value || {}).registries || [])
            .find(o => o.path === registryPath);
          const group = ((registry || {}).groups || [])
            .find(o => o.name === groupName);
          tool = ((group || {}).tools || [])
            .find(o => imageNameRegExp.test(o.image));
          if (!tool) {
            throw new Error(`Tool ${dockerImage} not found`);
          }
        }
        const instanceTypesRequest = new AllowedInstanceTypes(
          tool ? tool.id : undefined,
          cloudRegionId,
          isSpot
        );
        await instanceTypesRequest.fetch();
        if (instanceTypesRequest.error) {
          throw new Error(instanceTypesRequest.error);
        }
        const {
          [names.allowedInstanceTypes]: instanceTypes = [],
          [names.allowedPriceTypes]: priceTypes = []
        } = instanceTypesRequest.value || {};
        newState.instanceTypes = instanceTypes;
        newState.priceTypes = priceTypes;
        newState.tool = tool;
      } catch (e) {
        newState.instanceTypesError = e.message;
      } finally {
        this.setState(newState);
      }
    });
  };

  reportChanged = () => {
    const {
      onChange
    } = this.props;
    const entry = this.entry || {};
    const {
      payload,
      parametersValid,
      rootEntityId,
      configuration = {}
    } = this.state;
    const {
      parameters,
      modifiedParameters,
      instance_disk: instanceDisk,
      timeout,
      instance_size: instanceType,
      startIdle,
      ...rest
    } = payload;
    let valid;
    try {
      valid = !!instanceType &&
        parametersValid &&
        validateDisk(instanceDisk) &&
        validateTimeout(timeout);
    } catch (_) {
      valid = false;
    }
    const result = {
      ...configuration,
      entries: [{
        ...entry,
        configuration: {
          ...(entry.configuration || {}),
          ...rest,
          instance_disk: instanceDisk,
          instance_size: instanceType,
          timeout,
          parameters: modifiedParameters || parameters
        },
        rootEntityId
      }]
    };
    if (typeof onChange === 'function') {
      onChange(result, valid);
    }
  };

  renderPipeline = () => {
    const entry = this.entry;
    if (!entry) {
      return null;
    }
    const {
      pipelines
    } = this.props;
    const {
      pipelineId,
      pipelineVersion
    } = entry;
    let pipelineName = pipelineId ? `#${pipelineId} (${pipelineVersion})` : undefined;
    if (pipelineId && pipelines && pipelines.loaded) {
      const pipeline = (pipelines.value || [])
        .find(o => o.id === Number(pipelineId));
      if (!pipeline) {
        pipelineName = `unknown pipeline (#${pipelineId})`;
      } else {
        pipelineName = `${pipeline.name} (${pipelineVersion}`;
      }
    }
    return (
      <div
        className={styles.row}
      >
        <span
          className={styles.title}
        >
          Pipeline:
        </span>
        {
          !pipelineId && (
            <span
              className="cp-text-not-important"
            >
              Pipeline is not specified
            </span>
          )
        }
        {
          pipelineId && (
            <span>
              {pipelineName}
            </span>
          )
        }
      </div>
    );
  };

  renderDockerImage = () => {
    const {
      payload
    } = this.state;
    if (!payload) {
      return null;
    }
    const {
      docker_image: dockerImage
    } = payload;
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Docker image:
        </span>
        {
          !dockerImage && (
            <span className="cp-text-not-important">
              Docker image is not specified
            </span>
          )
        }
        {
          dockerImage && (
            <span>
              {dockerImage}
            </span>
          )
        }
      </div>
    );
  };

  renderInstanceType = () => {
    const {
      payload,
      instanceTypes = [],
      instanceTypesPending,
      instanceTypesError
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    if (instanceTypesError) {
      return (
        <Alert
          message={instanceTypesError}
          type="error"
        />
      );
    }
    const {
      instance_size: instanceType
    } = payload;
    const onChange = newInstanceType => this.setState({
      payload: {
        ...payload,
        instance_size: newInstanceType
      }
    }, this.reportChanged);
    const families = [...new Set(instanceTypes.map(o => o.instanceFamily))];
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Instance type:
        </span>
        <Select
          className={
            classNames({
              'cp-error': !instanceType
            })
          }
          style={{flex: 1, maxWidth: 300}}
          value={instanceType}
          dropdownMatchSelectWidth={false}
          disabled={disabled || (instanceTypesPending && instanceTypes.length === 0)}
          onChange={onChange}
        >
          {
            families.map(family => (
              <Select.OptGroup
                key={family || 'Other'}
                label={family || 'Other'}
              >
                {
                  instanceTypes
                    .filter(o => o.instanceFamily === family)
                    .map(anInstanceType => (
                      <Select.Option
                        key={anInstanceType.name}
                        value={anInstanceType.name}
                        title={instanceInfoString(anInstanceType)}
                      >
                        {instanceInfoString(anInstanceType)}
                      </Select.Option>
                    ))
                }
              </Select.OptGroup>
            ))
          }
        </Select>
      </div>
    );
  };

  renderCloudRegion = () => {
    const {
      payload
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    const {
      cloudRegionId
    } = payload;
    const onChange = newCloudRegionId => this.setState({
      payload: {
        ...payload,
        cloudRegionId: Number(newCloudRegionId)
      }
    }, this.onPayloadChanged);
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Cloud region:
        </span>
        <Select
          className={
            classNames({
              'cp-error': !cloudRegionId
            })
          }
          style={{flex: 1, maxWidth: 300}}
          value={cloudRegionId ? `${cloudRegionId}` : undefined}
          dropdownMatchSelectWidth={false}
          disabled={disabled}
          onChange={onChange}
        >
          {
            this.cloudRegions
              .map(region => {
                return (
                  <Select.Option
                    key={`${region.id}`}
                    name={region.name}
                    title={region.name}
                    value={`${region.id}`}
                  >
                    <AWSRegionTag
                      provider={region.provider}
                      regionUID={region.regionId}
                      style={{fontSize: 'larger'}}
                    />
                    {region.name}
                  </Select.Option>
                );
              })
          }
        </Select>
      </div>
    );
  };

  renderDisk = () => {
    const {
      payload
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    const {
      instance_disk: instanceDisk
    } = payload;
    const onChange = disk => this.setState({
      payload: {
        ...payload,
        instance_disk: disk
      }
    }, this.reportChanged);
    let valid = true;
    try {
      validateDisk(instanceDisk);
    } catch (e) {
      valid = false;
    }
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Disk (Gb):
        </span>
        <InputNumber
          disabled={disabled}
          onChange={onChange}
          style={{flex: 1, maxWidth: 300}}
          value={instanceDisk}
          className={
            classNames(
              {
                'cp-error': !valid
              }
            )
          }
        />
      </div>
    );
  };

  renderPriceType = () => {
    const {
      payload,
      priceTypes = [],
      instanceTypesPending
    } = this.state;
    if (!payload) {
      return null;
    }
    const {
      disabled
    } = this.props;
    const {
      is_spot: isSpot
    } = payload;
    const onChange = (value) => this.setState({
      payload: {
        ...payload,
        is_spot: /^true$/i.test(value)
      }
    }, this.onPayloadChanged);
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Price type:
        </span>
        <Select
          value={`${isSpot}`}
          onChange={onChange}
          style={{flex: 1, maxWidth: 300}}
          disabled={disabled || (instanceTypesPending && priceTypes.length === 0)}
        >
          {
            priceTypes
              .map(o => /^spot$/i.test(o))
              .map(o => (
                <Select.Option key={`${o}`} value={`${o}`}>
                  {
                    getSpotTypeName(o, this.currentProvider)
                  }
                </Select.Option>
              ))
          }
        </Select>
      </div>
    );
  };

  renderTimeout = () => {
    const {
      payload
    } = this.state;
    if (!payload) {
      return null;
    }
    const {
      disabled
    } = this.props;
    const {
      timeout
    } = payload;
    const onChange = newTimeout => this.setState({
      payload: {
        ...payload,
        timeout: newTimeout
      }
    }, this.reportChanged);
    let valid = true;
    try {
      validateTimeout(timeout);
    } catch (e) {
      valid = false;
    }
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Timeout:
        </span>
        <InputNumber
          disabled={disabled}
          onChange={onChange}
          style={{flex: 1, maxWidth: 300}}
          value={timeout}
          className={
            classNames(
              {
                'cp-error': !valid
              }
            )
          }
        />
      </div>
    );
  };

  renderCmdTemplate = () => {
    const {
      payload
    } = this.state;
    if (!payload) {
      return null;
    }
    const {
      disabled
    } = this.props;
    const {
      cmd_template: cmdTemplate,
      startIdle
    } = payload;
    const onChange = newCmdTemplate => this.setState({
      payload: {
        ...payload,
        cmd_template: newCmdTemplate,
        startIdle: false
      }
    }, this.reportChanged);
    const onChangeStartIdle = e => this.setState({
      payload: {
        ...payload,
        startIdle: e.target.checked
      }
    }, this.reportChanged);
    return (
      <div>
        <div
          className={styles.row}
        >
          <span className={styles.title}>
            Cmd template:
          </span>
          <Checkbox
            disabled={disabled}
            checked={startIdle}
            onChange={onChangeStartIdle}
          >
            Start idle
          </Checkbox>
        </div>
        {
          !startIdle && (
            <CodeEditor
              readOnly={disabled}
              language="shell"
              onChange={onChange}
              lineWrapping
              code={cmdTemplate}
            />
          )
        }
      </div>
    );
  };

  renderRootEntity = () => {
    const {
      rootEntityId,
      classes = []
    } = this.state;
    const {
      disabled,
      rootEntityDisabled
    } = this.props;
    if (classes.length === 0) {
      return null;
    }
    const onChange = newRootEntityId => this.setState({
      rootEntityId: newRootEntityId ? Number(newRootEntityId) : undefined
    }, this.reportChanged);
    return (
      <ParameterRow>
        <ParameterName>
          Root entity:
        </ParameterName>
        <ParameterValue>
          <Select
            disabled={disabled || rootEntityDisabled}
            style={{width: '100%'}}
            value={rootEntityId ? `${rootEntityId}` : undefined}
            onChange={onChange}
          >
            {
              classes.map((aType) => (
                <Select.Option
                  key={`${aType.id}`}
                  value={`${aType.id}`}
                  title={aType.name}
                >
                  {aType.name}
                </Select.Option>
              ))
            }
          </Select>
        </ParameterValue>
      </ParameterRow>
    );
  };

  renderCapabilities = () => {
    const {
      payload,
      tool
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    const {
      docker_image: dockerImage
    } = payload;
    return (
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Run capabilities:
        </span>
        <ParametersRunCapabilities
          disabled={disabled}
          style={{flex: 1, maxWidth: '50%'}}
          dockerImage={dockerImage}
          tool={tool}
          provider={this.currentProvider}
          region={this.currentCloudRegion}
        />
      </div>
    );
  };

  renderLimitMounts = () => {
    const {
      payload,
      tool
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    return (
      <div
        className={styles.row}
      >
        <span
          className={styles.title}
          style={{
            alignSelf: 'flex-start'
          }}
        >
          Limit mounts:
        </span>
        <ParametersLimitMounts
          disabled={disabled}
          style={{flex: 1, maxWidth: '50%'}}
          tool={tool}
        />
      </div>
    );
  };

  renderNotifications = () => {
    const {
      payload
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!payload) {
      return null;
    }
    const {
      notifications = []
    } = payload;
    const onChange = newNotifications => this.setState({
      payload: {
        ...payload,
        notifications: newNotifications
      }
    }, this.reportChanged);
    return (
      <div
        className={styles.row}
      >
        <span
          className={styles.title}
          style={{
            alignSelf: 'flex-start'
          }}
        >
          Notifications:
        </span>
        <JobNotifications
          disabled={disabled}
          style={{flex: 1, maxWidth: '50%'}}
          value={notifications.map(mapObservableNotification)}
          onChange={onChange}
          linkStyle={{margin: 0, alignSelf: 'center'}}
        />
      </div>
    );
  };

  render () {
    const entry = this.entry;
    if (!entry) {
      return (
        <Alert
          message="Select configuration"
          type="info"
        />
      );
    }
    const {
      payload = {},
      projectMetadata = {}
    } = this.state;
    const {
      className,
      style,
      disabled
    } = this.props;
    const {
      parameters = {}
    } = payload;
    const changeParameters = (modifiedParameters, valid) => this.setState({
      payload: {
        ...payload,
        modifiedParameters
      },
      parametersValid: valid
    }, this.reportChanged);
    return (
      <Parameters.Provider
        className={
          classNames(
            className,
            styles.configurationPayload
          )
        }
        style={style}
        parameters={parameters}
        onChange={changeParameters}
        entityFields={this.currentEntityTypeFields.map(o => o.name)}
        projectMetadataFields={Object.keys(projectMetadata)}
      >
        <Collapse
          defaultActiveKey={['parameters']}
          bordered={false}
          style={{
            flex: 1,
            overflow: 'auto'
          }}
        >
          <Collapse.Panel key="exec" header="Execution environment">
            {this.renderPipeline()}
            {this.renderDockerImage()}
            {this.renderInstanceType()}
            {this.renderDisk()}
            {this.renderCloudRegion()}
            {this.renderCapabilities()}
          </Collapse.Panel>
          <Collapse.Panel key="advanced" header="Advanced">
            {this.renderPriceType()}
            {this.renderNotifications()}
            {this.renderTimeout()}
            {this.renderLimitMounts()}
            {this.renderCmdTemplate()}
            <Parameters
              disabled={disabled}
              mode={Parameters.Modes.system}
              editable={!this.pipelineSpecified}
            />
          </Collapse.Panel>
          <Collapse.Panel key="parameters" header="Parameters">
            {this.renderRootEntity()}
            <Parameters
              disabled={disabled}
              mode={Parameters.Modes.nonSystem}
              editable={!this.pipelineSpecified}
            />
          </Collapse.Panel>
        </Collapse>
      </Parameters.Provider>
    );
  };
}

ConfigurationPayload.propTypes = {
  disabled: PropTypes.bool,
  className: PropTypes.string,
  configurationId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  entryName: PropTypes.string,
  folderId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  style: PropTypes.object,
  onChange: PropTypes.func,
  rootEntityDisabled: PropTypes.bool
};

export default inject('pipelines', 'dockerRegistries', 'cloudRegionsInfo')(
  observer(ConfigurationPayload)
);
