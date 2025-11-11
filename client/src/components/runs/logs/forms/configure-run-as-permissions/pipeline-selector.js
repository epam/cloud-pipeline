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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Icon,
  Select
} from 'antd';
import classNames from 'classnames';
import styles from './configure-run-as-permissions.css';

@inject('pipelines')
@observer
class PipelineSelector extends React.Component {
  state = {
    search: undefined
  }

  @computed
  get pipelines () {
    const {pipelines, pipelinesToExclude} = this.props;
    const isDisabled = (pipeline) => {
      return pipelinesToExclude &&
        pipelinesToExclude.length &&
        pipelinesToExclude.includes(pipeline.id);
    };
    if (pipelines.loaded) {
      return (pipelines.value || []).filter((pipeline) => !isDisabled(pipeline));
    }
    return [];
  }

  get filteredPipelines () {
    const {
      search
    } = this.state;
    const {pipelineId} = this.props;
    return this.pipelines.filter((pipeline) => pipeline.id === pipelineId ||
      (
        search &&
        search.length >= 3 &&
        pipeline.name.toLowerCase().includes(search.toLowerCase())
      ));
  }

  onChangePipeline = (pipelineId) => {
    const {pipelineId: currentPipelineId, onChange} = this.props;
    const id = Number.isNaN(Number(pipelineId)) ? undefined : Number(pipelineId);
    if (currentPipelineId !== id && onChange) {
      onChange(id);
    }
  };

  render () {
    const {
      disabled,
      duplicate,
      className,
      pipelines,
      style,
      containerStyle,
      onRemove,
      pipelineId
    } = this.props;
    const {search} = this.state;
    if (pipelines.error) {
      return null;
    }
    const notFoundContent = !search
      ? 'Start typing to filter pipelines...'
      : (search.length < 3
        ? 'Start typing to filter pipelines...' : 'Not found');
    return (
      <div
        className={className}
        style={style}
      >
        <div
          className={classNames(styles.container)}
          style={containerStyle}
        >
          <Select
            className={classNames({'cp-error': duplicate})}
            showSearch
            disabled={disabled}
            value={pipelineId === undefined || null ? undefined : `${pipelineId}`}
            onChange={this.onChangePipeline}
            onSearch={(e) => this.setState({search: e})}
            onFocus={() => this.setState({search: undefined})}
            placeholder="Pipeline"
            style={{flex: 1}}
            filterOption={false}
            getPopupContainer={node => node.parentNode}
            notFoundContent={notFoundContent}
          >
            {
              this.filteredPipelines.map(p => (
                <Select.Option
                  key={`${p.id}`}
                  value={`${p.id}`}
                >
                  {p.name}
                </Select.Option>
              ))
            }
          </Select>
          <Button
            disabled={disabled}
            size="small"
            type="danger"
            onClick={onRemove}
            className={styles.action}
          >
            <Icon type="delete" />
          </Button>
        </div>
      </div>
    );
  }
}

PipelineSelector.propTypes = {
  disabled: PropTypes.bool,
  duplicate: PropTypes.bool,
  className: PropTypes.string,
  pipelineId: PropTypes.number,
  style: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  containerStyle: PropTypes.object,
  pipelinesToExclude: PropTypes.arrayOf(PropTypes.number)
};

export default PipelineSelector;
