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
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {Button, Row} from 'antd';
import EndpointInput from './EndpointInput';

@observer
export default class ToolEndpointsFormItem extends React.Component {

  static propTypes = {
    disabled: PropTypes.bool,
    value: PropTypes.array,
    onChange: PropTypes.func
  };

  state = {
    value: []
  };

  components = {};

  validate = () => {
    let result = true;
    for (let key in this.components) {
      if (this.components.hasOwnProperty(key) && this.components[key] && this.components[key].validate) {
        result = result && this.components[key].validate();
      }
    }
    return result;
  };

  onInitialize = (index, control) => {
    this.components[`control_${index}`] = control;
  };

  onChangeEndPoint = (index, value) => {
    if ((this.state.value || []).length <= index) {
      return;
    }
    const array = this.state.value;
    let isDefault = false;
    try {
      const json = JSON.parse(value || '');
      isDefault = `${json.isDefault}` === 'true';
    } catch (__) {}
    if (isDefault) {
      const length = array.length;
      for (let i = 0; i < length; i++) {
        let item = array[i];
        const newItem = {};
        try {
          const itemJson = JSON.parse(item || '');
          newItem.name = itemJson.name;
          newItem.isDefault = false;
          newItem.nginx = itemJson.nginx;
          array[i] = JSON.stringify(newItem);
        } catch (__) {}
      }
    }
    array[index] = value;
    this.setState({
      value: array
    }, () => {
      this.props.onChange && this.props.onChange(this.state.value);
    });
  };

  onAddEndpointClicked = () => {
    const array = this.state.value;
    array.push('');
    this.setState({
      value: array
    }, () => {
      this.props.onChange && this.props.onChange(this.state.value);
    });
  };

  onRemoveEndpointClicked = (index) => {
    const array = this.state.value;
    array.splice(index, 1);
    this.setState({
      value: array
    }, () => {
      this.props.onChange && this.props.onChange(this.state.value);
    });
  };

  render () {
    return (
      <div>
        {
          (this.state.value || []).map((value, index) => {
            return (
              <Row type="flex" align="middle" key={index} style={{marginBottom: 10}}>
                <EndpointInput
                  disabled={this.props.disabled}
                  ref={control => this.onInitialize(index, control)}
                  even={index % 2 === 0}
                  value={value}
                  onRemove={() => this.onRemoveEndpointClicked(index)}
                  onChange={(newValue) => this.onChangeEndPoint(index, newValue)} />
              </Row>
            );
          })
        }
        <Row type="flex" justify="center" style={{marginTop: 10}}>
          <Button
            disabled={this.props.disabled}
            onClick={this.onAddEndpointClicked}
            size="small">
            Add endpoint
          </Button>
        </Row>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if ((nextProps.value || []).join('|') !== (this.state.value || []).join('|')) {
      this.components = {};
      this.setState({
        value: nextProps.value
      }, this.validate);
    }
  }

  componentDidMount () {
    this.setState({
      value: this.props.value
    }, this.validate);
  }

}
