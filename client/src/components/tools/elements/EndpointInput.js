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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {
  Button,
  Col,
  Dropdown,
  Icon,
  Input,
  Menu,
  Row
} from 'antd';
import classNames from 'classnames';
import CodeEditor from '../../special/CodeEditor';
import styles from '../Tools.css';
import '../../../staticStyles/EndpointInput.css';

function validatePort (port) {
  if (!port) {
    return 'Port is required';
  } else if (isNaN(port) || +port <= 0) {
    return 'Port must be a positive number';
  }
  return null;
}

function validateName (name) {
  return null;
}

function validateAdditional (additional) {
  return null;
}

function getValidationResult (endpoint) {
  let name, port, additional;
  try {
    const json = JSON.parse(endpoint || '');
    name = json.name;
    if (json.nginx) {
      port = json.nginx.port;
      additional = json.nginx.additional;
    }
  } catch (__) {
  }
  return {
    port: validatePort(port),
    name: validateName(name),
    additional: validateAdditional(additional)
  };
}

export function getValidationError (endpoint) {
  const {
    port,
    name,
    additional
  } = getValidationResult(endpoint);
  return port || name || additional || undefined;
}

@inject('authenticatedUserInfo')
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

  @computed
  get additionalConfigurationEditable () {
    const {
      authenticatedUserInfo
    } = this.props;
    if (authenticatedUserInfo.loaded) {
      const {
        admin,
        roles = []
      } = authenticatedUserInfo.value;
      return admin || !!roles.find(o => /^ROLE_ADVANCED_USER$/i.test(o.name));
    }
    return false;
  }

  validate = () => {
    const validation = getValidationResult(this.state.value);
    this.setState({
      validation
    });
    return !validation.port && !validation.name && !validation.additional;
  };

  get name () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return json.name;
      } catch (__) {
      }
    }
    return undefined;
  }

  get port () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        if (json.nginx && json.nginx.port !== undefined) {
          return json.nginx.port;
        }
      } catch (__) {
      }
    }
    return undefined;
  }

  get isDefault () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return `${json.isDefault}` === 'true';
      } catch (__) {
      }
    }
    return false;
  }

  get sslBackend () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return `${json.sslBackend}` === 'true';
      } catch (__) {
      }
    }
    return false;
  }

  get sameTab () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return `${json.sameTab}` === 'true';
      } catch (__) {
      }
    }
    return false;
  }

  get customDNS () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        return `${json.customDNS}` === 'true';
      } catch (__) {}
    }
    return false;
  }

  get additional () {
    if (this.state.value) {
      try {
        const json = JSON.parse(this.state.value || '');
        if (json.nginx) {
          return json.nginx.additional;
        }
      } catch (__) {
      }
    }
    return undefined;
  }

  composeValue = (options = {}) => {
    let {
      name,
      port,
      additional,
      isDefault,
      sslBackend,
      customDNS,
      sameTab
    } = options;
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
    if (sslBackend === undefined || sslBackend === null) {
      sslBackend = this.sslBackend;
    }
    if (customDNS === undefined || customDNS === null) {
      customDNS = this.customDNS;
    }
    if (sameTab === undefined || sameTab === null) {
      sameTab = this.sameTab;
    }
    const value = {
      name,
      nginx: {
        port,
        additional
      },
      isDefault,
      sslBackend,
      customDNS,
      sameTab
    };
    let result = '';
    try {
      result = JSON.stringify(value);
    } catch (___) {
    }
    return result;
  };

  onChangeName = (e) => {
    const name = e.target.value;
    const value = this.composeValue({name});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangePort = (e) => {
    const port = e.target.value;
    const value = this.composeValue({port});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeDefault = (isDefault) => {
    const value = this.composeValue({isDefault});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeSSLBackend = (sslBackend) => {
    const value = this.composeValue({sslBackend});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeSameTab = (sameTab) => {
    const value = this.composeValue({sameTab});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  }

  onChangeCustomDNS = (customDNS) => {
    const value = this.composeValue({customDNS});
    this.setState({
      value
    }, () => {
      this.props.onChange && this.props.onChange(value);
      this.validate();
    });
  };

  onChangeAdditional = (additional) => {
    const value = this.composeValue({additional});
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
    const options = [];
    if (this.isDefault) {
      options.push('Default');
    }
    if (this.sslBackend) {
      options.push('SSL');
    }
    if (this.customDNS) {
      options.push('Sub-Domain');
    }
    if (this.sameTab) {
      options.push('Same Tab');
    }
    if (options.length === 0) {
      options.push('Configure');
    }
    const onChange = (opts) => {
      const {key} = opts;
      switch (key) {
        case 'isDefault':
          this.onChangeDefault(!this.isDefault);
          break;
        case 'sslBackend':
          this.onChangeSSLBackend(!this.sslBackend);
          break;
        case 'sameTab':
          this.onChangeSameTab(!this.sameTab);
          break;
        case 'customDNS':
          this.onChangeCustomDNS(!this.customDNS);
          break;
        default:
          break;
      }
    };
    const overlay = (
      <Menu
        onClick={onChange}
      >
        <Menu.Item key="isDefault">
          {this.isDefault ? (<Icon type="check" />) : undefined}
          <span style={{marginLeft: 5}}>Default</span>
        </Menu.Item>
        <Menu.Item key="sslBackend">
          {this.sslBackend ? (<Icon type="check" />) : undefined}
          <span style={{marginLeft: 5}}>SSL backend</span>
        </Menu.Item>
        <Menu.Item key="customDNS">
          {this.customDNS ? (<Icon type="check" />) : undefined}
          <span style={{marginLeft: 5}}>Use sub-domain</span>
        </Menu.Item>
        <Menu.Item key="sameTab">
          {this.sameTab ? (<Icon type="check" />) : undefined}
          <span style={{marginLeft: 5}}>Open in same tab</span>
        </Menu.Item>
      </Menu>
    );
    return (
      <div
        className={classNames({'cp-tool-add-endpoint': this.props.even})}
        style={{
          width: '100%',
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
                className={
                  classNames(
                    {
                      'cp-error': this.state.validation.port
                    }
                  )
                }
                style={{
                  width: 100,
                  margin: '0px 5px'
                }}
                size="small"
              />
            </Row>
            {
              this.state.validation.port &&
              <Row
                type="flex"
                className="cp-error"
                style={{
                  margin: 0,
                  padding: 0,
                  fontSize: 'x-small',
                  lineHeight: 'normal'
                }}>
                <span>
                  {this.state.validation.port}
                </span>
              </Row>
            }
          </Col>
          <Col style={{width: 190}}>
            <Row type="flex" align="middle">
              <span style={{fontWeight: 'bold'}}>Name:</span>
              <Input
                disabled={this.props.disabled}
                value={this.name}
                onChange={this.onChangeName}
                className={
                  classNames(
                    {
                      'cp-error': this.state.validation.name
                    }
                  )
                }
                style={{
                  flex: 1,
                  marginLeft: 5
                }}
                size="small"
              />
            </Row>
            {
              this.state.validation.name &&
              <Row
                type="flex"
                className="cp-error"
                style={{
                  margin: 0,
                  padding: 0,
                  fontSize: 'x-small',
                  lineHeight: 'normal'
                }}>
                <span>
                  {this.state.validation.name}
                </span>
              </Row>
            }
          </Col>
          <Col style={{paddingLeft: 5, flex: 1, textAlign: 'right'}}>
            <Dropdown
              overlay={overlay}
              trigger={['click']}
            >
              <a>
                {options.join(', ')}
                <Icon type="setting" style={{marginLeft: 2}} />
              </a>
            </Dropdown>
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
            readOnly={this.props.disabled || !this.additionalConfigurationEditable}
            ref={this.initializeEditor}
            placeholder={
              this.additionalConfigurationEditable
                ? 'Add any additional nginx configuration here'
                : 'You have no permissions to modify the additional nginx configuration'
            }
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
            className="cp-error"
            style={{
              margin: 0,
              padding: 0,
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
};
