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
import {observer} from 'mobx-react';
import {Button, Icon} from 'antd';
import {HeadBranch, RemoteBranch} from './utilities/conflicted-file/branches';
import styles from './conflicts.css';

class BinaryFileConflict extends React.PureComponent {
  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
  }

  componentWillUnmount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(undefined);
  }

  render () {
    const {
      conflictedFile
    } = this.props;
    if (!conflictedFile) {
      return null;
    }
    return (
      <div className={styles.binaryFileContainer}>
        <div className={styles.alert}>
          <span>This is <b>binary</b> file. You can</span>
          <Button
            type={conflictedFile.acceptedBranch === HeadBranch ? 'primary' : 'default'}
            onClick={() => conflictedFile.acceptBranch(HeadBranch)}
          >
            {
              conflictedFile.acceptedBranch === HeadBranch && (
                <Icon type="check" />
              )
            }
            Accept yours
          </Button>
          <span>version or</span>
          <Button
            type={conflictedFile.acceptedBranch === RemoteBranch ? 'primary' : 'default'}
            onClick={() => conflictedFile.acceptBranch(RemoteBranch)}
          >
            {
              conflictedFile.acceptedBranch === RemoteBranch && (
                <Icon type="check" />
              )
            }
            Take remote
          </Button>
          <span>one.</span>
        </div>
      </div>
    );
  }
}

BinaryFileConflict.propTypes = {
  disabled: PropTypes.bool,
  conflictedFile: PropTypes.object,
  onInitialized: PropTypes.func
};

export default observer(BinaryFileConflict);
