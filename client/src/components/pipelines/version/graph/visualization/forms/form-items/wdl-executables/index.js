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
import {isTask, WdlEvent} from '../../../../../../../../utils/pipeline-builder';
import classNames from 'classnames';
import {Button, Collapse, Icon, Modal} from 'antd';
import WdlIssues from '../wdl-issues';
import {addCall, getEntityNameOptions, removeTask} from '../../utilities/workflow-utilities';
import styles from './wdl-executables.css';

class WdlExecutables extends React.Component {
  state = {
    executables: [],
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
    this.updateExecutables();
  };

  unsubscribeFromDocumentEvents = (doc) => {
    if (doc) {
      doc.off(WdlEvent.changed, this.onDocumentChanged, this);
    }
  };

  onDocumentChanged = () => {
    this.updateExecutables();
  };

  updateExecutables = () => {
    const {
      document: wdlDocument
    } = this.props;
    const {
      executables = []
    } = wdlDocument || {};
    this.setState({
      executables
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

  renderExecutableInfo = (executable) => {
    const info = [];
    if (executable && executable.executions && executable.executions.length > 0) {
      info.push(`calls: ${executable.executions.length}`);
    }
    if (executable && executable.issues && executable.issues.length > 0) {
      info.push(`issues: ${executable.issues.length}`);
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

  renderExecutableHeader = (executable) => {
    const {
      disabled,
      document: wdlDocument
    } = this.props;
    const {
      name,
      type
    } = getEntityNameOptions(executable, wdlDocument);
    return (
      <div className={styles.header}>
        <div className={styles.headerTitle}>
          {
            type && (
              <span style={{marginRight: 5}}>
                {type}
              </span>
            )
          }
          <b>{name}</b>
          {
            this.renderExecutableInfo(executable)
          }
        </div>
        {
          !disabled && isTask(executable) && executable.document === wdlDocument && (
            <div className={styles.actions}>
              <Button
                className={styles.action}
                type="danger"
                size="small"
                onClick={this.onRemoveTaskClicked(executable)}
              >
                <Icon type="delete" />
              </Button>
            </div>
          )
        }
      </div>
    );
  };

  renderExecutable = (executable) => {
    const {
      executions = [],
      issues = []
    } = executable;
    const {
      onSelect,
      disabled,
      document: wdlDocument
    } = this.props;
    const onSelectExecution = (execution) => {
      if (typeof onSelect === 'function') {
        onSelect(execution);
      }
    };
    const onCreateCall = () => {
      if (wdlDocument) {
        onSelectExecution(addCall(wdlDocument.requireWorkflow(), executable));
      }
    };
    const thisDocumentExecutions = executions.filter((o) => o.document === wdlDocument);
    return (
      <div className={styles.taskDetails}>
        {
          thisDocumentExecutions.length === 0 && (
            <div
              className={classNames('cp-text-not-important', styles.row)}
            >
              No calls specified.
            </div>
          )
        }
        {
          thisDocumentExecutions.length > 0 && (
            <div
              className={styles.row}
            >
              <b>Calls ({executions.length}):</b>
            </div>
          )
        }
        {
          thisDocumentExecutions.map((execution) => (
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
          !disabled && !!wdlDocument && (
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
              <b>Issues ({issues.length}):</b>
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
      executables,
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
          <b>Document tasks & workflows ({executables.length})</b>
        </div>
        <Collapse
          activeKey={expandedKeys}
          onChange={this.onExpandedKeysChanged}
          className="wdl-properties-collapse"
          bordered={false}
        >
          {
            executables.map((executable) => (
              <Collapse.Panel
                key={executable.uuid}
                header={this.renderExecutableHeader(executable)}
              >
                {this.renderExecutable(executable)}
              </Collapse.Panel>
            ))
          }
        </Collapse>
      </div>
    );
  }
}

WdlExecutables.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  onSelect: PropTypes.func,
  document: PropTypes.object
};

export default WdlExecutables;
