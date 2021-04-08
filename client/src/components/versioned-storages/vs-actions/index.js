/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {findDOMNode} from 'react-dom';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import classNames from 'classnames';
import {
  Dropdown,
  Icon,
  Menu, message
} from 'antd';
import VSBrowseDialog from '../vs-browse-dialog';
import GitDiffModal from './components/diff/modal';
import VSList from '../../../models/versioned-storage/list';
import styles from './vs-actions.css';
import '../../../staticStyles/vs-actions-dropdown.css';
import VSClone from '../../../models/versioned-storage/clone';
import VSCommit from '../../../models/versioned-storage/commit';
import VSDiff from '../../../models/versioned-storage/diff';
import VSFetch from '../../../models/versioned-storage/fetch';
import VSTaskStatus from '../../../models/versioned-storage/status';
import {GitCommitDialog} from './components';

const SUBMENU_POSITION = {
  right: 'right',
  left: 'left'
};

class VSActions extends React.Component {
  state = {
    dropDownVisible: false,
    vsBrowserVisible: false,
    subMenuPosition: SUBMENU_POSITION.right,
    gitCommit: undefined,
    gitDiff: undefined
  };

  menuContainerRef;

  @observable vsList;
  statuses = [];

  @computed
  get isDtsEnvironment () {
    const {run} = this.props;
    return run &&
      run.executionPreferences &&
      run.executionPreferences.environment === 'DTS';
  }

  @computed
  get fsBrowserAvailable () {
    const {run} = this.props;
    return run &&
      run.initialized &&
      run.podIP &&
      /^running$/i.test(run.status) &&
      !this.isDtsEnvironment;
  }

  @computed
  get repositories () {
    if (this.vsList && this.vsList.loaded) {
      return Array.from(this.vsList.value || []);
    }
    return [];
  }

  componentWillUnmount () {
    this.vsTaskStatus && this.vsTaskStatus.abort();
    this.statuses.forEach(operation => operation.abort());
    delete this.statuses;
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.run?.id !== this.props.run?.id) {
      this.statuses.forEach(operation => operation.abort());
    }
    if (prevState.dropDownVisible !== this.state.dropDownVisible && this.state.dropDownVisible) {
      this.refresh(prevProps.run?.id !== this.props.run?.id);
    }
  }

  pushOperation = (operation, promise) => {
    this.statuses.push(operation);
    (promise || operation.fetchUntilDone())
      .catch(console.error)
      .then(() => {
        const index = this.statuses.indexOf(operation);
        if (index >= 0) {
          this.statuses.splice(index, 1);
        }
      });
  }

  openVSBrowser = () => {
    this.setState({vsBrowserVisible: true});
  };

  closeVSBrowser = () => {
    this.setState({vsBrowserVisible: false});
  };

  performRequestWithStatus = (request, resolve, hideMessage) => {
    request
      .send()
      .then(() => {
        if (request.error) {
          message.error(request.error, 5);
        } else {
          const {task} = request.value;
          const vsTaskStatus = new VSTaskStatus(this.props.run?.id, task);
          const promise = vsTaskStatus.fetchUntilDone();
          this.pushOperation(vsTaskStatus, promise);
          return promise;
        }
      })
      .then(() => this.refresh(true))
      .catch(e => {
        message.error(e.message, 5);
        console.error(e);
      })
      .then(() => {
        hideMessage && hideMessage();
      })
      .then(() => resolve());
  };

  onSelectVS = (versionedStorage) => {
    return new Promise((resolve) => {
      this.closeVSBrowser();
      const {id, commitId, version, name} = versionedStorage;
      const hide = message.loading((
        <span>
          Cloning <b>{name}</b> storage (version <b>{version?.name || commitId}</b>)...
        </span>
      ), 0);
      this.performRequestWithStatus(
        new VSClone(this.props.run?.id, id, commitId),
        resolve,
        hide
      );
    });
  }

  onFetchVS = (versionedStorage) => {
    return new Promise((resolve) => {
      const {id, name} = versionedStorage;
      const hide = message.loading((
        <span>
          Refreshing <b>{name}</b> storage...
        </span>
      ), 0);
      this.performRequestWithStatus(
        new VSFetch(this.props.run?.id, id),
        resolve,
        hide
      );
    });
  };

  getVSDiff = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve([]);
    }
    return new Promise((resolve) => {
      const request = new VSDiff(this.props.run?.id, versionedStorage?.id);
      request
        .fetch()
        .then(() => {
          if (request.error || !request.loaded) {
            message.error(request.error || 'Error fetching storage diff', 5);
            resolve([]);
          } else {
            resolve(Array.from(request.value || []));
          }
        })
        .catch(e => {
          message.error(e.message, 5);
          resolve([]);
        });
    });
  };

  showCommitDialog = (versionedStorage, diff) => {
    if (versionedStorage && diff) {
      this.setState({gitCommit: {
        storage: versionedStorage,
        files: diff
      }});
    }
  };

  closeCommitDialog = () => this.setState({gitCommit: undefined});

  onCommitVS = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve();
    }
    const hide = message.loading((
      <span>
        Fetching <b>{versionedStorage.name}</b> diff...
      </span>
    ), 0);
    return new Promise((resolve) => {
      this.getVSDiff(versionedStorage)
        .then(diff => {
          hide();
          if (!diff || !diff.length) {
            message.info('Nothing to save', 5);
          } else {
            this.showCommitDialog(versionedStorage, diff);
          }
        })
        .then(() => resolve());
    });
  };

  doCommit = (versionedStorage, message) => {
    // todo: uncomment code below when GitCommitDialog will be ready and remove setTimeout
    return new Promise((resolve) => {
      // const {id, name} = versionedStorage;
      // const hide = message.loading((
      //   <span>
      //     Saving changes for the <b>{name}</b> storage...
      //   </span>
      // ), 0);
      // this.performRequestWithStatus(
      //   new VSCommit(this.props.run?.id, id, message),
      //   resolve,
      //   hide
      // );
      setTimeout(() => resolve(this.closeCommitDialog()), 2000);
    });
  };

  onDiffVS = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve();
    }
    const hide = message.loading((
      <span>
        Fetching <b>{versionedStorage.name}</b> diff...
      </span>
    ), 0);
    return new Promise((resolve) => {
      this.getVSDiff(versionedStorage)
        .then(diff => {
          hide();
          if (!diff || !diff.length) {
            message.info(
              (
                <span>
                  There are no modified files for <b>{versionedStorage.name}</b> storage
                </span>
              ),
              5
            );
          } else {
            this.setState({
              gitDiff: {
                storage: versionedStorage,
                files: diff
              }
            });
          }
        })
        .then(() => resolve());
    });
  };

  closeGitDiffModal = () => {
    this.setState({
      gitDiff: undefined
    });
  };

  refresh = (force = false) => {
    const {run} = this.props;
    if (!run || !this.fsBrowserAvailable) {
      if (this.vsList) {
        this.vsList.initialized = false;
      }
      return Promise.resolve();
    }
    if (!this.vsList || (this.vsList.runId !== run.id)) {
      this.vsList = new VSList(run.id);
    }
    if (force || this.vsList.initialized !== run.initialized) {
      return this.vsList.fetch();
    }
    return this.vsList.fetchIfNeededOrWait();
  };

  onDropDownVisibilityChange = (visible) => {
    const {onDropDownVisibleChange} = this.props;
    this.setState({
      dropDownVisible: visible
    }, () => {
      onDropDownVisibleChange && onDropDownVisibleChange(this.state.dropDownVisible);
    });
  };

  setSubMenuPosition = () => {
    const {subMenuPosition} = this.state;
    if (!this.menuContainerRef) {
      return;
    }
    const menuNode = findDOMNode(this.menuContainerRef);
    if (menuNode && menuNode instanceof HTMLElement) {
      const menuRect = menuNode.getBoundingClientRect();
      const padding = 25;
      const availableSpace = window.innerWidth - menuRect.right;
      const spaceNeeded = padding + menuRect.width;
      const preferredPosition = availableSpace > spaceNeeded
        ? SUBMENU_POSITION.right
        : SUBMENU_POSITION.left;
      if (preferredPosition !== subMenuPosition) {
        this.setState({subMenuPosition: preferredPosition});
      }
    }
  }

  renderOverlay = () => {
    const menuItems = [];
    let onChange;
    if (!this.vsList || (!this.vsList.loaded && this.vsList.pending)) {
      menuItems.push((
        <Menu.Item disabled key="loading">
          <Icon type="loading" /> Fetching versioned storage info...
        </Menu.Item>
      ));
    } else if (this.vsList.error) {
      menuItems.push((
        <Menu.Item disabled key="error">
          <i>{this.vsList.error}</i>
        </Menu.Item>
      ));
    } else if (!this.vsList.loaded) {
      menuItems.push((
        <Menu.Item disabled key="error">
          <i>Error fetching versioned storages</i>
        </Menu.Item>
      ));
    } else {
      const storages = this.repositories;
      menuItems.push((
        <Menu.Item key="clone">
          <Icon type="cloud-download-o" /> Clone
        </Menu.Item>
      ));
      if (storages.length > 0) {
        menuItems.push((<Menu.Divider key="clone-divider" />));
      }
      storages.forEach((storage, index, array) => {
        const Container = array.length === 1 ? Menu.ItemGroup : Menu.SubMenu;
        menuItems.push((
          <Container
            key={`-${storage.id}`}
            title={storage.name}
            ref={(el) => { this.menuContainerRef = el; }}
          >
            <Menu.Item
              key={`diff-${storage.id}`}
            >
              <Icon type="exception" /> Diff
            </Menu.Item>
            <Menu.Item
              key={`save-${storage.id}`}
              disabled={storage.detached}
            >
              <Icon type="save" /> Save
            </Menu.Item>
            <Menu.Item
              key={`refresh-${storage.id}`}
            >
              <Icon type="sync" /> Refresh
            </Menu.Item>
          </Container>
        ));
      });
      onChange = ({key}) => {
        const [action, storageId] = key.split('-');
        const storage = storages.find(s => +(s.id) === +(storageId));
        switch (action) {
          case 'clone':
            this.openVSBrowser();
            break;
          case 'refresh':
            if (storage) {
              this.onFetchVS(storage);
            }
            break;
          case 'save':
            if (storage) {
              this.onCommitVS(storage);
            }
            break;
          case 'diff':
            if (storage) {
              this.onDiffVS(storage);
            }
            break;
        }
        this.setState({dropDownVisible: false});
      };
    }
    const {subMenuPosition} = this.state;
    return (
      <Menu
        className={
          classNames(
            styles.menu,
            'vs-actions-dropdown',
            `vs-actions-dropdown-${subMenuPosition}`
          )
        }
        openTransition="none"
        onClick={onChange}
        onOpenChange={this.setSubMenuPosition}
      >
        {menuItems}
      </Menu>
    );
  };

  render () {
    const {
      children,
      placement = 'bottomRight',
      getPopupContainer,
      trigger,
      showDownIcon,
      run
    } = this.props;
    const {
      dropDownVisible,
      gitDiff,
      gitCommit
    } = this.state;
    if (!this.fsBrowserAvailable) {
      return null;
    }
    return (
      <Dropdown
        overlay={this.renderOverlay()}
        visible={dropDownVisible}
        onVisibleChange={this.onDropDownVisibilityChange}
        trigger={trigger}
        placement={placement}
        getPopupContainer={getPopupContainer}
      >
        <a onClick={e => e.stopPropagation()}>
          {children}
          {showDownIcon && (<Icon type="down" />)}
          <VSBrowseDialog
            visible={this.state.vsBrowserVisible}
            onClose={this.closeVSBrowser}
            onSelect={this.onSelectVS}
            repositories={this.repositories}
          />
          <GitDiffModal
            visible={!!gitDiff}
            run={run?.id}
            storage={gitDiff?.storage}
            fileDiffs={gitDiff?.files}
            onClose={this.closeGitDiffModal}
          />
          {gitCommit && (
            <GitCommitDialog
              visible={!!gitCommit}
              run={run?.id}
              onCommit={this.doCommit}
              onCancel={this.closeCommitDialog}
              storage={gitCommit?.storage}
              files={gitCommit?.files}
            />
          )}
        </a>
      </Dropdown>
    );
  }
}

VSActions.propTypes = {
  run: PropTypes.object,
  placement: PropTypes.string,
  getPopupContainer: PropTypes.func,
  trigger: PropTypes.arrayOf(PropTypes.oneOf(['click', 'hover'])),
  onDropDownVisibleChange: PropTypes.func,
  showDownIcon: PropTypes.bool
};

VSActions.defaultProps = {
  getPopupContainer: o => o.parentNode,
  trigger: ['hover']
};

export default observer(VSActions);
