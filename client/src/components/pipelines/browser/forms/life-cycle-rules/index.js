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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {
  Button,
  Modal,
  Tooltip,
  Spin,
  Icon,
  message
} from 'antd';
import DataStorageLifeCycleRulesLoad
  from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import DataStorageLifeCycleRulesUpdate
  from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesUpdate';
import DataStorageLifeCycleRulesCreate
  from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesCreate';
import DataStorageLifeCycleRulesDelete
  from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesDelete';
import {
  LifeCycleEditModal,
  DESTINATIONS,
  LifeCycleHistoryModal
} from './modals';
import styles from './life-cycle-rules.css';

@inject(({routing}, params) => ({
  lifeCycleRules: new DataStorageLifeCycleRulesLoad(params.storageId)
}))
@observer
class LifeCycleRules extends React.Component {
  state = {
    editRule: null,
    showHistory: null,
    pending: false
  }

  componentDidMount () {
    this.fetchLifeCycleRules();
  }

  componentDidUpdate (prevProps) {
    const {storageId} = this.props;
    if (storageId !== prevProps.storageId) {
      this.fetchLifeCycleRules();
    }
  }

  @computed
  get rules () {
    const {lifeCycleRules} = this.props;
    if (lifeCycleRules && lifeCycleRules.loaded && !lifeCycleRules.error) {
      return [...(lifeCycleRules.value || [])]
        .sort((ruleA, ruleB) => ruleA.pathGlob.localeCompare(ruleB.pathGlob));
    }
    return [];
  }

  fetchLifeCycleRules = () => {
    const hide = message.loading('Loading transition rules...', 0);
    this.setState({pending: true}, async () => {
      const {lifeCycleRules} = this.props;
      await lifeCycleRules.fetch();
      hide();
      if (lifeCycleRules.error) {
        message.error(lifeCycleRules.error, 5);
      }
      this.setState({pending: false});
    });
  };

  openEditRuleDialog = (rule) => {
    if (!rule) {
      return null;
    }
    this.setState({editRule: rule});
  };

  closeEditRuleDialog = () => {
    this.setState({editRule: null});
  };

  showHistory = (rule) => {
    if (!rule) {
      return null;
    }
    this.setState({showHistory: rule});
  };

  closeHistory = () => {
    this.setState({showHistory: null});
  };

  updateRule = (payload, ruleId) => {
    const {storageId} = this.props;
    const request = new DataStorageLifeCycleRulesUpdate(storageId);
    payload.id = ruleId;
    payload.datastorageId = storageId;
    this.setState({pending: true}, async () => {
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
      } else {
        this.fetchLifeCycleRules();
        this.closeEditRuleDialog();
      }
      this.setState({pending: false});
    });
  };

  createRule = (payload) => {
    const {storageId} = this.props;
    const request = new DataStorageLifeCycleRulesCreate(storageId);
    payload.datastorageId = storageId;
    this.setState({pending: true}, async () => {
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
      } else {
        this.fetchLifeCycleRules();
        this.closeEditRuleDialog();
      }
      this.setState({pending: false});
    });
  };

  deleteRule = (ruleId) => {
    const {storageId} = this.props;
    const onConfirm = () => {
      const request = new DataStorageLifeCycleRulesDelete(storageId, ruleId);
      this.setState({pending: true}, async () => {
        await request.send();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.fetchLifeCycleRules();
          this.closeEditRuleDialog();
        }
        this.setState({pending: false});
      });
    };
    Modal.confirm({
      title: `Are you sure you want to delete transition rule?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        return onConfirm();
      }
    });
  };

  onOkEditRule = (payload, ruleId) => {
    if (payload && ruleId) {
      return this.updateRule(payload, ruleId);
    }
    return this.createRule(payload);
  };

  renderRule = (rule) => {
    const {readOnly} = this.props;
    const controls = [
      !readOnly && (
        <Button
          className={styles.controlBtn}
          onClick={() => this.openEditRuleDialog(rule)}
          size="small"
          key="edit"
        >
          <Icon type="edit" />
        </Button>
      ),
      <Button
        className={styles.controlBtn}
        onClick={() => this.showHistory(rule)}
        size="small"
        key="history"
      >
        <Icon type="book" />
      </Button>,
      !readOnly && (
        <Button
          className={styles.controlBtn}
          type="danger"
          onClick={() => this.deleteRule(rule.id)}
          size="small"
          key="delete"
        >
          <Icon type="delete" />
        </Button>
      )
    ].filter(Boolean);
    const destination = (rule.transitions || [])[0]
      ? DESTINATIONS[(rule.transitions || [])[0].storageClass]
      : '';
    const renderTitle = () => (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {rule.transitions.slice(1).map((transition, index) => (
          <span key={`${transition.storageClass}${index}`}>
            {DESTINATIONS[transition.storageClass]}
          </span>
        ))}
      </div>
    );
    return (
      <tr
        className="cp-even-odd-element"
        key={rule.id}
      >
        <td>{rule.pathGlob}</td>
        <td>{rule.objectGlob}</td>
        <td>
          <span
            className={destination === DESTINATIONS.DELETION
              ? 'cp-error'
              : ''
            }
          >
            {destination}
          </span>
          {(rule.transitions || []).length > 1 ? (
            <Tooltip title={renderTitle()}>
              <span
                className="cp-primary"
                style={{marginLeft: 5, cursor: 'pointer'}}
              >
                <Icon type="plus" />
                {`${rule.transitions.length - 1} more`}
              </span>
            </Tooltip>
          ) : null}
        </td>
        <td style={{padding: '0 5px', textAlign: 'right'}}>
          {controls}
        </td>
      </tr>
    );
  };

  render () {
    const {
      editRule,
      showHistory,
      pending
    } = this.state;
    const {storageId, readOnly} = this.props;
    return (
      <Spin spinning={pending}>
        <div className={styles.container}>
          <Button
            className={styles.createBtn}
            onClick={() => this.openEditRuleDialog({})}
            size="small"
            disabled={readOnly}
          >
            <Icon type="plus" />
            Create
          </Button>
          {this.rules.length > 0 ? (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Root</th>
                  <th>Glob</th>
                  <th>Transition</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {this.rules.map((rule) => this.renderRule(rule))}
              </tbody>
            </table>
          ) : null }
          {editRule ? (
            <LifeCycleEditModal
              visible={Boolean(editRule)}
              onOk={this.onOkEditRule}
              onCancel={this.closeEditRuleDialog}
              rule={editRule}
              createNewRule={Object.keys(editRule).length === 0}
              pending={pending}
            />
          ) : null}
          <LifeCycleHistoryModal
            visible={Boolean(showHistory)}
            rule={showHistory}
            storageId={storageId}
            onOk={this.closeHistory}
          />
        </div>
      </Spin>
    );
  }
}

LifeCycleRules.propTypes = {
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  readOnly: PropTypes.bool
};

export default LifeCycleRules;
