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
import VSDiff from '../../../../../models/versioned-storage/diff';
import FileDiffPresenter from './file-diff-presenter';
import styles from './diff.css';

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

function loadDiff (runId, storage, file) {
  return new Promise((resolve) => {
    const request = new VSDiff(runId, storage, file, true);
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

  componentDidMount () {
    this.fetchDiffs();
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

  fetchDiffs = () => {
    const {run, storage, fileDiffs} = this.props;
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
          loadDiff(run, storage, fileDiff.path)
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

  render () {
    const {
      visible
    } = this.props;
    const {
      files = [],
      diffs = {}
    } = this.state;
    return (
      <div className={styles.container}>
        {
          files.map((file) => (
            <FileDiffPresenter
              key={file}
              file={file}
              className={styles.file}
              type={diffs[file]?.status}
              binary={diffs[file]?.diff?.binary}
              raw={diffs[file]?.diff?.raw_output}
              visible={visible}
            />
          ))
        }
      </div>
    );
  }
}

GitDiff.propTypes = {
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  fileDiffs: PropTypes.array,
  visible: PropTypes.bool
};

GitDiff.defaultProps = {
  visible: true
};

export default GitDiff;
