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
import {computed} from 'mobx';
import {Button, Dropdown, Icon, Input, Menu, Popover, Row} from 'antd';

const CloneOption = {
  https: 'https',
  ssh: 'ssh'
};

const CLOSE_POPOVER_DELAY_MS = 200;

export default @observer
class GitRepositoryControl extends React.Component {
  static propTypes = {
    cloneType: PropTypes.oneOf([CloneOption.https, CloneOption.ssh]),
    https: PropTypes.string,
    overlayClassName: PropTypes.string,
    ssh: PropTypes.string
  };
  static defaultProps = {
    cloneType: CloneOption.https
  };
  state = {
    cloneType: undefined,
    visible: false
  };
  componentWillReceiveProps (nextProps) {
    if (this.props.ssh !== nextProps.ssh || this.props.https !== nextProps.https) {
      this.setState({
        cloneType: undefined
      });
    }
  }

  @computed
  get availableCloneOptions () {
    return [
      this.props.https ? CloneOption.https : undefined,
      this.props.ssh ? CloneOption.ssh : undefined
    ].filter(Boolean);
  };
  @computed
  get defaultCloneOption () {
    return this.availableCloneOptions[0];
  };
  getGitRepositoryPopoverTitle = () => {
    const cloneType = this.state.cloneType || this.defaultCloneOption;
    if (!cloneType) {
      return <b>Git repository</b>;
    }
    const onSelectOption = ({key}) => {
      this.setState({
        cloneType: key,
        preventPopoverFromClosing: true
      });
    };
    const menu = (
      <Menu onClick={onSelectOption}>
        {this.availableCloneOptions.map(o => (
          <Menu.Item key={o}>{o.toUpperCase()}</Menu.Item>
        ))}
      </Menu>
    );
    return (
      <Row type="flex" align="middle">
        <b style={{marginRight: 5}}>Clone repository via</b>
        <Dropdown overlay={menu}>
          <a style={{lineHeight: 1}}>
            <b>{cloneType.toUpperCase()}<Icon type="down" /></b>
          </a>
        </Dropdown>
      </Row>
    );
  };
  getGitRepositoryPopoverContent = () => {
    const cloneType = this.state.cloneType || this.defaultCloneOption;
    const currentValue = this.props[cloneType];
    return (
      <Row className={this.props.overlayClassName}>
        <Input
          readOnly
          value={currentValue} />
      </Row>
    );
  };
  onDropdownVisibilityChanged = (visibility) => {
    if (!visibility && this.closePopoverTimeout) {
      clearTimeout(this.closePopoverTimeout);
    }
    if (visibility) {
      this.setState({
        visible: visibility
      });
    } else {
      this.closePopoverTimeout = setTimeout(() => {
        if (this.state.preventPopoverFromClosing) {
          this.setState({
            preventPopoverFromClosing: false
          });
        } else {
          this.setState({
            preventPopoverFromClosing: false,
            visible: visibility
          });
        }
      }, CLOSE_POPOVER_DELAY_MS);
    }
  };
  render () {
    if (this.availableCloneOptions.length === 0) {
      return null;
    }
    return (
      <Popover
        overlayClassName="git-repository-popover"
        title={this.getGitRepositoryPopoverTitle()}
        content={this.getGitRepositoryPopoverContent()}
        visible={this.state.visible}
        onVisibleChange={this.onDropdownVisibilityChanged}
        trigger={['click']}
        placement="bottomLeft">
        <Button
          id="pipeline-repository-button"
          size="small"
          style={{lineHeight: 1}}>
          GIT REPOSITORY
        </Button>
      </Popover>
    );
  }
}
