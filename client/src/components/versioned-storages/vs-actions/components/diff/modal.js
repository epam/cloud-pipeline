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
import {Modal} from 'antd';
import GitDiff from './git-diff';

class GitDiffModal extends React.Component {
  getTitle = () => {
    const {storage} = this.props;
    if (storage) {
      return (
        <span>
          <b>{storage.name}</b>: diff
        </span>
      );
    }
    return 'Diff';
  };

  render () {
    const {
      fileDiffs,
      onClose,
      mergeInProgress,
      run,
      storage,
      visible
    } = this.props;
    return (
      <Modal
        title={this.getTitle()}
        visible={visible}
        onCancel={onClose}
        width="80%"
        footer={false}
      >
        <GitDiff
          fileDiffs={fileDiffs}
          run={run}
          storage={storage?.id}
          mergeInProgress={mergeInProgress}
          visible={visible}
        />
      </Modal>
    );
  }
}

GitDiffModal.propTypes = {
  fileDiffs: PropTypes.array,
  onClose: PropTypes.func,
  mergeInProgress: PropTypes.bool,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object,
  visible: PropTypes.bool
};

export default GitDiffModal;
