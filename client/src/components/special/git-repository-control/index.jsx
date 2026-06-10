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
import {makeObservable} from 'mobx';
import {Button, Dropdown, Input, Popover, Row} from 'antd';
import {DownOutlined} from '@ant-design/icons';

const CloneOption = {
  https: 'https',
  ssh: 'ssh',
};

const CLOSE_POPOVER_DELAY_MS = 200;

const RepositoryTypes = {
  GitLab: 'GITLAB',
  GitHub: 'GITHUB',
  GitHubApp: 'GITHUB_APP',
  BitBucket: 'BITBUCKET',
  ButBucketCloud: 'BITBUCKET_CLOUD',
  AzureDevOps: 'AZURE_DEVOPS',
};

const availableRepositoryTypes = [
  RepositoryTypes.GitLab,
  RepositoryTypes.GitHub,
  RepositoryTypes.BitBucket,
  RepositoryTypes.ButBucketCloud,
  RepositoryTypes.AzureDevOps,
];

const RepositoryTypeNames = {
  [RepositoryTypes.GitLab]: 'GitLab',
  [RepositoryTypes.GitHub]: 'GitHub',
  [RepositoryTypes.GitHubApp]: 'GitHub App',
  [RepositoryTypes.BitBucket]: 'BitBucket',
  [RepositoryTypes.ButBucketCloud]: 'BitBucket Cloud',
  [RepositoryTypes.AzureDevOps]: 'Azure DevOps',
};

function normalizeRepositoryType(repositoryType) {
  if (repositoryType === RepositoryTypes.GitHubApp) {
    return RepositoryTypes.GitHub;
  }
  return repositoryType;
}

export {RepositoryTypes, RepositoryTypeNames, availableRepositoryTypes, normalizeRepositoryType};

export default
@observer
class GitRepositoryControl extends React.Component {
  static propTypes = {
    cloneType: PropTypes.oneOf([CloneOption.https, CloneOption.ssh]),
    https: PropTypes.string,
    overlayClassName: PropTypes.string,
    ssh: PropTypes.string,
    repositoryType: PropTypes.string,
  };

  static defaultProps = {
    cloneType: CloneOption.https,
  };

  state = {
    cloneType: undefined,
    visible: false,
  };

  constructor(props) {
    super(props);
    makeObservable(this, {});
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    if (this.props.ssh !== nextProps.ssh || this.props.https !== nextProps.https) {
      this.setState({
        cloneType: undefined,
      });
    }
  }

  get availableCloneOptions() {
    return [
      this.props.https ? CloneOption.https : undefined,
      this.props.ssh ? CloneOption.ssh : undefined,
    ].filter(Boolean);
  }

  get defaultCloneOption() {
    return this.availableCloneOptions[0];
  }

  getGitRepositoryPopoverTitle = () => {
    const cloneType = this.state.cloneType || this.defaultCloneOption;
    if (!cloneType) {
      return <b>Git repository</b>;
    }
    const onSelectOption = ({key}) => {
      this.setState({
        cloneType: key,
        preventPopoverFromClosing: true,
      });
    };
    const menuItems = this.availableCloneOptions.map((o) => ({
      key: o,
      label: o.toUpperCase(),
    }));
    return (
      <Row type="flex" align="middle">
        <b style={{marginRight: 5}}>Clone repository via</b>
        <Dropdown menu={{items: menuItems, onClick: onSelectOption}}>
          <a style={{lineHeight: 1}}>
            <b>
              {cloneType.toUpperCase()}
              <DownOutlined />
            </b>
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
        <Input readOnly value={currentValue} />
      </Row>
    );
  };

  onDropdownVisibilityChanged = (visibility) => {
    if (!visibility && this.closePopoverTimeout) {
      clearTimeout(this.closePopoverTimeout);
    }
    if (visibility) {
      this.setState({
        visible: visibility,
      });
    } else {
      this.closePopoverTimeout = setTimeout(() => {
        if (this.state.preventPopoverFromClosing) {
          this.setState({
            preventPopoverFromClosing: false,
          });
        } else {
          this.setState({
            preventPopoverFromClosing: false,
            visible: visibility,
          });
        }
      }, CLOSE_POPOVER_DELAY_MS);
    }
  };

  render() {
    if (this.availableCloneOptions.length === 0) {
      return null;
    }
    const {repositoryType} = this.props;
    return (
      <Popover
        classNames={{root: 'git-repository-popover'}}
        title={this.getGitRepositoryPopoverTitle()}
        content={this.getGitRepositoryPopoverContent()}
        open={this.state.visible}
        onOpenChange={this.onDropdownVisibilityChanged}
        trigger={['click']}
        placement="bottomLeft"
      >
        <Button id="pipeline-repository-button" size="small" style={{lineHeight: 1}}>
          {repositoryType && (
            <span style={{textTransform: 'uppercase', marginRight: 5}}>
              {RepositoryTypeNames[normalizeRepositoryType(repositoryType)]}
            </span>
          )}
          <span>REPOSITORY</span>
        </Button>
      </Popover>
    );
  }
}
