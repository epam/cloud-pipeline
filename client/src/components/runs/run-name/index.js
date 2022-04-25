/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {message} from 'antd';
import RunName from './run-name';
import PipelineRunTagsUpdate from '../../../models/pipelines/PipelineRunTagsUpdate';

class RunNameAutoUpdate extends React.PureComponent {
  state = {
    pending: false
  };

  get runId () {
    const {id, run} = this.props;
    if (id) {
      return id;
    }
    if (run) {
      return run.id;
    }
    return undefined;
  }

  onChange = (newAlias) => {
    this.setState(
      {pending: true},
      async () => {
        const hide = message.loading(
          newAlias
            ? (<span>Changing run alias to <b>{newAlias}</b>...</span>)
            : (<span>Removing run alias</span>),
          0
        );
        this.setState({pending: true});
        try {
          const request = new PipelineRunTagsUpdate(this.runId, false);
          await request.send({tags: {alias: newAlias}});
          const {onRefresh} = this.props;
          if (onRefresh && typeof onRefresh.then === 'function') {
            await onRefresh();
          } else if (onRefresh && typeof onRefresh === 'function') {
            onRefresh();
          }
        } catch (error) {
          message.error(error.message, 5);
        } finally {
          hide();
          this.setState({pending: false});
        }
      }
    );
  };

  render () {
    const {
      alias,
      run,
      children,
      className,
      style,
      editable,
      ignoreOffset
    } = this.props;
    const {
      pending
    } = this.state;
    return (
      <RunName
        alias={alias}
        run={run}
        className={className}
        style={style}
        ignoreOffset={ignoreOffset}
        editable={editable}
        disabled={pending}
        onChange={this.onChange}
      >
        {children}
      </RunName>
    );
  }
}

const propTypes = {
  id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  alias: PropTypes.string,
  run: PropTypes.object,
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object,
  editable: PropTypes.bool,
  ignoreOffset: PropTypes.bool,
  onRefresh: PropTypes.func
};

RunNameAutoUpdate.propTypes = propTypes;

RunName.AutoUpdate = RunNameAutoUpdate;
RunName.AutoUpdate.propTypes = propTypes;

export default RunName;
