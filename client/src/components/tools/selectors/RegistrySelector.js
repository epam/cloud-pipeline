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
import {observer} from 'mobx-react';
import {Row, Button, Dropdown} from 'antd';
import styles from './Selectors.css';
import compareArrays from '../../../utils/compareArrays';
import registryName from '../registryName';

@observer
export default class RegistrySelector extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    registries: PropTypes.array,
    emptyValueMessage: PropTypes.string,
    disabled: PropTypes.bool
  };

  state = {
    value: null
  };

  onSelectRegistry = (path) => {
    this.setState({
      value: path
    });
    if (this.props.onChange) {
      this.props.onChange(path);
    }
  };

  get currentRegistry () {
    return this.state.value ? this.props.registries.filter(r => r.path === this.state.value)[0] : null;
  }

  render () {
    if (this.props.disabled || this.props.registries.filter(r => !this.currentRegistry || r.id !== this.currentRegistry.id).length === 0) {
      return (
        <Button size="small" style={{border: 'none', fontWeight: 'bold', backgroundColor: 'transparent'}} onClick={null}>
          {
            this.currentRegistry
              ? registryName(this.currentRegistry)
              : this.props.emptyValueMessage || this.state.value || 'Unknown registry'
          }
        </Button>
      );
    }
    return (
      <Dropdown
        overlay={
          <div className={styles.navigationDropdownContainer}>
            {
              this.props.registries.filter(r => !this.currentRegistry || r.id !== this.currentRegistry.id).map(registry => {
                return (
                  <Row key={registry.id} type="flex">
                    <Button
                      style={{textAlign: 'left', width: '100%', border: 'none'}}
                      onClick={() => this.onSelectRegistry(registry.path)}>
                      {registryName(registry)}
                    </Button>
                  </Row>
                );
              })
            }
          </div>
        }>
        <Button size="small" style={{border: 'none', fontWeight: 'bold', backgroundColor: 'transparent'}}>
          {this.currentRegistry ? registryName(this.currentRegistry) : this.state.value || 'Unknown registry'}
        </Button>
      </Dropdown>
    );
  }

  updateState = (props) => {
    props = props || this.props;
    if (props.value && props.value.length) {
      this.onSelectRegistry(props.value);
    } else if (props.registries && props.registries.length > 0) {
      this.onSelectRegistry(props.registries[0].path);
    } else {
      this.onSelectRegistry(null);
    }
  };

  componentWillReceiveProps (nextProps) {
    const registriesAreEquals = (reg1, reg2) =>
    reg1.path === reg2.path && reg1.description === reg2.description && reg1.id === reg2.id;
    if (this.props.value !== nextProps.value ||
      !compareArrays(this.props.registries, nextProps.registries, registriesAreEquals)) {
      this.updateState(nextProps);
    }
  }

  componentDidMount () {
    this.updateState();
  }
}
