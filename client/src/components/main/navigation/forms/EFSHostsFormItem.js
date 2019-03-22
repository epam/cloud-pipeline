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
import {Button, Icon, Input, Row} from 'antd';

@observer
export default class EFSHostsFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
    onChange: PropTypes.func
  };

  state = {
    value: []
  };

  onChange = (index) => (e) => {
    const {value} = this.state;
    value[index] = e.target.value;
    this.setState({
      value
    }, () => this.props.onChange && this.props.onChange(this.state.value));
  };

  onRemove = (index) => () => {
    const {value} = this.state;
    value.splice(index, 1);
    this.setState({
      value
    }, () => this.props.onChange && this.props.onChange(this.state.value));
  };

  onAdd = () => {
    const {value} = this.state;
    value.push('');
    this.setState({
      value
    }, () => this.props.onChange && this.props.onChange(this.state.value));
  };

  render () {
    return (
      <div style={{width: '100%', marginBottom: 10}}>
        {
          this.state.value.map((v, i) => {
            return (
              <Row type="flex" key={i} align="middle" style={{marginTop: 5}}>
                <Input
                  style={{flex: 1}}
                  size="small"
                  value={v}
                  onChange={this.onChange(i)} />
                <Button
                  onClick={this.onRemove(i)}
                  size="small"
                  style={{marginLeft: 5}}>
                  <Icon type="close" />
                </Button>
              </Row>
            );
          })
        }
        <Row type="flex" justify="start" align="middle">
          <Button
            onClick={this.onAdd}
            size="small"
            style={{marginTop: 5}}>
            <Icon type="plus" />Add
          </Button>
        </Row>
      </div>
    );
  }

  updateState = (props) => {
    this.setState({
      value: (props.value || []).map(h => h)
    });
  };

  componentWillReceiveProps (nextProps) {
    if (this.props.value !== nextProps.value) {
      this.updateState(nextProps);
    }
  }

  componentDidMount () {
    this.updateState(this.props);
  }
}
