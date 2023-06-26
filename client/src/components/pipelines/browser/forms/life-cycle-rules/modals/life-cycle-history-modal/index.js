/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  Button,
  Modal,
  Row,
  Select,
  Table,
  message,
  Spin
} from 'antd';
import moment from 'moment-timezone';
// eslint-disable-next-line max-len
import DataStorageLifeCycleRulesExecutionLoad from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesExecutionLoad';
import {DESTINATIONS} from '../life-cycle-edit-modal';
import columns from './columns';
import styles from './life-cycle-history-modal.css';

const ACTION_TYPES = {
  transition: 'Transition',
  deletion: 'Deletion',
  prolongation: 'Prolongation'
};

function mapDestination (destination) {
  return DESTINATIONS[destination] || '';
}

function getTransitionDate (prolongation = {}) {
  if (prolongation.prolongedDate && prolongation.days) {
    return moment.utc(prolongation.prolongedDate).add(prolongation.days, 'days');
  }
  return undefined;
}

@inject('usersInfo')
@observer
class LifeCycleHistoryModal extends React.Component {
  state = {
    pending: false,
    executions: [],
    actionFilter: null
  }

  componentDidMount () {
    this.fetchHistory();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.rule !== this.props.rule) {
      this.fetchHistory();
    }
  }

  @computed
  get transitions () {
    const {rule} = this.props;
    if (rule && rule.transitions) {
      return rule.transitions;
    }
    return [];
  }

  @computed
  get prolongations () {
    const {rule} = this.props;
    if (rule && rule.prolongations) {
      return rule.prolongations;
    }
    return [];
  }

  @computed
  get usersInfo () {
    const {usersInfo} = this.props;
    if (usersInfo.loaded) {
      return usersInfo.value;
    }
    return [];
  }

  @computed
  get history () {
    const {executions} = this.state;
    const executionsData = executions.map(execution => ({
      date: moment.utc(execution.updated),
      action: DESTINATIONS[execution.storageClass] === DESTINATIONS.DELETION
        ? ACTION_TYPES.deletion
        : ACTION_TYPES.transition,
      user: 'System',
      file: execution.path,
      prolongation: undefined,
      transition: undefined,
      destination: mapDestination(execution.storageClass),
      status: execution.status
    }));
    const prolongationsData = this.prolongations
      .map(prolongation => ({
        date: moment.utc(prolongation.prolongedDate),
        action: ACTION_TYPES.prolongation,
        user: prolongation.userId
          ? this.getUserById(prolongation.userId)
          : '',
        file: prolongation.path,
        prolongation: `${prolongation.days} days`,
        transition: getTransitionDate(prolongation),
        destination: undefined,
        status: undefined
      }));
    return [...executionsData, ...prolongationsData]
      .sort((a, b) => a.date - b.date);
  }

  get filteredHistory () {
    const {actionFilter} = this.state;
    if (actionFilter && actionFilter.length > 0) {
      return this.history
        .filter(entry => actionFilter.includes(entry.action));
    }
    return this.history;
  }

  fetchHistory = () => {
    const {rule, storageId} = this.props;
    if (rule && rule.id && storageId) {
      this.setState({pending: true}, async () => {
        const request = new DataStorageLifeCycleRulesExecutionLoad(storageId, rule.id);
        await request.fetch();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.setState({executions: request.value || []});
        }
        this.setState({pending: false});
      });
    }
  };

  getUserById = (userId) => {
    const userObject = this.usersInfo.find(user => user.id === userId);
    if (userObject) {
      return userObject.name;
    }
    return '';
  };

  onSelectActionFilter = (keys) => {
    this.setState({actionFilter: keys});
  };

  renderHeader = () => {
    const {rule} = this.props;
    return (
      <div className={styles.header}>
        <div className={styles.headerRow}>
          <div className={styles.headerCell}>
            <span className="cp-title">
              Root:
            </span>
            <span className={styles.headerText}>
              {rule.pathGlob}
            </span>
          </div>
          <div className={styles.headerCell}>
            <span className="cp-title">
              Glob:
            </span>
            <span className={styles.headerText}>
              {rule.objectGlob}
            </span>
          </div>
          <div className={styles.headerCell}>
            <span className="cp-title">
              Action type:
            </span>
            <Select
              mode="multiple"
              onChange={this.onSelectActionFilter}
              className={styles.historyFilter}
            >
              {Object.values(ACTION_TYPES).map((description) => (
                <Select.Option
                  value={description}
                  key={description}
                >
                  {description}
                </Select.Option>
              ))}
            </Select>
          </div>
        </div>
      </div>
    );
  };

  render () {
    const {visible, onOk, rule} = this.props;
    const {pending} = this.state;
    if (!rule) {
      return null;
    }
    return (
      <Modal
        visible={visible}
        onCancel={onOk}
        onOk={onOk}
        title="History"
        width="70%"
        style={{top: '10%'}}
        footer={(
          <Row type="flex" justify="end">
            <Button
              onClick={onOk}
              type="primary"
              id="lifecycle-rules-history-modal-ok-btn"
            >
              OK
            </Button>
          </Row>
        )}
      >
        <Spin
          spinning={pending}
          style={{width: '100%'}}
        >
          <div className={styles.container}>
            {this.renderHeader()}
            <Table
              columns={columns}
              dataSource={this.filteredHistory}
              rowClassName={() => 'cp-even-odd-element'}
              rowKey={(data) => `${data.action}_${data.file}_${data.date}`}
              size="small"
              style={{flex: 1, width: '100%'}}
            />
          </div>
        </Spin>
      </Modal>
    );
  }
}

LifeCycleHistoryModal.propTypes = {
  visible: PropTypes.bool,
  onOk: PropTypes.func,
  rule: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default LifeCycleHistoryModal;
