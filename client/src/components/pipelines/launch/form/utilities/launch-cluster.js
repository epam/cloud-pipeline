/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Checkbox,
  InputNumber,
  Icon,
  Modal,
  Radio,
  Row,
  Select
} from 'antd';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  LaunchClusterTooltip,
  renderTooltip
} from './launch-cluster-tooltips';
import {getSpotTypeName} from '../../../../special/spot-instance-names';
import {booleanParameterIsSetToValue} from './parameter-utilities';
import AllowedInstancesCountWarning from './allowed-instances-count-warning';
import {
  CP_CAP_SGE,
  CP_CAP_SPARK,
  CP_CAP_SLURM,
  CP_CAP_KUBE,
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_AUTOSCALE_HYBRID,
  CP_CAP_AUTOSCALE_PRICE_TYPE,
  CP_CAP_LIMIT_MOUNTS,
  CP_CAP_RESCHEDULE_RUN,
  CP_CAP_AUTOSCALE_HYBRID_FAMILY,
  CP_CAP_AUTOSCALE_INSTANCE_TYPE
} from './parameters';
import {getRunCapabilitiesSkippedParameters} from './run-capabilities';
import {
  getSkippedParameters as getGPUScalingSkippedParameters,
  getGPUScalingDefaultConfiguration,
  getScalingConfigurationForProvider,
  gpuScalingAvailable,
  InstanceTypeSelector,
  InstanceFamilySelector
} from './enable-gpu-scaling';
import {getInstanceFamilyByName} from '../../../../../utils/instance-family';
import {instanceInfoString} from '../../../../special/instance-type-info';

const PARAMETER_TITLE_WIDTH = 110;
const PARAMETER_TITLE_RIGHT_MARGIN = 5;
const LEFT_MARGIN = PARAMETER_TITLE_WIDTH + PARAMETER_TITLE_RIGHT_MARGIN;
const PARAMETER_TITLE_STYLE = {
  width: PARAMETER_TITLE_WIDTH,
  marginRight: PARAMETER_TITLE_RIGHT_MARGIN
};

export function hybridAutoScaledClusterEnabled (parameters) {
  return autoScaledClusterEnabled(parameters) &&
    booleanParameterIsSetToValue(parameters, CP_CAP_AUTOSCALE_HYBRID);
}

export function gridEngineEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SGE);
}

export function sparkEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SPARK);
}

export function slurmEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SLURM);
}

export function autoScaledClusterEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_AUTOSCALE) && (
    gridEngineEnabled(parameters) ||
    slurmEnabled(parameters)
  );
}

export function kubeEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_KUBE);
}

export const AUTO_SCALE_PRICE_TYPES = {
  master: 'master',
  spot: 'spot',
  onDemand: 'on-demand'
};

export function getAutoScaledPriceTypeValue (parameters) {
  if (!parameters || !parameters.hasOwnProperty(CP_CAP_AUTOSCALE_PRICE_TYPE)) {
    return undefined;
  }
  return [AUTO_SCALE_PRICE_TYPES.spot, AUTO_SCALE_PRICE_TYPES.onDemand]
    .includes(`${parameters[CP_CAP_AUTOSCALE_PRICE_TYPE].value}`)
    ? `${parameters[CP_CAP_AUTOSCALE_PRICE_TYPE].value}` : undefined;
}

export const CLUSTER_TYPE = {
  singleNode: 0,
  fixedCluster: 1,
  autoScaledCluster: 2
};

export function getSkippedSystemParametersList (controller) {
  if (controller && controller.state && controller.state.launchCluster &&
    (
      controller.state.autoScaledCluster ||
      controller.state.gridEngineEnabled ||
      controller.state.sparkEnabled ||
      controller.state.slurmEnabled ||
      controller.state.hybridAutoScaledClusterEnabled
    )) {
    return [
      CP_CAP_SGE,
      CP_CAP_SPARK,
      CP_CAP_SLURM,
      CP_CAP_KUBE,
      CP_CAP_AUTOSCALE,
      CP_CAP_AUTOSCALE_WORKERS,
      CP_CAP_AUTOSCALE_HYBRID,
      CP_CAP_AUTOSCALE_PRICE_TYPE,
      CP_CAP_RESCHEDULE_RUN,
      ...getRunCapabilitiesSkippedParameters(),
      ...getGPUScalingSkippedParameters(controller.props.preferences)
    ];
  }
  return [
    CP_CAP_AUTOSCALE,
    CP_CAP_AUTOSCALE_WORKERS,
    CP_CAP_RESCHEDULE_RUN,
    ...getRunCapabilitiesSkippedParameters()
  ];
}

export function getAllSkippedSystemParametersList (preferences) {
  return [
    CP_CAP_LIMIT_MOUNTS,
    CP_CAP_SGE,
    CP_CAP_SPARK,
    CP_CAP_SLURM,
    CP_CAP_KUBE,
    CP_CAP_AUTOSCALE,
    CP_CAP_AUTOSCALE_WORKERS,
    CP_CAP_AUTOSCALE_HYBRID,
    CP_CAP_AUTOSCALE_PRICE_TYPE,
    ...getRunCapabilitiesSkippedParameters(),
    ...getGPUScalingSkippedParameters(preferences)
  ];
}

export function getSystemParameterDisabledState (controller, parameterName) {
  return getSkippedSystemParametersList(controller).indexOf(parameterName) >= 0;
}

export function setClusterParameterValue (form, sectionName, configuration) {
  if (!form || !configuration) {
    return;
  }
  const {
    gridEngineEnabled,
    sparkEnabled,
    slurmEnabled,
    kubeEnabled,
    hybridAutoScaledClusterEnabled,
    autoScaledPriceType
  } = configuration;
  const formValue = form.getFieldValue(sectionName);
  if (!formValue || !formValue.hasOwnProperty('params')) {
    return;
  }
  const {params} = formValue;
  const keys = Object.keys(params);
  let modified = false;
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const value = params[key];
    if (!value || !value.name) {
      continue;
    }
    if (value.name === CP_CAP_SGE) {
      value.value = `${gridEngineEnabled}`;
      modified = true;
    }
    if (value.name === CP_CAP_SPARK) {
      value.value = `${sparkEnabled}`;
      modified = true;
    }
    if (value.name === CP_CAP_SLURM) {
      value.value = `${slurmEnabled}`;
      modified = true;
    }
    if (value.name === CP_CAP_KUBE) {
      value.value = `${kubeEnabled}`;
      modified = true;
    }
    if (value.name === CP_CAP_AUTOSCALE_PRICE_TYPE && autoScaledPriceType) {
      value.value = `${autoScaledPriceType}`;
      modified = true;
    }
    if (value.name === CP_CAP_AUTOSCALE_HYBRID) {
      value.value = `${hybridAutoScaledClusterEnabled}`;
      modified = true;
    }
  }
  if (modified) {
    form.setFieldsValue(
      {
        [sectionName]: formValue
      }
    );
  }
}

const range = ({nodesCount, maxNodesCount}) => {
  return `${nodesCount} - ${maxNodesCount}`;
};

const plural = (count, str) => {
  if (count === 1) {
    return `${count} ${str}`;
  }
  return `${count} ${str}s`;
};

const lowerCasedString = (string, lowercased) => {
  if (lowercased) {
    return (string || '').toLowerCase();
  }
  return string;
};

export function parseChildNodeInstanceConfiguration (options) {
  const {
    autoScaled = false,
    gpuScaling = false,
    hybrid = false,
    parameters = {}
  } = options || {};
  if (autoScaled && !gpuScaling) {
    const readParameterValue = parameter => parameters && parameters[parameter]
      ? parameters[parameter].value
      : undefined;
    if (hybrid) {
      return readParameterValue(CP_CAP_AUTOSCALE_HYBRID_FAMILY);
    }
    return readParameterValue(CP_CAP_AUTOSCALE_INSTANCE_TYPE);
  }
  return undefined;
}

export function applyChildNodeInstanceParameters (parameters, value, hybrid) {
  if (value && parameters) {
    const applyParameter = (parameterName) => {
      parameters[parameterName] = {
        value
      };
    };
    if (hybrid) {
      applyParameter(CP_CAP_AUTOSCALE_HYBRID_FAMILY);
    } else {
      applyParameter(CP_CAP_AUTOSCALE_INSTANCE_TYPE);
    }
  }
}

export function applyChildNodeInstanceParametersAsArray (parameters, value, hybrid) {
  if (value && parameters) {
    const applyParameter = (parameterName) => {
      parameters.push({
        name: parameterName,
        type: 'string',
        value
      });
    };
    if (hybrid) {
      applyParameter(CP_CAP_AUTOSCALE_HYBRID_FAMILY);
    } else {
      applyParameter(CP_CAP_AUTOSCALE_INSTANCE_TYPE);
    }
  }
}

@inject('preferences')
@observer
class ConfigureClusterDialog extends React.Component {
  static getClusterName = (ctrl, lowerCased) => {
    if (ctrl.state.launchCluster && ctrl.state.autoScaledCluster) {
      const details = [
        ctrl.state.gridEngineEnabled ? 'GridEngine' : false,
        ctrl.state.slurmEnabled ? 'Slurm' : false,
        ctrl.state.hybridAutoScaledClusterEnabled ? 'hybrid' : false,
        ctrl.state.gpuScalingConfiguration ? 'GPU' : false
      ].filter(Boolean).join(' ');
      const name = lowerCasedString(
        `Auto-scaled ${details ? `${details} ` : ''}cluster`,
        lowerCased
      );
      if (!isNaN(ctrl.state.nodesCount) && !isNaN(ctrl.state.maxNodesCount)) {
        return `${name} (${range(ctrl.state)} child nodes)`;
      }
      return name;
    } else if (ctrl.state.launchCluster) {
      let clusterName = lowerCasedString('Cluster', lowerCased);
      if (ctrl.state.gridEngineEnabled) {
        clusterName = `GridEngine ${lowerCasedString('Cluster', lowerCased)}`;
      }
      if (ctrl.state.sparkEnabled) {
        clusterName = `Apache Spark ${lowerCasedString('Cluster', lowerCased)}`;
      }
      if (ctrl.state.slurmEnabled) {
        clusterName = `Slurm ${lowerCasedString('Cluster', lowerCased)}`;
      }
      if (ctrl.state.kubeEnabled) {
        clusterName = `Kubernetes ${lowerCasedString('Cluster', lowerCased)}`;
      }
      if (!isNaN(ctrl.state.nodesCount)) {
        return `${clusterName} (${plural(ctrl.state.nodesCount, 'child node')})`;
      }
      return clusterName;
    }
    return null;
  };

  static getClusterDescription = (ctrl, lowerCased = false) => {
    return ConfigureClusterDialog.getClusterName(ctrl, lowerCased) ||
      lowerCasedString('Single node', lowerCased);
  };

  static getConfigureClusterButtonDescription = (ctrl, lowerCased = false) => {
    return ConfigureClusterDialog.getClusterName(ctrl, lowerCased) ||
      lowerCasedString('Configure cluster', lowerCased);
  };

  static propTypes = {
    visible: PropTypes.bool,
    disabled: PropTypes.bool,
    cloudRegionProvider: PropTypes.string,
    launchCluster: PropTypes.bool,
    autoScaledCluster: PropTypes.bool,
    gridEngineEnabled: PropTypes.bool,
    sparkEnabled: PropTypes.bool,
    slurmEnabled: PropTypes.bool,
    kubeEnabled: PropTypes.bool,
    autoScaledPriceType: PropTypes.string,
    hybridAutoScaledClusterEnabled: PropTypes.bool,
    gpuScalingConfiguration: PropTypes.object,
    childNodeInstanceConfiguration: PropTypes.string,
    nodesCount: PropTypes.number,
    maxNodesCount: PropTypes.number,
    onChange: PropTypes.func,
    onClose: PropTypes.func,
    instanceName: PropTypes.string,
    instanceTypes: PropTypes.array
  };

  state = {
    launchCluster: false,
    autoScaledCluster: false,
    setDefaultNodesCount: false,
    gridEngineEnabled: false,
    sparkEnabled: false,
    slurmEnabled: false,
    kubeEnabled: false,
    hybridAutoScaledClusterEnabled: false,
    gpuScalingConfiguration: undefined,
    childNodeInstanceConfiguration: undefined,
    autoScaledPriceType: undefined,
    nodesCount: 0,
    maxNodesCount: 0,
    validation: {
      nodesCount: null,
      maxNodesCount: null
    }
  };

  get selectedClusterType () {
    if (this.state.launchCluster) {
      return this.state.autoScaledCluster &&
      !this.state.sparkEnabled &&
      !this.state.kubeEnabled
        ? CLUSTER_TYPE.autoScaledCluster
        : CLUSTER_TYPE.fixedCluster;
    } else {
      return CLUSTER_TYPE.singleNode;
    }
  }

  onChange = (e) => {
    if (this.props.disabled) {
      return;
    }
    switch (e.target.value) {
      case CLUSTER_TYPE.fixedCluster:
        this.setState({
          launchCluster: true,
          autoScaledCluster: false,
          setDefaultNodesCount: false,
          gridEngineEnabled: false,
          sparkEnabled: false,
          slurmEnabled: false,
          kubeEnabled: false,
          autoScaledPriceType: undefined,
          hybridAutoScaledClusterEnabled: false,
          gpuScalingConfiguration: undefined,
          childNodeInstanceConfiguration: undefined,
          nodesCount: Math.max(1,
            !isNaN(this.state.maxNodesCount)
              ? this.state.maxNodesCount
              : 1
          )
        }, this.validate);
        break;
      case CLUSTER_TYPE.autoScaledCluster:
        this.setState({
          launchCluster: true,
          autoScaledCluster: true,
          setDefaultNodesCount: false,
          nodesCount: undefined,
          gridEngineEnabled: true,
          sparkEnabled: false,
          slurmEnabled: false,
          kubeEnabled: false,
          autoScaledPriceType: undefined,
          hybridAutoScaledClusterEnabled: false,
          gpuScalingConfiguration: undefined,
          childNodeInstanceConfiguration: undefined,
          maxNodesCount: Math.max(1,
            !isNaN(this.state.nodesCount)
              ? this.state.nodesCount
              : 1
          )
        }, this.validate);
        break;
      case CLUSTER_TYPE.singleNode:
      default:
        this.setState({
          launchCluster: false,
          autoScaledCluster: false,
          setDefaultNodesCount: false,
          gridEngineEnabled: false,
          sparkEnabled: false,
          slurmEnabled: false,
          kubeEnabled: false,
          autoScaledPriceType: undefined,
          hybridAutoScaledClusterEnabled: false,
          gpuScalingConfiguration: undefined,
          childNodeInstanceConfiguration: undefined
        }, this.validate);
        break;
    }
  };

  onChangeNodeCount = (value) => {
    this.setState({
      nodesCount: value
    }, this.validate);
  };

  onChangeMaxNodeCount = (value) => {
    this.setState({
      maxNodesCount: value
    }, this.validate);
  };

  getInputStyle = (field) => {
    if (this.state.validation[field]) {
      return 'cp-error';
    }
    return undefined;
  };

  getValidationRow = (field) => {
    if (this.state.validation[field]) {
      return (
        <Row
          key={`${field} validation`}
          type="flex"
          align="middle"
          className="cp-error"
          style={{paddingLeft: 115, fontSize: 'smaller'}}>
          {this.state.validation[field]}
        </Row>
      );
    }
    return null;
  };

  onChangeEnableGridEngine = (e) => {
    if (this.state.launchCluster && this.state.autoScaledCluster) {
      this.setState({
        gridEngineEnabled: e.target.checked,
        sparkEnabled: false,
        slurmEnabled: !e.target.checked,
        kubeEnabled: false
      });
    } else {
      this.setState({
        gridEngineEnabled: e.target.checked,
        sparkEnabled: false,
        slurmEnabled: false,
        kubeEnabled: false,
        hybridAutoScaledClusterEnabled: false,
        gpuScalingConfiguration: undefined,
        childNodeInstanceConfiguration: undefined
      });
    }
  };

  onChangeEnableSpark = (e) => {
    this.setState({
      gridEngineEnabled: false,
      sparkEnabled: e.target.checked,
      slurmEnabled: false,
      kubeEnabled: false,
      hybridAutoScaledClusterEnabled: false,
      gpuScalingConfiguration: undefined,
      childNodeInstanceConfiguration: undefined
    });
  };

  onChangeEnableSlurm = (e) => {
    if (this.state.launchCluster && this.state.autoScaledCluster) {
      this.setState({
        gridEngineEnabled: !e.target.checked,
        sparkEnabled: false,
        slurmEnabled: e.target.checked,
        kubeEnabled: false,
        gpuScalingConfiguration: undefined,
        childNodeInstanceConfiguration: undefined
      });
    } else {
      this.setState({
        gridEngineEnabled: false,
        sparkEnabled: false,
        slurmEnabled: e.target.checked,
        kubeEnabled: false,
        hybridAutoScaledClusterEnabled: false,
        gpuScalingConfiguration: undefined,
        childNodeInstanceConfiguration: undefined
      });
    }
  };

  onChangeEnableKube = (e) => {
    this.setState({
      gridEngineEnabled: false,
      sparkEnabled: false,
      slurmEnabled: false,
      kubeEnabled: e.target.checked,
      hybridAutoScaledClusterEnabled: false,
      gpuScalingConfiguration: undefined,
      childNodeInstanceConfiguration: undefined
    });
  };

  onChangeEnableHybridAutoScaledCluster = (e) => {
    const {
      cloudRegionProvider: provider,
      preferences
    } = this.props;
    const {
      gpuScalingConfiguration
    } = this.state;
    const gpuScalingConfigurationEnabled = !!gpuScalingConfiguration;
    this.setState({
      sparkEnabled: false,
      kubeEnabled: false,
      hybridAutoScaledClusterEnabled: e.target.checked,
      gpuScalingConfiguration: gpuScalingConfigurationEnabled
        ? getGPUScalingDefaultConfiguration({provider, hybrid: e.target.checked}, preferences)
        : undefined,
      childNodeInstanceConfiguration: undefined
    });
  };

  onChangeEnableGPUScaling = (e) => {
    const {
      cloudRegionProvider,
      preferences
    } = this.props;
    const {
      hybridAutoScaledClusterEnabled: hybrid
    } = this.state;
    this.setState({
      gpuScalingConfiguration: e.target.checked
        ? getGPUScalingDefaultConfiguration({provider: cloudRegionProvider, hybrid}, preferences)
        : undefined,
      childNodeInstanceConfiguration: undefined
    });
  };

  renderFixedClusterConfiguration = () => {
    return [
      <Row key="nodes count" type="flex" align="middle" style={{marginTop: 5}}>
        <span style={PARAMETER_TITLE_STYLE}>Child nodes:</span>
        <InputNumber
          min={1}
          max={this.launchMaxScheduledNumber}
          disabled={this.props.disabled}
          className={this.getInputStyle('nodesCount')}
          style={{flex: 1}}
          value={this.state.nodesCount}
          onChange={this.onChangeNodeCount} />
      </Row>,
      <Row
        key="nodes count warning row"
        type="flex"
        style={{
          marginTop: 5,
          marginLeft: 115
        }}
      >
        <AllowedInstancesCountWarning
          key="nodes count warning"
          payload={{
            nodeCount: this.state.nodesCount
          }}
          style={{width: '100%'}}
        />
      </Row>,
      this.getValidationRow('nodesCount'),
      <Row key="enable grid engine" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.gridEngineEnabled}
          onChange={this.onChangeEnableGridEngine}>
          Enable GridEngine
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.cluster.enableGridEngine)}
      </Row>,
      <Row key="enable slurm" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.slurmEnabled}
          onChange={this.onChangeEnableSlurm}>
          Enable Slurm
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.cluster.enableSlurm)}
      </Row>,
      <Row key="enable spark" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.sparkEnabled}
          onChange={this.onChangeEnableSpark}>
          Enable Apache Spark
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.cluster.enableSpark)}
      </Row>,
      <Row key="enable kube" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.kubeEnabled}
          onChange={this.onChangeEnableKube}>
          Enable Kubernetes
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.cluster.enableKube)}
      </Row>
    ].filter(r => !!r);
  };

  renderGPUScalingParameter = (value, onChange, gpu = false) => {
    const {hybridAutoScaledClusterEnabled: hybrid} = this.state;
    const label = (
      <span>
        {gpu ? 'GPU' : 'CPU'} {hybrid ? 'family' : 'instance'}:
      </span>
    );
    const {
      cloudRegionProvider
    } = this.props;
    return (
      <Row
        key={`gpu-scaling-${gpu ? 'gpu' : 'cpu'}-family`}
        style={{
          marginTop: 5,
          paddingRight: 15
        }}
        type="flex"
        align="middle"
      >
        <div style={PARAMETER_TITLE_STYLE}>
          {label}
        </div>
        {
          hybrid && (
            <InstanceFamilySelector
              value={value}
              onChange={onChange}
              instanceTypes={this.props.instanceTypes}
              provider={cloudRegionProvider}
              gpu={gpu}
              style={{flex: 1}}
            />
          )
        }
        {
          !hybrid && (
            <InstanceTypeSelector
              value={value}
              onChange={onChange}
              instanceTypes={this.props.instanceTypes}
              gpu={gpu}
              style={{flex: 1}}
            />
          )
        }
      </Row>
    );
  };

  renderChildNodeInstanceParameter = () => {
    const {
      hybridAutoScaledClusterEnabled: hybrid,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration: value
    } = this.state;
    if (gpuScalingConfiguration) {
      return null;
    }
    const {
      instanceName,
      cloudRegionProvider
    } = this.props;
    const label = (
      <span style={PARAMETER_TITLE_STYLE}>
        Workers {hybrid ? 'family' : 'instance'}:
      </span>
    );
    const onChange = (newValue) => {
      this.setState({
        childNodeInstanceConfiguration: newValue
      });
    };
    const getEmptyConfigFromString = (str) => ({
      name: str,
      tooltip: str
    });
    const {
      name: emptyName,
      tooltip: emptyTooltip
    } = (() => {
      if (hybrid) {
        const family = getInstanceFamilyByName(instanceName, cloudRegionProvider);
        if (family) {
          return getEmptyConfigFromString(`Master's config - ${family}`)
        }
        return getEmptyConfigFromString('Master\'s config');
      }
      if (instanceName) {
        const instance = (this.props.instanceTypes || []).find((o) => o.name === instanceName);
        if (instance) {
          return {
            name: (
              <div style={{display: 'flex', flexDirection: 'row'}}>
                <span>
                  Master's config
                </span>
                <span style={{marginLeft: 5, marginRight: 5}}>
                  -
                </span>
                {instanceInfoString(instance, {plainText: false})}
              </div>
            ),
            tooltip: `Master's config - ${instanceInfoString(instance, {plainText: true})}`
          };
        }
        return getEmptyConfigFromString(`Master's config - ${instanceName}`);
      }
      return getEmptyConfigFromString('Master\'s config');
    })();
    return (
      <Row
        key={`child-node-${hybrid ? 'family' : 'instance'}`}
        style={{
          marginTop: 5
        }}
        type="flex"
        align="middle"
      >
        {label}
        {
          hybrid && (
            <InstanceFamilySelector
              value={value}
              onChange={onChange}
              instanceTypes={this.props.instanceTypes}
              provider={cloudRegionProvider}
              style={{flex: 1}}
              allowEmpty
              emptyName={emptyName}
              emptyTooltip={emptyTooltip}
            />
          )
        }
        {
          !hybrid && (
            <InstanceTypeSelector
              value={value}
              onChange={onChange}
              instanceTypes={this.props.instanceTypes}
              style={{flex: 1}}
              allowEmpty
              emptyName={emptyName}
              emptyTooltip={emptyTooltip}
            />
          )
        }
        {renderTooltip(
          hybrid
            ? LaunchClusterTooltip.autoScaledCluster.childNodeInstanceFamily
            : LaunchClusterTooltip.autoScaledCluster.childNodeInstance,
          {marginLeft: 5}
        )}
      </Row>
    );
  };

  renderAutoScaledClusterConfiguration = () => {
    const onSetUpChildNodesClicked = () => {
      this.setState({
        setDefaultNodesCount: true,
        maxNodesCount: !isNaN(this.state.maxNodesCount)
          ? Math.max(2, this.state.maxNodesCount)
          : 2,
        nodesCount: !isNaN(this.state.nodesCount)
          ? Math.max(1, this.state.nodesCount)
          : 1
      });
    };
    const onUnsetChildNodes = () => {
      this.setState({
        setDefaultNodesCount: false
      }, this.validate);
    };
    const renderSetDefaultNodesCount = () => {
      if (this.state.setDefaultNodesCount) {
        return [
          <Row key="nodes count" type="flex" align="middle" style={{marginTop: 5}}>
            <span style={PARAMETER_TITLE_STYLE}>Default child nodes:</span>
            <InputNumber
              min={1}
              max={this.launchMaxScheduledNumber}
              disabled={this.props.disabled}
              className={this.getInputStyle('nodesCount')}
              style={{flex: 1}}
              value={this.state.nodesCount}
              onChange={this.onChangeNodeCount} />
            {
              renderTooltip(
                LaunchClusterTooltip.autoScaledCluster.defaultNodesCount,
                {marginLeft: 5}
              )
            }
            <a
              onClick={onUnsetChildNodes}
              className="cp-text underline"
              style={{marginLeft: 5}}
            >
              <Icon type="close" /> Reset
            </a>
          </Row>
        ];
      } else {
        return [
          <Row key="nodes count" type="flex" align="middle" style={{marginTop: 5}}>
            <span style={{marginLeft: LEFT_MARGIN, flex: 1}}>
              <a
                onClick={onSetUpChildNodesClicked}
                className="cp-text underline"
              >
                Setup default child nodes count
              </a>
              {
                renderTooltip(
                  LaunchClusterTooltip.autoScaledCluster.defaultNodesCount,
                  {marginLeft: 5}
                )
              }
            </span>
          </Row>
        ];
      }
    };
    const renderAutoScaledPriceTypeSelect = () => (
      <Row key="autoScalePriceType" type="flex" align="middle" style={{marginTop: 5}}>
        <span style={PARAMETER_TITLE_STYLE}>Workers price type:</span>
        <Select
          style={{flex: 1}}
          value={
            this.state.autoScaledPriceType
              ? this.state.autoScaledPriceType
              : AUTO_SCALE_PRICE_TYPES.master
          }
          onSelect={(autoScaledPriceType) => {
            if (autoScaledPriceType === AUTO_SCALE_PRICE_TYPES.master) {
              this.setState({autoScaledPriceType: undefined});
            } else {
              this.setState({autoScaledPriceType});
            }
          }}
        >
          <Select.Option key={AUTO_SCALE_PRICE_TYPES.master}>Master's config</Select.Option>
          <Select.Option key={AUTO_SCALE_PRICE_TYPES.spot}>
            {getSpotTypeName(true, this.props.cloudRegionProvider)}
          </Select.Option>
          <Select.Option key={AUTO_SCALE_PRICE_TYPES.onDemand}>
            {getSpotTypeName(false, this.props.cloudRegionProvider)}
          </Select.Option>
        </Select>
        {renderTooltip(
          LaunchClusterTooltip.autoScaledCluster.autoScalePriceType,
          {marginLeft: 5}
        )}
      </Row>
    );
    const renderGPUScalingConfigurationToggle = () => {
      const {preferences, cloudRegionProvider} = this.props;
      const configuration = getScalingConfigurationForProvider(cloudRegionProvider, preferences);
      if (!configuration || this.state.slurmEnabled) {
        return [];
      }
      const {
        gpuScalingConfiguration
      } = this.state;
      return (
        <Row
          key="gpu-scaling-checkbox"
          type="flex"
          align="middle"
          style={{marginTop: 5}}
        >
          <Checkbox
            style={{marginLeft: LEFT_MARGIN}}
            checked={!!gpuScalingConfiguration}
            onChange={this.onChangeEnableGPUScaling}>
            Enable GPU scaling
          </Checkbox>
          {renderTooltip(LaunchClusterTooltip.autoScaledCluster.gpuScaling, {marginLeft: 5})}
        </Row>
      );
    };
    const renderGPUScalingConfiguration = () => {
      const {preferences, cloudRegionProvider} = this.props;
      const configuration = getScalingConfigurationForProvider(cloudRegionProvider, preferences);
      if (!configuration || this.state.slurmEnabled) {
        return [];
      }
      const {
        gpuScalingConfiguration
      } = this.state;
      const renderers = [];
      if (gpuScalingConfiguration) {
        const {cpu, gpu} = gpuScalingConfiguration;
        const onChangeCPU = value => this.setState({
          gpuScalingConfiguration: {...gpuScalingConfiguration, cpu: {...cpu, value}}
        });
        const onChangeGPU = value => this.setState({
          gpuScalingConfiguration: {...gpuScalingConfiguration, gpu: {...gpu, value}}
        });
        renderers.push(this.renderGPUScalingParameter(cpu?.value, onChangeCPU));
        renderers.push(this.renderGPUScalingParameter(gpu?.value, onChangeGPU, true));
      }
      return renderers;
    };
    return [
      <Row key="max nodes count" type="flex" align="middle" style={{marginTop: 5}}>
        <span style={PARAMETER_TITLE_STYLE}>Auto-scaled up to:</span>
        <InputNumber
          min={this.state.setDefaultNodesCount ? 2 : 1}
          max={this.launchMaxAutoScaledNumber}
          disabled={this.props.disabled}
          className={this.getInputStyle('maxNodesCount')}
          style={{flex: 1}}
          value={this.state.maxNodesCount}
          onChange={this.onChangeMaxNodeCount} />
        {renderTooltip(LaunchClusterTooltip.autoScaledCluster.autoScaledUpTo, {marginLeft: 5})}
      </Row>,
      <Row
        key="nodes count warning row"
        type="flex"
        style={{
          marginTop: 5,
          marginLeft: 115,
          marginRight: 17
        }}
      >
        <AllowedInstancesCountWarning
          payload={{
            nodeCount: this.state.setDefaultNodesCount
              ? this.state.nodesCount
              : 0,
            maxNodeCount: this.state.maxNodesCount
          }}
          style={{
            width: '100%'
          }}
        />
      </Row>,
      this.getValidationRow('maxNodesCount'),
      ...renderSetDefaultNodesCount(),
      <Row key="enable grid engine" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.gridEngineEnabled}
          onChange={this.onChangeEnableGridEngine}>
          Enable GridEngine
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.autoScaledCluster.enableGridEngine)}
      </Row>,
      <Row key="enable slurm" type="flex" align="middle" style={{marginTop: 5}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.slurmEnabled}
          onChange={this.onChangeEnableSlurm}>
          Enable Slurm
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.autoScaledCluster.enableSlurm)}
      </Row>,
      <Row key="enable hybrid" type="flex" align="middle" style={{marginTop: 15}}>
        <Checkbox
          style={{marginLeft: LEFT_MARGIN}}
          checked={this.state.hybridAutoScaledClusterEnabled}
          onChange={this.onChangeEnableHybridAutoScaledCluster}>
          Enable Hybrid cluster
        </Checkbox>
        {renderTooltip(LaunchClusterTooltip.autoScaledCluster.hybridAutoScaledCluster)}
      </Row>,
      renderGPUScalingConfigurationToggle(),
      this.renderChildNodeInstanceParameter(),
      ...renderGPUScalingConfiguration(),
      this.getValidationRow('nodesCount'),
      renderAutoScaledPriceTypeSelect()
    ].filter(r => !!r);
  };

  onOkClicked = () => {
    if (this.props.disabled) {
      this.props.onClose && this.props.onClose();
      return;
    }
    if (this.validate()) {
      this.props.onChange && this.props.onChange({
        nodesCount: this.state.launchCluster &&
        (!this.state.autoScaledCluster || this.state.setDefaultNodesCount)
          ? this.state.nodesCount
          : undefined,
        maxNodesCount: this.state.launchCluster && this.state.autoScaledCluster
          ? this.state.maxNodesCount : undefined,
        launchCluster: this.state.launchCluster,
        autoScaledCluster: this.state.autoScaledCluster,
        gridEngineEnabled: this.state.gridEngineEnabled,
        hybridAutoScaledClusterEnabled: this.state.hybridAutoScaledClusterEnabled,
        gpuScalingConfiguration: this.state.gpuScalingConfiguration,
        childNodeInstanceConfiguration: this.state.childNodeInstanceConfiguration,
        sparkEnabled: this.state.sparkEnabled,
        slurmEnabled: this.state.slurmEnabled,
        kubeEnabled: this.state.kubeEnabled,
        autoScaledPriceType: this.state.autoScaledPriceType
      });
    }
  };

  render () {
    const {sparkEnabled, kubeEnabled} = this.state;
    return (
      <Modal
        title={
          this.props.instanceName
            ? `Configure cluster (${this.props.instanceName})`
            : 'Configure cluster'
        }
        onCancel={this.props.onClose}
        onOk={this.onOkClicked}
        visible={this.props.visible}
        width={566}>
        <div>
          <Row type="flex">
            <div style={{marginLeft: LEFT_MARGIN}}>
              <Radio.Group
                onChange={this.onChange}
                value={this.selectedClusterType}>
                <Radio.Button value={CLUSTER_TYPE.singleNode}>Single node</Radio.Button>
                <Radio.Button value={CLUSTER_TYPE.fixedCluster}>Cluster</Radio.Button>
                <Radio.Button
                  disabled={sparkEnabled || kubeEnabled}
                  value={CLUSTER_TYPE.autoScaledCluster}
                >
                  Auto-scaled cluster
                </Radio.Button>
              </Radio.Group>
              {renderTooltip(LaunchClusterTooltip.clusterMode, {marginLeft: 10})}
            </div>
          </Row>
          {this.selectedClusterType === CLUSTER_TYPE.singleNode && (
            <Row
              type="flex"
              justify="center"
            >
              <AllowedInstancesCountWarning
                singleNode
                style={{width: '100%', marginTop: '5px'}}
              />
            </Row>
          )}
          {
            this.state.launchCluster && !this.state.autoScaledCluster &&
            this.renderFixedClusterConfiguration()
          }
          {
            this.state.launchCluster && this.state.autoScaledCluster &&
            this.renderAutoScaledClusterConfiguration()
          }
        </div>
      </Modal>
    );
  }

  @computed
  get launchMaxScheduledNumber () {
    if (this.props.preferences && this.props.preferences.loaded) {
      return +this.props.preferences.getPreferenceValue('launch.max.scheduled.number') - 1;
    }
    return undefined;
  }

  @computed
  get launchMaxAutoScaledNumber () {
    const scheduledMaxPreferenceValue = this.launchMaxScheduledNumber;
    if (this.props.preferences && this.props.preferences.loaded) {
      const autoScalingMaxPreferenceValue = this.props.preferences
        .getPreferenceValue('ge.autoscaling.scale.up.to.max');
      if (autoScalingMaxPreferenceValue && !isNaN(autoScalingMaxPreferenceValue)) {
        if (scheduledMaxPreferenceValue && !isNaN(scheduledMaxPreferenceValue)) {
          // 'ge.autoscaling.scale.up.to.max' should not be less then 'launch.max.scheduled.number'
          return Math.max(+autoScalingMaxPreferenceValue - 1, +scheduledMaxPreferenceValue);
        }
        return +autoScalingMaxPreferenceValue - 1;
      }
    }
    return scheduledMaxPreferenceValue;
  }

  validate = () => {
    let nodesCount = null;
    let maxNodesCount = null;
    if (this.state.launchCluster) {
      if (
        (!this.state.autoScaledCluster || this.state.setDefaultNodesCount) &&
        isNaN(this.state.nodesCount)
      ) {
        nodesCount = 'Enter positive number';
      } else if (
        (!this.state.autoScaledCluster || this.state.setDefaultNodesCount) &&
        +this.state.nodesCount <= 0
      ) {
        nodesCount = 'Value should be greater than 0';
      } else if (!this.state.autoScaledCluster || this.state.setDefaultNodesCount) {
        const maxValue = this.launchMaxScheduledNumber;
        if (maxValue && !isNaN(maxValue) && +maxValue > 0 && +this.state.nodesCount > maxValue) {
          nodesCount = `Maximum value is ${+maxValue}`;
        }
      }
      if (this.state.autoScaledCluster) {
        if (isNaN(this.state.maxNodesCount)) {
          maxNodesCount = 'Enter positive number';
        } else if (+this.state.maxNodesCount <= 0) {
          maxNodesCount = 'Value should be greater than 0';
        } else {
          const maxValue = this.launchMaxAutoScaledNumber;
          if (
            maxValue &&
            !isNaN(maxValue) && +maxValue > 0 &&
            +this.state.maxNodesCount > maxValue
          ) {
            maxNodesCount = `Maximum value is ${+maxValue}`;
          }
        }
        if (
          this.state.setDefaultNodesCount &&
          !nodesCount &&
          !maxNodesCount &&
          +this.state.nodesCount >= +this.state.maxNodesCount
        ) {
          nodesCount = 'Max child nodes count should be greater than child nodes count';
          maxNodesCount = 'Max child nodes count should be greater than child nodes count';
        }
      }
    }
    this.setState({
      validation: {nodesCount, maxNodesCount}
    });
    return !nodesCount && !maxNodesCount;
  };

  updateFromProps () {
    const isGPUScalingAvailable = gpuScalingAvailable(
      this.props.cloudRegionProvider,
      this.props.preferences
    );
    this.setState({
      launchCluster: this.props.launchCluster,
      autoScaledCluster: this.props.autoScaledCluster,
      gridEngineEnabled: this.props.gridEngineEnabled,
      sparkEnabled: this.props.sparkEnabled,
      slurmEnabled: this.props.slurmEnabled,
      kubeEnabled: this.props.kubeEnabled,
      autoScaledPriceType: this.props.autoScaledPriceType,
      hybridAutoScaledClusterEnabled: this.props.hybridAutoScaledClusterEnabled,
      gpuScalingConfiguration: isGPUScalingAvailable
        ? this.props.gpuScalingConfiguration
        : undefined,
      childNodeInstanceConfiguration: this.props.childNodeInstanceConfiguration,
      setDefaultNodesCount: this.props.nodesCount > 0,
      nodesCount: this.props.nodesCount && !isNaN(this.props.nodesCount)
        ? this.props.nodesCount
        : 0,
      maxNodesCount: this.props.maxNodesCount,
      validation: {
        nodesCount: null,
        maxNodesCount: null
      }
    }, this.validate);
  }

  componentDidUpdate (prevProps, nextContext) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateFromProps();
    }
  }
}

export {ConfigureClusterDialog};
