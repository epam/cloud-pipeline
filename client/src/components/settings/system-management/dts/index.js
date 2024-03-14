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
import {Modal} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import displayDate from '../../../../utils/displayDate';
import SubSettings from '../../sub-settings';
import DtsInfo from './dts-info';
import DTSPreferencesUpdate from '../../../../models/dts/DTSPreferencesUpdate';
import DTSPreferencesDelete from '../../../../models/dts/DTSPreferencesDelete';
import styles from './dts.css';

@inject('dtsList')
@observer
class DtsManagement extends React.Component {
  state = {
    pending: false,
    refreshToken: 0,
    modified: false
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

  onSavePreferences = async ({dts, toUpdate = [], toDelete = []}) => {
    let deletePromise;
    let updatePromise;
    if (toUpdate.length) {
      updatePromise = new Promise(async (resolve) => {
        const request = new DTSPreferencesUpdate(dts.id);
        await request.send({
          preferencesToUpdate: toUpdate.reduce((acc, current) => {
            acc[current.key] = current.value;
            return acc;
          }, {})
        });
        resolve(request);
      });
    }
    if (toDelete.length) {
      deletePromise = new Promise(async (resolve) => {
        const request = new DTSPreferencesDelete(dts.id);
        await request.send({
          preferenceKeysToRemove: toDelete.map(({key}) => key)
        });
        resolve(request);
      });
    }
    if (deletePromise || updatePromise) {
      this.setState({pending: true});
      const [deleteResult, updateResult] = await Promise.all([
        deletePromise,
        updatePromise
      ]);
      if (deletePromise && deleteResult.error) {

      }
      if (updatePromise && updateResult.error) {

      }
      await this.props.dtsList.fetch();
      this.setState({
        pending: false,
        modified: false,
        refreshToken: this.state.refreshToken + 1
      });
    }
  };

  renderDtsCard = (dts) => {
    return (
      <div className={styles.dtsCardContainer}>
        <div style={{display: 'flex', flexWrap: 'nowrap'}}>
          <span className={styles.sectionText}>Id: {dts.id}</span>
          <span
            className={classNames(styles.sectionText, styles.ellipsis)}
            style={{marginLeft: 5}}
          >
            {dts.name}
          </span>
        </div>
        <div>
          Status: {dts.status}
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

  render () {
    const {pending, refreshToken} = this.state;
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
            pending={pending}
            refreshToken={refreshToken}
          />
        );
      }}));
    return (
      <SubSettings
        className={styles.container}
        sectionsListClassName={styles.sectionList}
        sections={sections}
        showSectionsSearch
        sectionsSearchPlaceholder="Filter dts"
        canNavigate={this.confirmNavigation}
      />
    );
  }
}

DtsInfo.propTypes = {
  handleModified: PropTypes.func
};

export default DtsManagement;
