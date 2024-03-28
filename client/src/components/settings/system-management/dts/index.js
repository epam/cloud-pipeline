/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Modal, message, Button} from 'antd';
import classNames from 'classnames';
import displayDate from '../../../../utils/displayDate';
import SubSettings from '../../sub-settings';
import DtsInfo from './dts-info';
import DTSPreferencesUpdate from '../../../../models/dts/DTSPreferencesUpdate';
import DTSPreferencesDelete from '../../../../models/dts/DTSPreferencesDelete';
import CreateDtsModal from './components/create-dts-modal';
import styles from './dts.css';
import DTSDelete from '../../../../models/dts/DTSDelete';

@inject('dtsList')
@observer
class DtsManagement extends React.Component {
  state = {
    pending: false,
    refreshToken: 0,
    modified: false,
    showCreateDtsModal: false
  }

  @computed
  get dtsList () {
    const {dtsList} = this.props;
    if (dtsList.loaded && dtsList.value && dtsList.value.length) {
      return (dtsList.value || []).map(v => v);
    }
    return [];
  }

  componentDidMount () {
    const {dtsList} = this.props;
    dtsList.fetchIfNeededOrWait();
  }

  onChangePreferences = (modified) => {
    const {handleModified} = this.props;
    handleModified && handleModified(modified);
    if (this.state.modified !== modified) {
      this.setState({modified});
    }
  };

  confirmNavigation = () => {
    return new Promise((resolve) => {
      if (this.state.modified) {
        Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk: () => {
            this.setState({modified: false});
            resolve(true);
          },
          onCancel () {
            resolve(false);
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
  };

  onDeleteDts = async (dts) => {
    const {dtsList} = this.props;
    if (!dts) {
      return null;
    }
    const request = new DTSDelete(dts.id);
    await request.send();
    if (request.error) {
      message.error(request.error, 5);
    }
    if (dtsList) {
      await dtsList.fetch();
    }
  };

  onSavePreferences = async ({dts, toUpdate = [], toDelete = []}) => {
    let deleteRequest;
    let updateRequest;
    if (!toUpdate.length && !toDelete.length) {
      return;
    }
    this.setState({pending: true});
    if (toDelete.length) {
      deleteRequest = new DTSPreferencesDelete(dts.id);
      await deleteRequest.send({
        preferenceKeysToRemove: toDelete.map(({key}) => key)
      });
    }
    if (toUpdate.length) {
      updateRequest = new DTSPreferencesUpdate(dts.id);
      await updateRequest.send({
        preferencesToUpdate: toUpdate.reduce((acc, current) => {
          acc[current.key] = current.value;
          return acc;
        }, {})
      });
    }
    if (deleteRequest && deleteRequest.error) {
      message.error('Failed to delete preference.', 5);
    }
    if (updateRequest && updateRequest.error) {
      message.error('Failed to update preference', 5);
    }
    await this.props.dtsList.fetch();
    this.setState({
      pending: false,
      modified: false,
      refreshToken: this.state.refreshToken + 1
    });
  };

  renderDtsCard = (dts) => {
    const statusIcon = (
      <svg height="10" width="10">
        <circle cx="5" cy="5" r="4"
          strokeWidth={1}
          className={(dts.status || '').toLowerCase() === 'online'
            ? 'cp-status-online'
            : 'cp-status-offline'
          }
      />
      </svg>
    );
    return (
      <div className={styles.dtsCardContainer}>
        <div style={{display: 'flex', flexWrap: 'nowrap'}}>
          <span
            className={classNames(styles.sectionText, styles.ellipsis)}
            style={{marginLeft: 5}}
          >
            {statusIcon} {dts.name}
          </span>
        </div>
        <span
          className={classNames(
            styles.sectionText,
            styles.notImportant,
            'cp-text-not-important'
          )}
        >
          Created date: {displayDate(dts.createdDate, 'YYYY-MM-DD HH:mm:ss')}
        </span>
        {dts.heartbeat ? (
          <span className={classNames(
            styles.sectionText,
            styles.notImportant,
            'cp-text-not-important'
          )}>
            Heartbeat: {displayDate(dts.heartbeat, 'YYYY-MM-DD HH:mm:ss')}
          </span>
        ) : null}
      </div>
    );
  };

  openCreateDtsModal = () => this.setState({showCreateDtsModal: true});

  closeCreateDtsModal = () => this.setState({showCreateDtsModal: false});

  render () {
    const {
      pending,
      refreshToken,
      showCreateDtsModal
    } = this.state;
    const sections = this.dtsList.map((dts) => ({
      key: dts.id,
      title: this.renderDtsCard(dts),
      name: dts.name,
      render: () => {
        return (
          <DtsInfo
            key={dts.id}
            dts={dts}
            onSave={this.onSavePreferences}
            onChange={this.onChangePreferences}
            onDeleteDts={this.onDeleteDts}
            pending={pending}
            refreshToken={refreshToken}
          />
        );
      }}));
    return (
      <div style={{height: '100%'}}>
        <SubSettings
          beforeListRowRenderer={() => (
            <Button
              size="small"
              type="primary"
              style={{width: '100%', marginBottom: 5}}
              onClick={this.openCreateDtsModal}
            >
              Create DTS
            </Button>
          )}
          className={styles.container}
          sectionsListClassName={styles.sectionList}
          sections={sections}
          showSectionsSearch
          sectionsSearchPlaceholder="Filter dts"
          canNavigate={this.confirmNavigation}
        />
        <CreateDtsModal
          visible={showCreateDtsModal}
          onClose={this.closeCreateDtsModal}
        />
      </div>
    );
  }
}

DtsManagement.propTypes = {
  handleModified: PropTypes.func
};

export default DtsManagement;
