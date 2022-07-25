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
import {
  Button,
  Icon
} from 'antd';
import classNames from 'classnames';
import {observer, inject} from 'mobx-react';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {
  getAllSkippedSystemParametersList
} from '../../../form/utilities/launch-cluster';
import Parameter from './parameter';
import SystemParametersBrowser from '../../SystemParametersBrowser';
import ParametersProvider, {injectParametersStore} from './store';
import styles from './parameters.css';

class Parameters extends React.Component {
  static Modes = {
    system: 'system',
    nonSystem: 'non-system'
  };

  state = {
    systemParameterBrowserVisible: false
  };

  get parameters () {
    const {
      parametersStore
    } = this.props;
    if (!parametersStore) {
      return [];
    }
    if (this.displaySystemParameters) {
      return parametersStore.systemParameters;
    }
    return parametersStore.nonSystemParameters;
  }

  get sections () {
    const filtered = this.parameters;
    if (this.displaySystemParameters && filtered.length > 0) {
      return [{system: true}];
    }
    if (this.displaySystemParameters) {
      return [];
    }
    return [...(
      new Set(
        filtered
          .filter(o => !o.system)
          .map(o => o.section)
      )
    )]
      .sort((a, b) => {
        if (!a) {
          return 1;
        }
        if (!b) {
          return -1;
        }
        return a < b ? -1 : 1;
      });
  }

  get displaySystemParameters () {
    const {
      mode = Parameters.Modes.nonSystem
    } = this.props;
    return mode === Parameters.Modes.system;
  }

  renderAddParameterButton = () => {
    const {
      disabled,
      editable,
      preferences,
      parametersStore
    } = this.props;
    if (!parametersStore) {
      return null;
    }
    const {
      systemParameterBrowserVisible
    } = this.state;
    const openSystemParametersBrowser = () => {
      this.setState({
        systemParameterBrowserVisible: true
      });
    };
    const closeSystemParametersBrowser = () => {
      this.setState({
        systemParameterBrowserVisible: false
      });
    };
    const onAddSystemParameters = (systemParameters) => {
      parametersStore.onAddParameter(
        ...systemParameters.map(o => ({
          value: o.defaultValue,
          description: o.description,
          name: o.name,
          type: o.type,
          system: true
        }))
      );
      this.setState({
        systemParameterBrowserVisible: false
      });
    };
    const onSelect = ({key}) => {
      if (key === 'system') {
        openSystemParametersBrowser();
        return;
      }
      parametersStore.onAddParameter({type: key});
    };
    let button;
    let systemParametersBrowser;
    if (this.displaySystemParameters) {
      systemParametersBrowser = (
        <SystemParametersBrowser
          visible={systemParameterBrowserVisible}
          onCancel={closeSystemParametersBrowser}
          onSave={onAddSystemParameters}
          notToShow={[
            ...parametersStore.parameters.map(o => o.name),
            ...getAllSkippedSystemParametersList(preferences)
          ]}
        />
      );
      button = (
        <Button
          id="add-system-parameter-button"
          disabled={disabled || !editable}
          onClick={openSystemParametersBrowser}
        >
          Add system parameter
        </Button>
      );
    } else {
      const menu = (
        <div>
          <Menu
            selectedKeys={[]}
            onSelect={onSelect}
          >
            <MenuItem id="add-string-parameter" key="string">String parameter</MenuItem>
            <MenuItem id="add-boolean-parameter" key="boolean">Boolean parameter</MenuItem>
            <MenuItem id="add-path-parameter" key="path">Path parameter</MenuItem>
            <MenuItem id="add-input-parameter" key="input">Input path parameter</MenuItem>
            <MenuItem id="add-output-parameter" key="output">Output path parameter</MenuItem>
            <MenuItem id="add-common-parameter" key="common">Common path parameter</MenuItem>
          </Menu>
        </div>
      );
      button = (
        <Button.Group>
          <Button
            id="add-parameter-button"
            disabled={disabled || !editable}
            onClick={() => onSelect({key: 'string'})}
          >
            Add parameter
          </Button>
          <Dropdown
            overlay={menu}
            trigger={['click']}
            placement="bottomRight"
          >
            <Button
              id="add-typed-parameter-button"
              disabled={disabled || !editable}
            >
              <Icon type="down" />
            </Button>
          </Dropdown>
        </Button.Group>
      );
    }
    return (
      <div
        className={
          styles.addParameterRow
        }
      >
        {button}
        {systemParametersBrowser}
      </div>
    );
  };

  renderSectionHeader = (section, index, sections = []) => {
    const line = (
      <div
        className={
          classNames(
            'cp-divider',
            'horizontal',
            styles.line
          )
        }
      >
        {'\u00A0'}
      </div>
    );
    if (section && section.system) {
      return (
        <div
          className={styles.sectionHeader}
        >
          {line}
          <span>System parameters</span>
          {line}
        </div>
      );
    }
    if (
      sections
        .filter(o => !(o && o.system))
        .length <= 1
    ) {
      return null;
    }
    return (
      <div
        className={styles.sectionHeader}
      >
        {line}
        <span>{section || 'Other'}</span>
        {line}
      </div>
    );
  };

  renderSection = (section, index, sections = []) => {
    const {
      larger,
      disabled,
      editable,
      mode,
      parametersStore
    } = this.props;
    if (!parametersStore) {
      return null;
    }
    const parameters = this.parameters;
    const filtered = parameters.filter(parameter => {
      if (section && section.system) {
        return parameter.system;
      }
      return parameter.section === section;
    });
    return (
      <div
        key={`section-${index}`}
        className={styles.section}
      >
        {this.renderSectionHeader(section, index, sections)}
        {
          filtered.map((parameter, index) => (
            <Parameter
              id={`parameter-${index}`}
              key={`parameter-${index}`}
              parameter={parameter}
              larger={larger}
              disabled={disabled}
              editable={editable}
              mode={mode}
              onChange={parametersStore.onChangeParameter(parameter)}
              onRemove={parametersStore.onRemoveParameter(parameter)}
            />
          ))
        }
      </div>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={className}
        style={style}
      >
        {this.sections.map(this.renderSection)}
        {this.renderAddParameterButton()}
      </div>
    );
  }
}

Parameters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  editable: PropTypes.bool,
  larger: PropTypes.bool,
  mode: PropTypes.oneOf([Parameters.Modes.system, Parameters.Modes.nonSystem])
};

Parameters.defaultProps = {
  mode: Parameters.Modes.nonSystem,
  editable: true
};

Parameters.Provider = ParametersProvider;

export default inject('preferences')(
  injectParametersStore(
    observer(Parameters)
  )
);
