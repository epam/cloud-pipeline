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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {
  Icon,
  message
} from 'antd';
import VsActionsAvailable, {vsAvailabilityCheck} from './vs-actions-available';
import Menu, {MenuItem, Divider, ItemGroup, SubMenu} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import VSBrowseDialog from '../vs-browse-dialog';
import GitDiffModal from './components/diff/modal';
import VSList from '../../../models/versioned-storage/list';
import VSAbortMerge from '../../../models/versioned-storage/abort-merge';
import VSClone from '../../../models/versioned-storage/clone';
import VSCommit from '../../../models/versioned-storage/commit';
import VSCheckout from '../../../models/versioned-storage/checkout';
import VSCurrentState from '../../../models/versioned-storage/current-state';
import VSFetch from '../../../models/versioned-storage/fetch';
import VSTaskStatus from '../../../models/versioned-storage/status';
import VSConflictError from '../../../models/versioned-storage/conflict-error';
import resolveFileConflict from '../../../models/versioned-storage/resolve-file-conflict';
import VSResolveRepoAfterRefresh from
  '../../../models/versioned-storage/resolve-repo-after-refresh';
import {
  CheckoutDialog,
  GitCommitDialog,
  ConflictsDialog
} from './components';
import DropDownWrapper from '../../special/dropdown-wrapper';

class VSActions extends React.Component {
  state = {
    fetchId: undefined,
    storagesStatuses: {},
    dropDownVisible: false,
    vsBrowserVisible: false,
    gitCommit: undefined,
    gitCheckout: undefined,
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

  fetchRepositoryStatus = (repository) => new Promise((resolve) => {
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
        Promise.all(this.repositories.map(this.fetchRepositoryStatus))
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
          if (task) {
            const vsTaskStatus = new VSTaskStatus(this.props.run?.id, task);
            const promise = vsTaskStatus.fetchUntilDone();
            this.pushOperation(vsTaskStatus, promise);
            return promise;
          }
          return Promise.resolve();
        }
      })
      .then(() => {
        if (typeof options.onSuccess === 'function') {
          options.onSuccess();
        }
      })
      .catch(e => {
        message.error(e.message, 5);
        console.error(e);
        if (e instanceof VSConflictError && onConflicts) {
          onConflicts(e.conflicts, options?.storage, options?.mergeInProgress);
        }
      })
      .then(() => {
        if (typeof options.hide === 'function') {
          options.hide();
        }
        return this.refresh(true);
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
      const onSuccess = () => message.success(
        (
          <span>
            <b>{name}</b> storage (version <b>{version?.name || commitId}</b>) cloned.
          </span>
        ),
        5
      );
      this.performRequestWithStatus(
        new VSClone(this.props.run?.id, id, commitId),
        resolve,
        undefined,
        {
          storage: versionedStorage,
          hide,
          onSuccess
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
      const onSuccess = () => message.success(
        (
          <span>
            <b>{name}</b> storage refreshed.
          </span>
        ),
        5
      );
      this.performRequestWithStatus(
        new VSFetch(this.props.run?.id, id),
        resolve,
        this.onConflictsDetected,
        {
          hide,
          storage: versionedStorage,
          onSuccess,
          mergeInProgress: false
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
      const {
        storagesStatuses
      } = this.state;
      this.setState({gitCommit: {
        storage: versionedStorage,
        files: diff,
        mergeInProgress: storagesStatuses && storagesStatuses[versionedStorage.id]
          ? storagesStatuses[versionedStorage.id].merge_in_progress
          : false
      }});
    }
  };

  closeCommitDialog = () => this.setState({gitCommit: undefined});

  onCommitVS = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve();
    }
    const {
      storagesStatuses
    } = this.state;
    const unsaved = storagesStatuses &&
      storagesStatuses[versionedStorage.id] &&
      storagesStatuses[versionedStorage.id].unsaved;
    const hide = message.loading((
      <span>
        Fetching <b>{versionedStorage.name}</b> diff...
      </span>
    ), 0);
    return new Promise((resolve) => {
      this.getVSDiff(versionedStorage)
        .then(diff => {
          hide();
          if (!diff || diff.length === 0) {
            if (unsaved) {
              return this.doCommit(versionedStorage);
            } else {
              message.info('Nothing to save', 5);
            }
          } else {
            this.showCommitDialog(versionedStorage, diff);
          }
        })
        .then(() => resolve());
    });
  };

  doCommit = (versionedStorage, commitMessage, filesToCommit = []) => {
    return new Promise((resolve) => {
      const {id, name} = versionedStorage;
      const hide = message.loading((
        <span>
          Saving changes for the <b>{name}</b> storage...
        </span>
      ), 0);
      const onSuccess = () => message.success(
        (
          <span>
            Changes saved for <b>{name}</b> storage.
          </span>
        ),
        5
      );
      this.closeCommitDialog();
      this.performRequestWithStatus(
        new VSCommit(
          this.props.run?.id,
          id,
          commitMessage,
          filesToCommit
        ),
        resolve,
        this.onConflictsDetected,
        {
          hide,
          storage: versionedStorage,
          onSuccess,
          mergeInProgress: true
        }
      );
    });
  };

  onConflictsDetected = (conflicts, storage, mergeInProgress) => {
    this.fetchRepositoryStatus(storage)
      .catch(() => {})
      .then((status) => {
        let {storagesStatuses} = this.state;
        if (status && status.hasOwnProperty(storage.id)) {
          storagesStatuses = {...(storagesStatuses || {}), ...status};
        }
        let filesInfo = [];
        if (storagesStatuses && storagesStatuses.hasOwnProperty(storage.id)) {
          filesInfo = (storagesStatuses[storage.id].files || []).slice();
        }
        this.setState({
          storagesStatuses,
          conflicts: {
            files: conflicts,
            filesInfo,
            storage,
            mergeInProgress
          }
        });
      });
  };

  onCloseConflictsDialog = () => {
    this.setState({
      conflicts: undefined
    });
  };

  onAbortChanges = () => {
    const {
      conflicts = {}
    } = this.state;
    const {
      run
    } = this.props;
    const {
      storage,
      mergeInProgress
    } = conflicts;
    if (!mergeInProgress) {
      this.onCloseConflictsDialog();
    } else {
      this.setState({conflicts: {...conflicts, pending: true}}, () => {
        const hide = message.loading('Aborting...', 0);
        const request = new VSAbortMerge(run?.id, storage?.id);
        request.send()
          .then(() => {
            if (request.error) {
              throw new Error(request.message);
            } else {
              hide();
              message.success('Save operation aborted', 5);
              this.onCloseConflictsDialog();
            }
          })
          .catch((e) => {
            hide();
            message.error(e.message, 5);
          });
      });
    }
  };

  onResolveConflicts = (files) => {
    const {
      conflicts = {}
    } = this.state;
    const {
      run
    } = this.props;
    const {
      storage,
      mergeInProgress
    } = conflicts;
    this.setState({
      conflicts: {...conflicts, pending: true}
    }, () => {
      const hide = message.loading('Resolving conflicts...', 0);
      Promise.all(
        Object.entries(files || {})
          .map(([file, content]) => resolveFileConflict(run?.id, storage?.id, file, content))
      )
        .then(() => {
          hide();
          message.success('Conflicts have been resolved', 5);
          this.onCloseConflictsDialog();
          if (mergeInProgress) {
            const filesDescription = Object.keys(files).join(', ');
            return this.doCommit(storage, `Resolving conflicted files: ${filesDescription}`);
          } else {
            const resolveRepo = new VSResolveRepoAfterRefresh(run?.id, storage?.id);
            return resolveRepo.send();
          }
        })
        .catch((e) => {
          hide();
          message.error(e.message, 5);
          this.setState({
            conflicts: {...conflicts, pending: false}
          });
        });
    });
  };

  onDiffVS = (versionedStorage) => {
    if (!versionedStorage) {
      return Promise.resolve();
    }
    const {
      storagesStatuses
    } = this.state;
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
                files: diff,
                mergeInProgress: storagesStatuses && storagesStatuses[versionedStorage.id]
                  ? storagesStatuses[versionedStorage.id].merge_in_progress
                  : false
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
        files = [],
        merge_in_progress: mergeInProgress
      } = storagesStatuses[storage?.id];
      this.onConflictsDetected(
        files.filter(file => /^conflict/i.test(file.status)).map(file => file.path),
        storage,
        mergeInProgress
      );
    }
  };

  openGitCheckoutModal = (storage) => {
    this.setState({
      gitCheckout: storage
    });
  };

  closeGitCheckoutModal = () => {
    this.setState({
      gitCheckout: undefined
    });
  };

  doCheckout = (version) => {
    const {
      gitCheckout
    } = this.state;
    if (gitCheckout) {
      return new Promise((resolve) => {
        const {id, name} = gitCheckout;
        const hide = message.loading((
          <span>
            Checking out revision <b>{version}</b> for the <b>{name}</b> storage...
          </span>
        ), 0);
        const onSuccess = () => message.success(
          (
            <span>
              Revision <b>{version}</b> checked out
            </span>
          ),
          5
        );
        this.closeGitCheckoutModal();
        this.performRequestWithStatus(
          new VSCheckout(this.props.run?.id, id, version),
          resolve,
          this.onConflictsDetected,
          {
            hide,
            storage: gitCheckout,
            onSuccess,
            mergeInProgress: false
          }
        );
      });
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

  renderOverlay = () => {
    const {storagesStatuses} = this.state;
    const menuItems = [];
    let onChange;
    if (!this.vsList || (!this.vsList.loaded && this.vsList.pending)) {
      menuItems.push((
        <MenuItem
          disabled key="loading"
        >
          <Icon type="loading" />
          <span>Fetching versioned storage info...</span>
        </MenuItem>
      ));
    } else if (this.vsList.error) {
      menuItems.push((
        <MenuItem
          disabled
          key="error"
        >
          <i>VCS not configured</i>
        </MenuItem>
      ));
    } else if (!this.vsList.loaded) {
      menuItems.push((
        <MenuItem
          disabled
          key="error"
        >
          <i>Error fetching versioned storages</i>
        </MenuItem>
      ));
    } else {
      const storages = this.repositories;
      menuItems.push((
        <MenuItem
          key="clone"
        >
          <Icon type="cloud-download-o" />
          <span>Clone</span>
        </MenuItem>
      ));
      if (storages.length > 0) {
        menuItems.push((<Divider key="clone-divider" />));
      }
      storages.forEach((storage, index, array) => {
        const status = storagesStatuses.hasOwnProperty(storage.id)
          ? storagesStatuses[storage.id]
          : {};
        const {
          files = [],
          merge_in_progress: mergeInProgress = false,
          pending = false,
          unsaved = false
        } = status;
        const notIgnoredFiles = files.filter(f => !/^ignored$/i.test(f.status));
        const hasConflicts = !!files.find(f => /^conflicts$/i.test(f.status));
        const hasModifications = !!notIgnoredFiles.find(f => !/^conflicts$/i.test(f.status));
        const diffEnabled = !pending && files.length > 0;
        const saveEnabled = !storage.detached &&
          !pending &&
          !mergeInProgress &&
          (
            (hasModifications && !hasConflicts) ||
            unsaved
          );
        const refreshEnabled = !hasConflicts && !mergeInProgress && !pending;
        const Container = array.length === 1 ? ItemGroup : SubMenu;
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
            <MenuItem
              key={`diff-${storage.id}`}
              disabled={!diffEnabled}
            >
              <Icon type="exception" />
              <span> Diff</span>
            </MenuItem>
            <MenuItem
              key={`save-${storage.id}`}
              disabled={!saveEnabled}
            >
              <Icon type="save" /> Save
              {
                storage.detached && (
                  <span style={{marginLeft: 5}}>
                    (current revision is not the latest)
                  </span>
                )
              }
            </MenuItem>
            <MenuItem
              key={`refresh-${storage.id}`}
              disabled={!refreshEnabled}
            >
              <Icon type="sync" /> Refresh
            </MenuItem>
            <Divider />
            <MenuItem
              key={`checkout-${storage.id}`}
              disabled={mergeInProgress || unsaved}
            >
              <Icon type="fork" /> Checkout revision
            </MenuItem>
            <Divider />
            {
              hasConflicts && (
                <MenuItem
                  key={`resolve-${storage.id}`}
                >
                  <Icon type="exclamation-circle" /> Resolve conflicts
                </MenuItem>
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
            break;
          case 'checkout':
            if (storage) {
              this.openGitCheckoutModal(storage);
            }
            break;
        }
        this.setState({dropDownVisible: false});
      };
    }
    return (
      <div>
        <Menu
          onClick={onChange}
          openTransition="none"
          subMenuOpenDelay={0.2}
          subMenuCloseDelay={0.2}
          openAnimation="zoom"
          getPopupContainer={node => node.parentNode}
          selectedKeys={[]}
        >
          {menuItems}
        </Menu>
      </div>
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
      gitCheckout,
      gitCommit,
      conflicts
    } = this.state;
    if (!this.fsBrowserAvailable) {
      return null;
    }
    return (
      <DropDownWrapper visible={dropDownVisible}>
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
              mergeInProgress={gitDiff?.mergeInProgress}
              fileDiffs={gitDiff?.files}
              onClose={this.closeGitDiffModal}
            />
            {gitCommit && (
              <GitCommitDialog
                visible={!!gitCommit}
                run={run?.id}
                mergeInProgress={gitCommit?.mergeInProgress}
                onCommit={this.doCommit}
                onCancel={this.closeCommitDialog}
                storage={gitCommit?.storage}
                files={gitCommit?.files}
              />
            )}
            <ConflictsDialog
              visible={!!conflicts}
              disabled={conflicts?.pending}
              conflicts={conflicts?.files}
              conflictsInfo={conflicts?.filesInfo}
              onAbort={this.onAbortChanges}
              onClose={this.onCloseConflictsDialog}
              onResolve={this.onResolveConflicts}
              run={run?.id}
              mergeInProgress={conflicts?.mergeInProgress}
              storage={conflicts?.storage}
            />
            <CheckoutDialog
              visible={!!gitCheckout}
              repository={gitCheckout}
              onClose={this.closeGitCheckoutModal}
              onSelect={this.doCheckout}
            />
          </a>
        </Dropdown>
      </DropDownWrapper>
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

VSActions.check = vsAvailabilityCheck;

export default observer(VSActions);
export {VsActionsAvailable};
