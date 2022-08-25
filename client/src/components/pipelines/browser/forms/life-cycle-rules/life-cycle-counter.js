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
import {message} from 'antd';
import DataStorageLifeCycleRulesLoad
  from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';

class LifeCycleCounter extends React.Component {
  state={
    rulesAmount: undefined
  }

  componentDidMount () {
    this.fetchRulesInfo();
  }

  componentDidUpdate (prevProps) {
    if (this.props.storage !== prevProps.storage ||
      this.props.path !== prevProps.path
    ) {
      this.fetchRulesInfo();
    }
  }

  get showInfo () {
    const {storage} = this.props;
    const {rulesAmount} = this.state;
    if (storage && rulesAmount) {
      return /^s3$/i.test(storage.type);
    }
    return false;
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

  render () {
    const {rulesAmount} = this.state;
    if (!this.showInfo) {
      return null;
    }
    return (
      <div>
        <span>
          Transition rules:
        </span>
        <b style={{margin: '0px 3px'}}>
          {rulesAmount}
        </b>
        <span>
          rules for the folder.
        </span>
      </div>
    );
  }
}

LifeCycleCounter.propTypes = {
  storage: PropTypes.object,
  path: PropTypes.string
};

export default LifeCycleCounter;
