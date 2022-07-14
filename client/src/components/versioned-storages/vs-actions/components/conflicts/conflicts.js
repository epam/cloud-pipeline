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
import {observable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import {Icon, Table} from 'antd';
import classNames from 'classnames';
import ChangesDisplayConfig from './controls/changes-display-config';
import ConflictsSession from './session';
import Conflict from './conflict';
import styles from './conflicts.css';

function getGridTemplateColumns (filesWidth = 200) {
  return `[FILES] ${filesWidth}px [DIVIDER] 10px [IDE] 1fr`;
}

class Conflicts extends React.Component {
  state = {
    current: undefined,
    resolved: false,
    filesWidth: 200
  };

  @observable session = new ConflictsSession();
  @observable changesDisplayConfig;
  ideContainer;
  ide;
  resizeInfo;

  constructor (props) {
    super(props);
    this.changesDisplayConfig = new ChangesDisplayConfig(this.props.themes);
  }

  componentDidMount () {
    this.updateFromProps();
    this.updateResolvedState();
    window.addEventListener('mousemove', this.resize);
    window.addEventListener('mouseup', this.finishResize);
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.resize);
    window.removeEventListener('mouseup', this.finishResize);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.run !== this.props.run ||
      prevProps.storage !== this.props.storage ||
      prevProps.conflicts !== this.props.conflicts ||
      prevProps.mergeInProgress !== this.props.mergeInProgress
    ) {
      this.updateFromProps();
    }
    if (this.session.resolved !== this.state.resolved) {
      this.updateResolvedState();
    } else if (this.session.resolved) {
      this.reportSessionState();
    }
  }

  updateFromProps = () => {
    const {
      conflicts,
      conflictsInfo,
      run,
      storage,
      mergeInProgress
    } = this.props;
    this.session.setFiles(
      run,
      storage,
      mergeInProgress,
      conflicts,
      conflictsInfo
    );
    this.setState({
      current: (conflicts || [])[0]
    });
  };

  initializeIDEContainer = ideContainer => {
    this.ideContainer = ideContainer;
  };

  initializeIDE = ide => {
    this.ide = ide;
  };

  startResize = (e) => {
    if (e.nativeEvent.button === 0) {
      const {filesWidth} = this.state;
      e.stopPropagation();
      e.preventDefault();
      this.resizeInfo = {
        x: e.clientX,
        width: filesWidth
      };
    }
  };

  resize = (e) => {
    if (this.resizeInfo && this.ideContainer) {
      e.stopPropagation();
      e.preventDefault();
      const {
        x,
        width
      } = this.resizeInfo;
      const delta = e.clientX - x;
      const min = 200;
      const max = this.ideContainer.clientWidth / 2.0;
      const correctSize = size => Math.min(
        max,
        Math.max(
          min,
          size
        )
      );
      const newFilesPanelWidth = correctSize(width + delta);
      if (this.ideContainer) {
        this.ideContainer.style['grid-template-columns'] =
          getGridTemplateColumns(newFilesPanelWidth);
      }
      this.resizeInfo.newWidth = newFilesPanelWidth;
      if (this.ide && typeof this.ide.updateScrollBars === 'function') {
        this.ide.updateScrollBars();
      }
    }
  };

  finishResize = (e) => {
    if (this.resizeInfo) {
      this.resize(e);
      const {newWidth} = this.resizeInfo;
      this.resizeInfo = null;
      this.setState({
        filesWidth: newWidth || 200
      });
      if (this.ide && typeof this.ide.updateScrollBars === 'function') {
        this.ide.updateScrollBars();
      }
    }
  };

  updateResolvedState = () => {
    this.setState({
      resolved: this.session.resolved
    }, this.reportSessionState);
  };

  reportSessionState = () => {
    const {onSessionStateChanged} = this.props;
    onSessionStateChanged && onSessionStateChanged(this.session);
  };

  renderFiles = () => {
    const {current} = this.state;
    const tableColumns = [
      {
        key: 'file',
        dataIndex: 'name'
      },
      {
        key: 'status',
        className: styles.resolvedCell,
        dataIndex: 'resolved',
        render: resolved => {
          if (resolved) {
            return (
              <Icon
                className="cp-success"
                type="check-circle"
              />
            );
          }
          return null;
        }
      }
    ];
    const data = (this.session?.files || []).map(file => ({
      name: file.file,
      resolved: file.resolved
    }));
    return (
      <Table
        className={styles.filesTable}
        showHeader={false}
        columns={tableColumns}
        rowKey={file => file.name}
        dataSource={data}
        size="small"
        pagination={false}
        rowClassName={
          file => classNames(
            styles.file,
            {[styles.selectedFile]: file.name === current}
          )
        }
        onRowClick={file => this.setState({current: file.name})}
      />
    );
  };

  render () {
    const {
      current,
      filesWidth
    } = this.state;
    const {
      disabled,
      run,
      storage
    } = this.props;
    return (
      <div
        className={styles.conflictsIde}
        data-session-hash={this.session.hash}
        ref={this.initializeIDEContainer}
        style={{gridTemplateColumns: getGridTemplateColumns(filesWidth)}}
      >
        <div className={styles.files}>
          {this.renderFiles()}
        </div>
        <div
          className={classNames(styles.divider, 'cp-conflicts-divider')}
          onMouseDown={this.startResize}
        >
          <div>{'\u00A0'}</div>
        </div>
        <Provider
          conflictsSession={this.session}
          colorsConfig={this.changesDisplayConfig}
        >
          <div
            className={
              classNames(
                styles.resolveAreaContainer,
                'cp-conflicts-resolve-area-container'
              )
            }
          >
            {
              current && (
                <Conflict
                  disabled={disabled}
                  file={current}
                  run={run}
                  storage={storage}
                  onInitialized={this.initializeIDE}
                />
              )
            }
          </div>
        </Provider>
      </div>
    );
  }
}

Conflicts.propTypes = {
  conflicts: PropTypes.arrayOf(PropTypes.string),
  conflictsInfo: PropTypes.array,
  disabled: PropTypes.bool,
  onSessionStateChanged: PropTypes.func,
  mergeInProgress: PropTypes.bool,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object
};

export default inject('themes')(observer(Conflicts));
