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
import VSCurrentState from '../../../models/versioned-storage/current-state';
import VSFetch from '../../../models/versioned-storage/fetch';
import VSTaskStatus from '../../../models/versioned-storage/status';
import VSConflictError from '../../../models/versioned-storage/conflict-error';
import {GitCommitDialog, ConflictsDialog} from './components';

const SUBMENU_POSITION = {
  right: 'right',
  left: 'left'
};

class VSActions extends React.Component {
  state = {
    fetchId: undefined,
    storagesStatuses: {},
    dropDownVisible: false,
    vsBrowserVisible: false,
    subMenuPosition: SUBMENU_POSITION.right,
    gitCommit: undefined,
    gitDiff: undefined,
    conflicts: undefined
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

  componentDidMount () {
    if (this.vsList && this.vsList.loaded) {
      this.fetchVersionedStoragesStatus();
    }
  }

  componentWillUnmount () {
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
    if (this.vsList && this.vsList.loaded && this.state.fetchId !== this.vsList.fetchId) {
      this.fetchVersionedStoragesStatus();
    }
  }

  fetchVersionedStoragesStatus = () => {
    if (this.vsList && this.vsList.loaded && this.props.run) {
      this.setState({
        fetchId: this.vsList.fetchId,
        storagesStatuses: this.repositories.reduce((acc, cur) => ({
          ...acc,
          [cur.id]: {
            pending: true
          }
        }), {})
      }, () => {
        console.log('fetch storages info', this.repositories);
        const wrapFetchRepositoryStatusFn = repository => new Promise((resolve) => {
          const request = new VSCurrentState(this.props.run?.id, repository.id);
          request
            .fetch()
            .then(() => {
              if (request.loaded) {
                resolve({
                  [repository.id]: request.value
                });
              } else {
                resolve({});
              }
            })
            .catch(() => resolve({}));
        });
        Promise.all(this.repositories.map(wrapFetchRepositoryStatusFn))
          .then(payloads => {
            const storagesStatuses = payloads
              .reduce((acc, cur) => ({
                ...acc,
                ...cur
              }), {});
            this.setState({
              storagesStatuses
            });
          });
      });
    }
  };

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

  performRequestWithStatus = (
    request,
    resolve,
    hideMessage,
    onConflicts = undefined,
    options = {}
  ) => {
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
        if (e instanceof VSConflictError && onConflicts) {
          onConflicts(e.conflicts, options?.storage);
        }
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
        hide,
        undefined,
        {
          storage: versionedStorage
        }
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
        hide,
        this.onConflictsDetected,
        {
          storage: versionedStorage
        }
      );
    });
  };

  getVSDiff = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve([]);
    }
    return new Promise((resolve) => {
      const request = new VSCurrentState(this.props.run?.id, versionedStorage?.id);
      request
        .fetch()
        .then(() => {
          if (request.error || !request.loaded) {
            message.error(request.error || 'Error fetching storage diff', 5);
            resolve([]);
          } else {
            resolve(Array.from(request.value?.files || []));
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

  doCommit = (versionedStorage, commitMessage) => {
    return new Promise((resolve) => {
      const {id, name} = versionedStorage;
      const hide = message.loading((
        <span>
          Saving changes for the <b>{name}</b> storage...
        </span>
      ), 0);
      this.closeCommitDialog();
      this.performRequestWithStatus(
        new VSCommit(this.props.run?.id, id, commitMessage),
        resolve,
        hide,
        this.onConflictsDetected,
        undefined,
        {
          storage: versionedStorage
        }
      );
    });
  };

  onConflictsDetected = (conflicts, storage) => {
    this.setState({
      conflicts: {
        files: conflicts,
        storage
      }
    });
  };

  onCloseConflictsDialog = () => {
    this.setState({
      conflicts: undefined
    });
  }

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

  onResolveConflictsVS = (storage) => {
    const {
      storagesStatuses
    } = this.state;
    if (storagesStatuses && storagesStatuses.hasOwnProperty(storage?.id)) {
      const {
        files = []
      } = storagesStatuses[storage?.id];
      this.onConflictsDetected(
        files.filter(file => /^conflict/i.test(file.status)).map(file => file.path),
        storage
      );
    }
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
    const {storagesStatuses} = this.state;
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
        const status = storagesStatuses.hasOwnProperty(storage.id)
          ? storagesStatuses[storage.id]
          : {};
        const {
          merge_in_progress: mergeInProgress = false,
          pending = false
        } = status;
        const Container = array.length === 1 ? Menu.ItemGroup : Menu.SubMenu;
        menuItems.push((
          <Container
            key={`-${storage.id}`}
            title={(
              <span>
                {storage.name}
                {
                  pending && (
                    <Icon type="loading" />
                  )
                }
              </span>
            )}
            ref={(el) => {
              this.menuContainerRef = el;
            }}
          >
            <Menu.Item
              key={`diff-${storage.id}`}
              disabled={mergeInProgress || pending}
            >
              <Icon type="exception" /> Diff
            </Menu.Item>
            <Menu.Item
              key={`save-${storage.id}`}
              disabled={storage.detached || mergeInProgress || pending}
            >
              <Icon type="save" /> Save
            </Menu.Item>
            <Menu.Item
              key={`refresh-${storage.id}`}
              disabled={mergeInProgress || pending}
            >
              <Icon type="sync" /> Refresh
            </Menu.Item>
            {
              mergeInProgress && (
                <Menu.Item
                  key={`resolve-${storage.id}`}
                >
                  <Icon type="exclamation-circle" /> Resolve conflicts
                </Menu.Item>
              )
            }
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
          case 'resolve':
            if (storage) {
              this.onResolveConflictsVS(storage);
            }
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
      gitCommit,
      conflicts
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
          <ConflictsDialog
            visible={!!conflicts}
            conflicts={conflicts?.files}
            onClose={this.onCloseConflictsDialog}
            run={run?.id}
            storage={conflicts?.storage}
          />
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
