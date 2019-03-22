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
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Checkbox, Col, Input, Row} from 'antd';
import CodeEditor from '../../special/CodeEditor';
import styles from '../Tools.css';

import '../../../staticStyles/EndpointInput.css';

@observer
export default class EndpointInput extends React.Component {

  static propTypes = {
    disabled: PropTypes.bool,
    even: PropTypes.bool,
    value: PropTypes.string,
    onChange: PropTypes.func,
    onRemove: PropTypes.func
  };

  state = {
    value: '',
    validation: {}
  };

  validatePort = (port) => {
    if (!port) {
      return 'Port is required';
    } else if (isNaN(port) || +port <= 0) {
      return 'Port must be a positive number';
    }
    return null;
  };

  validateName = (name) => {
    return null;
  };

  validateAdditional = (additional) => {
    return null;
  };

  validate = () => {
    let port, name, additional;
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        name = json.name;
        if (json.nginx) {
          port = json.nginx.port;
          additional = json.nginx.additional;
        }
      } catch (__) {}
    }
    const validation = {
      port: this.validatePort(port),
      name: this.validateName(name),
      additional: this.validateAdditional(additional)
    };
    this.setState({
      validation
    });
    return !validation.port && !validation.name && !validation.additional;
  };

  @computed
  get name () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return json.name;
      } catch (__) {}
    }
    return undefined;
  }

  @computed
  get port () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        if (json.nginx && json.nginx.port !== undefined) {
          return json.nginx.port;
        }
      } catch (__) {}
    }
    return undefined;
  }

  @computed
  get isDefault () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return `${json.isDefault}` === 'true';
      } catch (__) {}
    }
    return false;
  }

  @computed
  get additional () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        if (json.nginx) {
          return json.nginx.additional;
        }
      } catch (__) {}
    }
    return undefined;
  }

  composeValue = (name, port, additional, isDefault) => {
    if (name === undefined || name === null) {
      name = this.name;
    }
    if (port === undefined || port === null) {
      port = this.port;
    }
    if (additional === undefined || additional === null) {
      additional = this.additional;
    }
    if (isDefault === undefined || isDefault === null) {
      isDefault = this.isDefault;
    }
    const value = {
      name,
      nginx: {
        port,
        additional
      },
      isDefault
    };
    return JSON.stringify(value);
  };

  onChangeName = (e) => {
    const name = e.target.value;
    const value = this.composeValue(name, null, null, null);
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangePort = (e) => {
    const port = e.target.value;
    const value = this.composeValue(null, port, null, null);
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeDefault = (e) => {
    const checked = e.target.checked;
    const value = this.composeValue(null, null, null, checked);
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeAdditional = (additional) => {
    const value = this.composeValue(null, null, additional, null);
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  render () {
    return (
      <div
        style={{
          width: '100%',
          backgroundColor: this.props.even ? '#fafafa' : undefined,
          padding: 5
        }}>
        <Row type="flex" align="top">
          <Col>
            <Row type="flex" align="middle">
              <span style={{fontWeight: 'bold'}}>Port:</span>
              <Input
                disabled={this.props.disabled}
                value={this.port}
                onChange={this.onChangePort}
                style={{
                  width: 200,
                  margin: '0px 5px',
                  border: this.state.validation.port ? '1px solid red' : undefined
                }}
                size="small" />
            </Row>
            {
              this.state.validation.port &&
              <Row
                type="flex"
                style={{
                  margin: 0,
                  padding: 0,
                  color: 'red',
                  fontSize: 'x-small',
                  lineHeight: 'normal'
                }}>
                <span>
                  {this.state.validation.port}
                </span>
              </Row>
            }
          </Col>
          <Col style={{flex: 1}}>
            <Row type="flex" align="middle">
              <span>Name:</span>
              <Input
                disabled={this.props.disabled}
                value={this.name}
                onChange={this.onChangeName}
                style={{
                  flex: 1,
                  marginLeft: 5,
                  border: this.state.validation.name ? '1px solid red' : undefined
                }}
                size="small" />
            </Row>
            {
              this.state.validation.name &&
              <Row
                type="flex"
                style={{
                  margin: 0,
                  padding: 0,
                  color: 'red',
                  fontSize: 'x-small',
                  lineHeight: 'normal'
                }}>
                <span>
                  {this.state.validation.name}
                </span>
              </Row>
            }
          </Col>
          <Col style={{paddingLeft: 5}}>
            <Checkbox checked={this.isDefault} onChange={this.onChangeDefault}>
              Is default
            </Checkbox>
          </Col>
          <Col style={{paddingLeft: 5}}>
            <Row type="flex" align="middle" style={{height: 31}}>
              <Button
                disabled={this.props.disabled}
                onClick={e => this.props.onRemove && this.props.onRemove()}
                size="small"
                type="danger">
                Delete
              </Button>
            </Row>
          </Col>
        </Row>
        <Row type="flex" className={styles.endpointCodeEditorContainer}>
          <CodeEditor
            readOnly={this.props.disabled}
            ref={this.initializeEditor}
            placeholder="Add any additional nginx configuration here"
            lineNumbers={false}
            className={styles.endpointCodeEditor}
            language="shell"
            onChange={this.onChangeAdditional}
            lineWrapping={false}
            defaultCode={this.additional}
          />
        </Row>
        {
          this.state.validation.additional &&
          <Row
            type="flex"
            style={{
              margin: 0,
              padding: 0,
              color: 'red',
              fontSize: 'x-small',
              lineHeight: 'normal'
            }}>
            <span>
              {this.state.validation.additional}
            </span>
          </Row>
        }
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.value !== this.state.value) {
      this.editor && this.editor.clear();
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
