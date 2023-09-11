/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {WdlEvent} from '../../../../../../../../utils/pipeline-builder';
import classNames from 'classnames';
import {Button, Collapse, Icon, Modal} from 'antd';
import WdlIssues from '../wdl-issues';
import {addCall, removeTask} from '../../utilities/workflow-utilities';
import styles from './wdl-tasks.css';

class WdlTasks extends React.Component {
  state = {
    tasks: [],
    expandedKeys: []
  };

  componentDidMount () {
    this.subscribeOnDocumentEvents(this.props.document);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.document !== this.props.document) {
      this.subscribeOnDocumentEvents(this.props.document, prevProps.document);
    }
  }

  subscribeOnDocumentEvents = (newDocument, previousDocument) => {
    if (previousDocument) {
      this.unsubscribeFromDocumentEvents(previousDocument);
    }
    if (newDocument) {
      newDocument.on(WdlEvent.changed, this.onDocumentChanged, this);
    }
    this.updateTasks();
  };

  unsubscribeFromDocumentEvents = (doc) => {
    if (doc) {
      doc.off(WdlEvent.changed, this.onDocumentChanged, this);
    }
  };

  onDocumentChanged = () => {
    this.updateTasks();
  };

  updateTasks = () => {
    const {
      document: wdlDocument
    } = this.props;
    const {
      tasks = []
    } = wdlDocument || {};
    this.setState({
      tasks
    });
  };

  onExpandedKeysChanged = (keys) => this.setState({expandedKeys: keys});

  onRemoveTaskClicked = (task) => (event) => {
    if (event) {
      event.stopPropagation();
      if (event.target && typeof event.target.blur === 'function') {
        event.target.blur();
      }
    }
    if (task) {
      Modal.confirm({
        title: (
          <div>
            Are you sure you want to delete task <b>{task.name}</b> and all underlying calls?
          </div>
        ),
        style: {
          wordWrap: 'break-word'
        },
        onOk: () => removeTask(task),
        okText: 'Remove',
        okType: 'danger',
        cancelText: 'Cancel'
      });
    }
  }

  renderTaskInfo = (task) => {
    const info = [];
    if (task && task.executions && task.executions.length > 0) {
      info.push(`calls: ${task.executions.length}`);
    }
    if (task && task.issues && task.issues.length > 0) {
      info.push(`issues: ${task.issues.length}`);
    }
    if (info.length > 0) {
      return (
        <span
          className={
            classNames(
              styles.taskInfo,
              'cp-text-not-important'
            )
          }
        >
          ({info.join(', ')})
        </span>
      );
    }
    return null;
  };

  renderTaskHeader = (task) => {
    const {
      disabled
    } = this.props;
    return (
      <div className={styles.header}>
        <b>{task.name}</b>
        {
          this.renderTaskInfo(task)
        }
        {
          !disabled && (
            <div className={styles.actions}>
              <Button
                className={styles.action}
                type="danger"
                size="small"
                onClick={this.onRemoveTaskClicked(task)}
              >
                <Icon type="delete" />
              </Button>
            </div>
          )
        }
      </div>
    );
  };

  renderTask = (task) => {
    const {
      executions = [],
      issues = []
    } = task;
    const {
      onSelect,
      disabled,
      document
    } = this.props;
    const onSelectExecution = (execution) => {
      if (typeof onSelect === 'function') {
        onSelect(execution);
      }
    };
    const onCreateCall = () => {
      if (document) {
        onSelectExecution(addCall(document.requireWorkflow(), task));
      }
    };
    return (
      <div className={styles.taskDetails}>
        {
          executions.length === 0 && (
            <div
              className={classNames('cp-text-not-important', styles.row)}
            >
              No calls specified.
            </div>
          )
        }
        {
          executions.length > 0 && (
            <div
              className={styles.row}
            >
              <b>Calls ({executions.length}):</b>
            </div>
          )
        }
        {
          executions.map((execution) => (
            <div
              key={execution.uuid}
              className={styles.row}
            >
              <a
                onClick={() => onSelectExecution(execution)}
              >
                <b>{execution.reference || (<i>unknown</i>)}</b> call
              </a>
            </div>
          ))
        }
        {
          !disabled && !!document && (
            <div
              className={styles.row}
            >
              <a
                onClick={() => onCreateCall()}
              >
                <Icon type="plus" /> create new call
              </a>
            </div>
          )
        }
        {
          issues.length === 0 && (
            <div
              className={classNames('cp-text-not-important', styles.row)}
            >
              No issues found.
            </div>
          )
        }
        {
          issues.length > 0 && (
            <div
              className={styles.row}
            >
              <b>Task issues ({issues.length}):</b>
            </div>
          )
        }
        {
          issues.length > 0 && (
            <WdlIssues
              issues={issues}
              alert
              fullDescription
            />
          )
        }
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      document: wdlDocument
    } = this.props;
    if (!wdlDocument) {
      return null;
    }
    const {
      tasks,
      expandedKeys = []
    } = this.state;
    return (
      <div
        className={
          classNames((className, styles.container))
        }
        style={style}
      >
        <div
          className={
            classNames(
              styles.header,
              styles.mainHeader
            )
          }
        >
          <b>Document tasks ({tasks.length})</b>
        </div>
        <Collapse
          activeKey={expandedKeys}
          onChange={this.onExpandedKeysChanged}
          className="wdl-properties-collapse"
          bordered={false}
        >
          {
            tasks.map((task) => (
              <Collapse.Panel
                key={task.uuid}
                header={this.renderTaskHeader(task)}
              >
                {this.renderTask(task)}
              </Collapse.Panel>
            ))
          }
        </Collapse>
      </div>
    );
  }
}

WdlTasks.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  onSelect: PropTypes.func,
  document: PropTypes.object
};

export default WdlTasks;
