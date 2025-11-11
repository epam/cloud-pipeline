/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
  Input,
  Icon
} from 'antd';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import HiddenObjects from '../../../../../../../utils/hidden-objects';
import DockerImageBrowser from '../../../../../launch/dialogs/DockerImageBrowser';

@inject('dockerRegistries')
@HiddenObjects.injectToolsFilters
@observer
class WdlRuntimeDocker extends React.Component {
  state = {
    browser: false
  };

  @computed
  get registries () {
    const {
      dockerRegistries,
      hiddenToolsTreeFilter
    } = this.props;
    if (dockerRegistries.loaded) {
      return hiddenToolsTreeFilter(dockerRegistries.value).registries;
    }
    return [];
  }

  get dockerImage () {
    const {value} = this.props;
    const e = /^("(.+)"|'(.+)')$/i.exec(value);
    if (e) {
      return e[2] || e[3];
    }
    return undefined;
  }

  onOpenDockerBrowser = () => this.setState({
    browser: true
  });

  onCloseDockerBrowser = () => this.setState({
    browser: false
  });

  onPickDockerImage = (dockerImage) => {
    const {
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      onChange(dockerImage ? `"${dockerImage}"` : undefined);
    }
    this.onCloseDockerBrowser();
  }

  render () {
    const {
      className,
      style,
      value,
      onChange,
      disabled
    } = this.props;
    const {
      browser
    } = this.state;
    const onChangeHandler = (event) => {
      if (typeof onChange === 'function') {
        onChange(event.target.value);
      }
    };
    return (
      <Input
        className={className}
        style={style}
        disabled={disabled}
        value={value}
        onChange={onChangeHandler}
        addonAfter={(
          <div onClick={this.onOpenDockerBrowser}>
            <Icon type="tool" />
            <DockerImageBrowser
              onChange={this.onPickDockerImage}
              dockerImage={this.dockerImage}
              visible={browser}
              registries={this.registries}
              onCancel={this.onCloseDockerBrowser}
            />
          </div>
        )}
      />
    );
  }
}

WdlRuntimeDocker.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.string,
  onChange: PropTypes.func
};

export default WdlRuntimeDocker;
