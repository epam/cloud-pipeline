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
import classNames from 'classnames';
import moment from 'moment-timezone';
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import {
  message
} from 'antd';
import displayDate from '../../../../../../utils/displayDate';
import DataStorageLifeCycleRulesLoad, {STATUS}
  from '../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import styles from './life-cycle-counter.css';

@observer
class LifeCycleCounter extends React.Component {
  state={
    rulesAmount: undefined
  }

  componentDidMount () {
    const {visible} = this.props;
    if (visible) {
      this.fetchRulesInfo();
    }
  }

  componentDidUpdate (prevProps) {
    const {visible, storage, path} = this.props;
    if (
      visible &&
      (storage !== prevProps.storage || path !== prevProps.path)
    ) {
      this.fetchRulesInfo();
    }
  }

  @computed
  get folderRestorationInfo () {
    const {restoreInfo} = this.props;
    if (restoreInfo && restoreInfo.parentRestore) {
      return restoreInfo.parentRestore;
    }
    return undefined;
  }

  get isS3Storage () {
    const {storage} = this.props;
    return storage &&
      storage.id &&
      /^s3$/i.test(storage.storageType || storage.type);
  }

  fetchRulesInfo = async () => {
    const {path, storage} = this.props;
    const request = new DataStorageLifeCycleRulesLoad(storage.id, path);
    await request.fetch();
    if (request.error) {
      return message.error(request.error, 5);
    }
    this.setState({
      rulesAmount: (request.value || []).length
    });
  };

  renderRestoreActions = () => {
    const {onClickRestore} = this.props;
    if (!this.folderRestorationInfo) {
      return (
        <a
          className={classNames(
            styles.restoreBtn,
            'cp-link'
          )}
          onClick={onClickRestore}
        >
          Restore files
        </a>
      );
    }
    const {restoredTill, status} = this.folderRestorationInfo;
    if (status === STATUS.SUCCEEDED) {
      return (
        <div>
          <span
            style={{marginRight: '3px'}}
          >
            Restore is completed.
          </span>
          {restoredTill ? (
            <span>
              Folder is restored till {displayDate(moment.utc(restoredTill))}.
            </span>
          ) : null}
        </div>
      );
    }
    if (status === STATUS.INITIATED || status === STATUS.RUNNING) {
      return (
        <span>
          Restore process is running...
        </span>
      );
    }
  };

  render () {
    const {rulesAmount} = this.state;
    const {
      restoreEnabled,
      visible
    } = this.props;
    if (!this.isS3Storage || !visible) {
      return null;
    }
    return (
      <div className={styles.container}>
        <div>
          <span>
            Transition rules:
          </span>
          <b style={{margin: '0px 3px'}}>
            {rulesAmount}
          </b>
          <span>
            rule{rulesAmount === 1 ? '' : 's'} for the folder.
          </span>
        </div>
        <div>
          {restoreEnabled
            ? this.renderRestoreActions()
            : null
          }
        </div>
      </div>
    );
  }
}

LifeCycleCounter.propTypes = {
  storage: PropTypes.object,
  path: PropTypes.string,
  onClickRestore: PropTypes.func,
  restoreInfo: PropTypes.shape({
    parentRestore: PropTypes.object,
    currentRestores: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
  }),
  restoreEnabled: PropTypes.bool,
  visible: PropTypes.bool
};

export default LifeCycleCounter;
