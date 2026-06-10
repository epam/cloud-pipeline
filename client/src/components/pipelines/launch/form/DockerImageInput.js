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
import classNames from 'classnames';
import {
  observer,
  inject} from 'mobx-react';
import {computed, makeObservable} from 'mobx';
import {Input} from 'antd';
import {ToolOutlined} from '@ant-design/icons';
import DockerImageBrowser from '../dialogs/DockerImageBrowser';
import styles from './launch-form-addon-input.css';
import HiddenObjects from '../../../../utils/hidden-objects';

@inject('dockerRegistries')
@HiddenObjects.injectToolsFilters
@observer
export default class DockerImageInput extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    disabled: PropTypes.bool
  };

  state = {
    browserVisible: false
  };

  input;

  refInput = (input) => {
    this.input = input;
  };

  openBrowser = () => {
    if (this.input) {
      this.input.blur();
    }
    this.setState({
      browserVisible: true
    });
  };

  closeBrowser = () => {
    this.setState({
      browserVisible: false
    });
  };

  selectDockerImage = (image) => {
    this.closeBrowser();
    if (this.props.onChange) {
      this.props.onChange(image);
    }
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      registries: computed
    });
  }

  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return this.props.hiddenToolsTreeFilter(this.props.dockerRegistries.value)
        .registries;
    }
    return [];
  }

  render () {
    const disabled = this.props.disabled ||
      this.props.dockerRegistries.pending ||
      this.registries.length === 0;
    return (
      <div
        className={classNames(this.props.className, styles.launchFormAddonInput)}
        style={{width: '100%', minWidth: 0, maxWidth: '100%'}}>
        <Input.Group
          compact
          style={{display: 'flex', width: '100%', minWidth: 0, maxWidth: '100%'}}>
          <span className={classNames(styles.launchFormAddonInputAddon, 'cp-input-group-addon')}>
            <div
              className={classNames(
                styles.launchFormAddonInputAddonButton,
                {[styles.disabled]: disabled}
              )}
              onClick={disabled ? undefined : this.openBrowser}>
              <ToolOutlined />
            </div>
          </span>
          <Input
            id="docker-image-input"
            style={{flex: '1 1 0', minWidth: 0, width: 0, maxWidth: '100%'}}
            disabled={disabled}
            ref={this.refInput}
            onFocus={this.openBrowser}
            value={this.state.value || ''} />
        </Input.Group>
        {
          this.registries.length > 0
            ? (
              <DockerImageBrowser
                registries={this.registries}
                visible={this.state.browserVisible}
                onCancel={this.closeBrowser}
                onChange={this.selectDockerImage}
                dockerImage={this.state.value}
              />
            )
            : undefined
        }
      </div>
    );
  }

  UNSAFE_componentWillReceiveProps (nextProps) {
    if ('value' in nextProps) {
      const value = nextProps.value;
      this.setState({value});
    }
  }

  componentDidMount () {
    this.setState({value: this.props.value});
  }
}
