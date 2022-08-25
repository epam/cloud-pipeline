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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
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
import DataStorageLifeCycleRulesExecutionLoad
  from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesExecutionLoad';
import {DESTINATIONS} from '../life-cycle-edit-modal';
import columns from './columns';
import styles from './life-cycle-history-modal.css';

const ACTION_TYPES = {
  transition: 'Transition',
  deletion: 'Deletion',
  prolongation: 'Prolongation'
};

const FORMAT = 'DD.MM.YYYY';
const FULL_FORMAT = `${FORMAT} HH:mm:ss`;

function mapDestination (destination) {
  return DESTINATIONS[destination] || '';
}

function getTransitionDate (prolongation = {}) {
  if (prolongation.prolongedDate && prolongation.days) {
    return moment.utc(prolongation.prolongedDate)
      .add(prolongation.days, 'days')
      .format(FORMAT);
  }
  return undefined;
}

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
  get history () {
    const {executions} = this.state;
    const executionsData = executions.map(execution => ({
      date: moment.utc(execution.updated).format(FULL_FORMAT),
      action: ACTION_TYPES.transition,
      user: 'System',
      file: execution.path,
      prolongation: undefined,
      transition: undefined,
      destination: mapDestination(execution.storageClass)
    }));
    const prolongationsData = this.prolongations
      .map(prolongation => ({
        date: moment.utc(prolongation.prolongedDate).format(FULL_FORMAT),
        action: ACTION_TYPES.prolongation,
        user: prolongation.user || '',
        file: prolongation.path,
        prolongation: `${prolongation.days} days`,
        transition: getTransitionDate(prolongation),
        destination: undefined
      }));
    return [...executionsData, ...prolongationsData]
      .sort((a, b) => moment(a.date) - moment(b.date));
  }

  get filteredHistory () {
    const {actionFilter} = this.state;
    console.log(this.history);
    if (actionFilter) {
      return this.history.filter(entry => entry.action === actionFilter);
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

  onSelectActionFilter = (key) => {
    this.setState({actionFilter: key});
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
              {rule.objectGlob}
            </span>
          </div>
          <div className={styles.headerCell}>
            <span className="cp-title">
              Glob:
            </span>
            <span className={styles.headerText}>
              {rule.pathGlob}
            </span>
          </div>
          <div className={styles.headerCell}>
            <span className="cp-title">
              Action type:
            </span>
            <Select
              onChange={this.onSelectActionFilter}
              style={{marginLeft: 5, width: 150}}
            >
              <Select.Option
                style={{height: 32}}
                value={undefined}
                key="empty"
              />
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
            >
              Ok
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
