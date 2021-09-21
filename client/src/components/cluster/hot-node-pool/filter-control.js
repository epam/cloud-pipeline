/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {
  Input,
  Select
} from 'antd';
import UserName from '../../special/UserName';
import AddDockerRegistryControl from './add-docker-registry-control';

const RUN_OWNER = {
  key: 'RUN_OWNER',
  title: 'Run owner'
};

const RUN_OWNER_GROUP = {
  key: 'RUN_OWNER_GROUP',
  title: 'Run owner group'
};

const PIPELINE_ID = {
  key: 'PIPELINE_ID',
  title: 'Pipeline'
};

const RUN_PARAMETER = {
  key: 'RUN_PARAMETER',
  title: 'Parameter'
};

const RUN_CONFIGURATION_ID = {
  key: 'CONFIGURATION_ID',
  title: 'Run configuration'
};

const DOCKER_IMAGE = {
  key: 'DOCKER_IMAGE',
  title: 'Docker image'
};

const types = [
  RUN_OWNER,
  RUN_OWNER_GROUP,
  PIPELINE_ID,
  RUN_PARAMETER,
  RUN_CONFIGURATION_ID,
  DOCKER_IMAGE
];

const EQUAL = {
  key: 'EQUAL',
  title: 'Equals'
};

const NOT_EQUAL = {
  key: 'NOT_EQUAL',
  title: 'Not equals'
};

const EMPTY = {
  key: 'EMPTY',
  title: 'Is empty'
};

const NOT_EMPTY = {
  key: 'NOT_EMPTY',
  title: 'Is not empty'
};

const operators = [
  EQUAL,
  NOT_EQUAL,
  EMPTY,
  NOT_EMPTY
];

function criteriaDescription (criteria) {
  if (!criteria) {
    return '';
  }
  const {operator, type, value} = criteria;
  const valueDescription = typeof value === 'object' ? JSON.stringify(value) : `${value}`;
  return `${operator || ''}_${type || ''}_${valueDescription}`;
}

function criteriaValid (criteria) {
  if (!criteria) {
    return true;
  }
  const {operator, type, value} = criteria;
  if (!operator || !type) {
    return false;
  }
  if (/^run_parameter$/i.test(type)) {
    return !!value && Object.keys(value).length === 1 && !!Object.keys(value)[0];
  }
  return !/^(equal|not_equal)$/i.test(operator) || value;
}

function criteriaEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {operator: aOperator, type: aType, value: aValue} = a;
  const {operator: bOperator, type: bType, value: bValue} = b;
  if (aOperator !== bOperator || aType !== bType) {
    return false;
  }
  if (!aValue && !bValue) {
    return true;
  }
  if (!aValue || !bValue) {
    return false;
  }
  if (/^RUN_PARAMETER$/i.test(aType) && /^RUN_PARAMETER$/i.test(bType)) {
    const aKeys = Object.keys(aValue);
    const bKeys = Object.keys(bValue);
    if (aKeys.length !== bKeys.length) {
      return false;
    }
    for (let i = 0; i < aKeys.length; i++) {
      const key = aKeys[i];
      if (!bValue.hasOwnProperty(key) || `${bValue[key]}` !== `${aValue[key]}`) {
        return false;
      }
    }
    return true;
  }
  return `${aValue}` === `${bValue}`;
}

const mapCriteria = o => o ? ({...o}) : {};

class FilterControl extends React.Component {
  state = {
    operator: undefined,
    type: undefined,
    value: undefined,
    valueKey: undefined,
    valueValue: undefined
  }

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!criteriaEqual(prevProps.filter, this.props.filter)) {
      this.updateState();
    }
  }

  updateState = () => {
    const {operator, type, value} = mapCriteria(this.props.filter);
    let valueKey, valueValue;
    if (typeof value === 'object') {
      [valueKey, valueValue] = (Object.entries(value || {})[0] || []);
    }
    this.setState({
      operator,
      type,
      value,
      valueKey,
      valueValue
    });
  };

  onChangeOperator = (o) => {
    let {type, value, valueKey, valueValue} = this.state;
    if (/^(empty|not_empty)$/i.test(o)) {
      if (/^run_parameter$/i.test(type)) {
        if (value) {
          [valueKey] = Object.keys(value);
          valueValue = '';
          if (valueKey) {
            value = {[valueKey]: ''};
          }
        } else {
          value = {};
          valueKey = undefined;
          valueValue = '';
        }
      } else {
        value = undefined;
        valueKey = undefined;
        valueValue = undefined;
      }
    }
    this.setState({
      operator: o,
      value,
      valueKey,
      valueValue
    }, this.onChange);
  };

  onChangeType = (t) => {
    let {value, valueKey, valueValue, type, operator} = this.state;
    if (type !== t) {
      value = undefined;
      valueKey = '';
      valueValue = '';
    }
    if (/^RUN_PARAMETER$/i.test(t)) {
      value = {};
      valueKey = '';
      valueValue = '';
    }
    if (t && !operator) {
      operator = EQUAL.key;
    }
    this.setState({
      type: t,
      value,
      valueKey,
      valueValue,
      operator
    }, this.onChange);
  };

  onChangeValue = v => {
    let valueKey, valueValue;
    if (typeof v === 'object') {
      [valueKey, valueValue] = (Object.entries(v || {})[0] || []);
    }
    this.setState({
      value: v,
      valueKey,
      valueValue
    }, this.onChange);
  };

  onChange = () => {
    const {onChange} = this.props;
    if (onChange) {
      const {operator, type, value} = this.state;
      onChange({
        operator,
        type,
        value
      });
    }
  }

  renderUsersSelection = () => {
    const {disabled: d, users} = this.props;
    const {value} = this.state;
    const disabled = d || !users.loaded;
    const list = users.loaded ? (users.value || []) : [];
    if (users.error) {
      return (
        <span style={{color: 'red'}}>Error fetching users: {users.error}</span>
      );
    }
    const description = user => {
      if (user.attributes) {
        const getAttributesValues = () => {
          const values = [];
          for (let key in user.attributes) {
            if (user.attributes.hasOwnProperty(key)) {
              values.push(user.attributes[key]);
            }
          }
          return values;
        };
        return getAttributesValues();
      }
      return [user.userName];
    };
    return (
      <Select
        showSearch
        disabled={disabled}
        placeholder="Select owner"
        value={value}
        onChange={this.onChangeValue}
        style={{flex: 1}}
        getPopupContainer={triggerNode => triggerNode.parentNode}
        filterOption={
          (input, option) => option.props.description
            .find(d => (d || '').toLowerCase().indexOf(input.toLowerCase()) >= 0)
        }
      >
        {
          list.map(u => (
            <Select.Option
              key={u.userName}
              value={u.userName}
              description={description(u)}
            >
              <UserName userName={u.userName} />
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderGroupSelection = () => {
    const {disabled: d, roles} = this.props;
    const {value} = this.state;
    const disabled = d || !roles.loaded;
    const list = roles.loaded ? (roles.value || []) : [];
    if (roles.error) {
      return (
        <span style={{color: 'red'}}>Error fetching groups: {roles.error}</span>
      );
    }
    const splitRoleName = (name) => {
      if (name && name.toLowerCase().indexOf('role_') === 0) {
        return name.substring('role_'.length);
      }
      return name;
    };
    return (
      <Select
        showSearch
        disabled={disabled}
        placeholder="Select group"
        value={value}
        onChange={this.onChangeValue}
        style={{flex: 1}}
        getPopupContainer={triggerNode => triggerNode.parentNode}
        filterOption={
          (input, option) => option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
        }
      >
        {
          list.map(u => (
            <Select.Option
              key={u.name}
              value={u.name}
            >
              {u.predefined ? u.name : splitRoleName(u.name)}
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderPipelineSelection = () => {
    const {disabled: d, pipelines} = this.props;
    const {value} = this.state;
    const disabled = d || !pipelines.loaded;
    const list = pipelines.loaded ? (pipelines.value || []) : [];
    if (pipelines.error) {
      return (
        <span style={{color: 'red'}}>Error fetching pipelines: {pipelines.error}</span>
      );
    }
    return (
      <Select
        showSearch
        disabled={disabled}
        placeholder="Select pipeline"
        value={value ? `${value}` : undefined}
        onChange={this.onChangeValue}
        style={{flex: 1}}
        getPopupContainer={triggerNode => triggerNode.parentNode}
        filterOption={
          (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
        }
      >
        {
          list.map(pipeline => (
            <Select.Option
              key={`${pipeline.id}`}
              value={`${pipeline.id}`}
            >
              {pipeline.name}
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderConfigurationSelection = () => {
    const {disabled: d, allConfigurations: configurations} = this.props;
    const {value} = this.state;
    const disabled = d || !configurations.loaded;
    const list = configurations.loaded ? (configurations.value || []) : [];
    if (configurations.error) {
      return (
        <span style={{color: 'red'}}>Error fetching configurations: {configurations.error}</span>
      );
    }
    return (
      <Select
        showSearch
        disabled={disabled}
        placeholder="Select configuration"
        value={value ? `${value}` : undefined}
        onChange={this.onChangeValue}
        style={{flex: 1}}
        getPopupContainer={triggerNode => triggerNode.parentNode}
        filterOption={
          (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
        }
      >
        {
          list.map(configuration => (
            <Select.Option
              key={`${configuration.id}`}
              value={`${configuration.id}`}
            >
              {configuration.name}
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderDockerImageSelection = () => {
    const {disabled} = this.props;
    const {value} = this.state;
    return (
      <AddDockerRegistryControl
        disabled={disabled}
        docker={value}
        onChange={this.onChangeValue}
        showDelete={false}
        showError={false}
        style={{flex: 1}}
      />
    );
  };

  renderValueControl = () => {
    const {disabled} = this.props;
    const {operator, type, value} = this.state;
    if (
      !type ||
      !operator ||
      (/^(empty|not_empty)$/i.test(operator) && !/^run_parameter$/i.test(type))
    ) {
      return null;
    }
    if (/^run_owner$/i.test(type)) {
      return this.renderUsersSelection();
    } else if (/^run_owner_group$/i.test(type)) {
      return this.renderGroupSelection();
    } else if (/^(pipeline_id)$/i.test(type)) {
      return this.renderPipelineSelection();
    } else if (/^(configuration_id)$/i.test(type)) {
      return this.renderConfigurationSelection();
    } else if (/^run_parameter$/i.test(type)) {
      const {valueKey, valueValue} = this.state;
      const onChangeKey = e => {
        this.onChangeValue({[e.target.value || '']: (valueValue || '')});
      };
      const onChangeValue = e => {
        this.onChangeValue({[valueKey || '']: (e.target.value || '')});
      };
      const showValue = !/^(empty|not_empty)$/i.test(operator);
      return [
        <Input
          key="key"
          placeholder="Parameter name"
          disabled={disabled}
          value={valueKey}
          onChange={onChangeKey}
          style={{width: 200, marginRight: 5}}
        />,
        showValue && (
          <Input
            key="value"
            placeholder="Parameter value"
            disabled={disabled}
            value={valueValue}
            onChange={onChangeValue}
            style={{width: 200, marginRight: 5}}
          />
        )
      ].filter(Boolean);
    } else if (/^docker_image$/i.test(type)) {
      return this.renderDockerImageSelection();
    }
    return (
      <Input
        disabled={disabled}
        value={value}
        onChange={this.onChangeValue}
        style={{flex: 1}}
      />
    );
  };

  render () {
    const {className, disabled, style} = this.props;
    const {operator, type} = this.state;
    return (
      <div
        className={className}
        style={style}
      >
        <Select
          disabled={disabled}
          style={{width: 200}}
          placeholder="Select property"
          value={type}
          onChange={this.onChangeType}
          getPopupContainer={triggerNode => triggerNode.parentNode}
        >
          {
            types.map(o => (
              <Select.Option key={o.key} value={o.key}>
                {o.title}
              </Select.Option>
            ))
          }
        </Select>
        {
          type && (
            <Select
              disabled={disabled}
              style={{marginLeft: 5, marginRight: 5, width: 100}}
              placeholder="operator"
              value={operator}
              onChange={this.onChangeOperator}
              getPopupContainer={triggerNode => triggerNode.parentNode}
            >
              {
                operators.map(o => (
                  <Select.Option key={o.key} value={o.key}>
                    {o.title}
                  </Select.Option>
                ))
              }
            </Select>
          )
        }
        {this.renderValueControl()}
      </div>
    );
  }
}

FilterControl.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  filter: PropTypes.object,
  onChange: PropTypes.func,
  style: PropTypes.object
};

export {criteriaDescription, criteriaEqual, criteriaValid, mapCriteria};
export default inject('users', 'roles', 'pipelines', 'allConfigurations')(observer(FilterControl));
