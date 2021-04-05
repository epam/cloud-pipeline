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
import classNames from 'classnames';
import {
  Dropdown,
  Icon,
  Menu, message
} from 'antd';
import VSBrowseDialog from '../vs-browse-dialog';
import VSList from '../../../models/versioned-storage/list';
import styles from './vs-actions.css';
import '../../../staticStyles/vs-actions-dropdown.css';
import VSClone from '../../../models/versioned-storage/clone';
import VSTaskStatus from '../../../models/versioned-storage/status';

class VSActions extends React.Component {
  state = {
    dropDownVisible: false,
    vsBrowserVisible: false
  };

  @observable vsList;

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
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.run?.id !== this.props.run?.id) {
      this.vsTaskStatus && this.vsTaskStatus.abort();
    }
    if (prevState.dropDownVisible !== this.state.dropDownVisible && this.state.dropDownVisible) {
      this.refresh(prevProps.run?.id !== this.props.run?.id);
    }
  }

  openVSBrowser = () => {
    this.setState({vsBrowserVisible: true});
  };

  closeVSBrowser = () => {
    this.setState({vsBrowserVisible: false});
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
      const request = new VSClone(this.props.run?.id, id, commitId);
      request
        .send()
        .then(() => {
          if (request.error) {
            message.error(request.error, 5);
          } else {
            const {task} = request.value;
            this.vsTaskStatus = new VSTaskStatus(this.props.run?.id, task);
            return this.vsTaskStatus.fetchUntilDone();
          }
        })
        .then(() => this.refresh(true))
        .catch(e => {
          message.error(e.message, 5);
        })
        .then(() => {
          hide();
        })
        .then(() => resolve());
    });
  }

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
          >
            <Menu.Item key={`diff-${storage.id}`}>
              <Icon type="exception" /> Diff
            </Menu.Item>
            <Menu.Item key={`save-${storage.id}`}>
              <Icon type="save" /> Save
            </Menu.Item>
            <Menu.Item key={`refresh-${storage.id}`}>
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
        }
        this.setState({
          dropDownVisible: false
        });
      };
    }
    const {subMenuDirection} = this.props;
    return (
      <Menu
        className={
          classNames(
            styles.menu,
            'vs-actions-dropdown',
            `vs-actions-dropdown-${subMenuDirection}`
          )
        }
        onClick={onChange}
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
      showDownIcon
    } = this.props;
    const {
      dropDownVisible
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
        </a>
      </Dropdown>
    );
  }
}

VSActions.propTypes = {
  run: PropTypes.object,
  placement: PropTypes.string,
  subMenuDirection: PropTypes.oneOf(['left', 'right']),
  getPopupContainer: PropTypes.func,
  trigger: PropTypes.arrayOf(PropTypes.oneOf(['click', 'hover'])),
  onDropDownVisibleChange: PropTypes.func,
  showDownIcon: PropTypes.bool
};

VSActions.defaultProps = {
  subMenuDirection: 'left',
  getPopupContainer: o => o.parentNode,
  trigger: ['hover']
};

export default observer(VSActions);
