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
import classNames from 'classnames';
import {Button, Collapse} from 'antd';

import VSDiff from '../../../../../models/versioned-storage/diff';
import VSConflictDiff from '../../../../../models/versioned-storage/conflict-diff';
import FileDiffPresenter from './file-diff-presenter';
import styles from './diff.css';

const IGNORED = 'ignored';

function mapFileDiffDescription (diff) {
  return `${diff.status}: ${diff.path}`;
}

function mapFileDiffDescriptions (diffs) {
  return (diffs || []).map(mapFileDiffDescription);
}

function compareFiles (a, b) {
  const filesA = mapFileDiffDescriptions(a).sort();
  const filesB = mapFileDiffDescriptions(b).sort();
  if (filesA.length !== filesB.length) {
    return false;
  }
  for (let i = 0; i < filesA.length; i++) {
    if (filesA[i] !== filesB[i]) {
      return false;
    }
  }
  return true;
}

function loadDiff (runId, storage, file, mergeInProgress) {
  return new Promise((resolve) => {
    const {path, status} = file || {};
    const request = /^conflicts$/i.test(status)
      ? new VSConflictDiff(runId, storage, path, {raw: true, mergeInProgress})
      : new VSDiff(runId, storage, path, true);
    request
      .fetch()
      .then(() => {
        if (request.error || !request.loaded) {
          resolve({
            file,
            error: request.error || 'Error fetching diff'
          });
        } else {
          resolve({
            file,
            diff: request.value
          });
        }
      })
      .catch(e => {
        resolve({
          file,
          error: e.message
        });
      });
  });
}

class GitDiff extends React.Component {
  state = {
    files: [],
    diffs: {}
  };

  async componentDidMount () {
    await this.fetchDiffs();
    this.onSelectAll();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.run !== this.props.run ||
      prevProps.storage !== this.props.storage ||
      !compareFiles(prevProps.fileDiffs, this.props.fileDiffs)
    ) {
      this.fetchDiffs();
    }
  }

  get groupedFiles () {
    const {diffs, files} = this.state;
    return files.reduce((all, f) => {
      diffs[f] && diffs[f].status === IGNORED ? all.ignored.push(f) : all.tracked.push(f);
      return all;
    }, {ignored: [], tracked: []});
  }

  fetchDiffs = () => {
    const {
      run,
      storage,
      fileDiffs,
      mergeInProgress
    } = this.props;
    if (run && storage && fileDiffs) {
      this.setState({
        files: (fileDiffs || []).map(f => f.path),
        diffs: (fileDiffs || [])
          .reduce((res, cur) => ({
            ...res,
            [cur.path]: {
              pending: true,
              error: undefined,
              diff: undefined,
              status: cur.status
            }
          }), {})
      }, () => {
        const fetchSingleFileDiff = (fileDiff) => {
          loadDiff(run, storage, fileDiff, mergeInProgress)
            .then(result => {
              const {diffs} = this.state;
              const {error, diff} = result;
              const newDiffs = {
                ...diffs,
                [fileDiff.path]: {
                  pending: false,
                  error,
                  diff,
                  status: fileDiff.status
                }
              };
              this.setState({diffs: newDiffs});
            });
        };
        (fileDiffs || []).forEach(fetchSingleFileDiff);
      });
    }
  };

  onOpenedChange = (keys) => {
    this.setState({
      opened: (keys || []).length > 0
    });
  }

  onSelectAll = () => {
    const {onSelectionChanged} = this.props;
    onSelectionChanged && onSelectionChanged(this.groupedFiles.tracked);
  }

  onClearAll = () => {
    const {onSelectionChanged} = this.props;
    onSelectionChanged && onSelectionChanged([]);
  }

  onSelectionChanged = (fileName) => () => {
    const {selectedFiles, onSelectionChanged} = this.props;
    let files = [...selectedFiles];
    if (files.includes(fileName)) {
      files = selectedFiles.filter(file => file !== fileName);
    } else {
      files.push(fileName);
    }
    onSelectionChanged && onSelectionChanged(files);
  }
  render () {
    const {
      visible,
      collapsed,
      className,
      style,
      selectable,
      selectedFiles = []
    } = this.props;
    const {
      diffs = {},
      opened
    } = this.state;
    const allFilesSelected = selectedFiles.length === this.groupedFiles.tracked.length;
    return (
      <div className={styles.container}>
        <div className={styles.selectionActions}>
          <h3 className={styles.listTitle}>Select files to commit changes</h3>
          <div >
            <Button onClick={this.onSelectAll} disabled={allFilesSelected}>Select All</Button>
            <Button onClick={this.onClearAll} disabled={!selectedFiles.length}>Clear selection</Button>
          </div>
        </div>

        {
          this.groupedFiles.tracked
            .map((file) => (
              <FileDiffPresenter
                key={file}
                file={file}
                className={classNames(styles.file, className)}
                style={style}
                type={diffs[file]?.status}
                binary={diffs[file]?.diff?.binary}
                // eslint-disable-next-line camelcase
                raw={diffs[file]?.diff?.raw_output}
                visible={visible}
                collapsed={collapsed}
                selectable={selectable}
                onSelectionChanged={this.onSelectionChanged}
                selectedFiles={selectedFiles}
              />
            ))
        }
        <div className={classNames(styles.file, className)}>
          <Collapse
            className={classNames(
              'git-diff-collapse',
              'cp-git-diff-collapse',
              {'git-diff-selectable': selectable},
              {'git-diff-unselectable': !selectable}
            )}
            activeKey={opened ? [IGNORED] : []}
            onChange={this.onOpenedChange}
          >
            <Collapse.Panel
              header={<div className={styles.fileDiffHeader}>Ignored files</div>}
              key={IGNORED}
            >
              <ul className={styles.ignoredList}>
                {
                  this.groupedFiles.ignored
                    .map((file) => (<li key={file}>{file}</li>))
                }
              </ul>
            </Collapse.Panel>
          </Collapse>
        </div>
      </div>
    );
  }
}

GitDiff.propTypes = {
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  mergeInProgress: PropTypes.bool,
  fileDiffs: PropTypes.array,
  visible: PropTypes.bool,
  collapsed: PropTypes.bool,
  selectable: PropTypes.bool,
  className: PropTypes.string,
  style: PropTypes.object,
  onSelectionChanged: PropTypes.func,
  selectedFiles: PropTypes.arrayOf(PropTypes.string)
};

GitDiff.defaultProps = {
  visible: true,
  collapsed: false,
  selectable: false
};

export default GitDiff;
