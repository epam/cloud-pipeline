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
import {Alert, Button, Checkbox, Modal, Table} from 'antd';
import classNames from 'classnames';
import {observer} from 'mobx-react';
import {
  loadAvailablePipelines,
  CP_CELLPROFILER_PIPELINE_NAME,
  CP_CELLPROFILER_PIPELINE_DESCRIPTION
} from '../model/analysis/analysis-pipeline-management';
import LoadingView from '../../LoadingView';
import styles from './cell-profiler.css';
import UserName from '../../UserName';

const PAGE_SIZE = 20;

@observer
class OpenPipelineModal extends React.Component {
  state = {
    pipelineFiles: [],
    selectedPipeline: undefined,
    pending: false,
    error: undefined,
    page: 0
  };

  componentDidMount () {
    const {visible} = this.props;
    if (visible) {
      this.updatePipelineFilesList();
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {visible} = this.props;
    if (visible && prevProps.visible !== visible) {
      this.updatePipelineFilesList();
    }
    if (
      prevState.page !== this.state.page ||
      prevState.pipelineFiles !== this.state.pipelineFiles
    ) {
      this.loadPipelinesInfo();
    }
  }

  loadPipelinesInfo = () => {
    const {
      page,
      pipelineFiles = []
    } = this.state;
    Promise.all(
      pipelineFiles
        .slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
        .filter(pipelineFile => !pipelineFile.pipeline)
        .map(pipelineFile => pipelineFile.load())
    )
      .then(results => {
        if (results.length > 0) {
          this.forceUpdate();
        }
      });
  };

  updatePipelineFilesList = () => {
    this.setState({
      pending: true
    }, () => {
      loadAvailablePipelines()
        .then(pipelineFiles => Promise.all(
          pipelineFiles.map(pipelineFile => pipelineFile.load())
        ))
        .then(pipelineFiles => this.setState({
          pipelineFiles,
          error: undefined,
          pending: false,
          page: 0
        }))
        .catch(error => this.setState({error: error.message, pending: false}));
    });
  };

  renderPipelinesTable = () => {
    const {
      pipelineFiles = [],
      error,
      page,
      pending,
      selectedPipeline
    } = this.state;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    if (pipelineFiles.length === 0 && pending) {
      return (<LoadingView />);
    }
    if (pipelineFiles.length === 0) {
      return (
        <Alert message="Analysis pipelines not found" type="info" />
      );
    }
    const pipelineIsSelected = pipeline => pipeline &&
      selectedPipeline &&
      pipeline.path === selectedPipeline.path;
    const togglePipeline = (pipeline, e) => {
      if (e) {
        e.stopPropagation();
      }
      if (pipelineIsSelected(pipeline)) {
        this.setState({selectedPipeline: undefined});
      } else {
        this.setState({selectedPipeline: pipeline});
      }
    };
    const columns = [
      {
        key: 'name',
        dataIndex: 'name',
        title: 'Name',
        className: classNames(styles.analysisPipelineCell, styles.analysisPipelineCellName),
        render: (name, obj) => {
          let pipelineName = name;
          if (obj.pipeline) {
            pipelineName = obj.pipeline.name;
          } else if (obj.info && obj.info[CP_CELLPROFILER_PIPELINE_NAME]) {
            pipelineName = obj.info[CP_CELLPROFILER_PIPELINE_NAME];
          }
          return (
            <span>
              <Checkbox
                style={{marginRight: 5}}
                checked={pipelineIsSelected(obj)}
                onChange={(e) => togglePipeline(obj, e)}
              />
              {pipelineName}
            </span>
          );
        }
      },
      {
        key: 'author',
        title: 'Author',
        className: styles.analysisPipelineCell,
        render: (obj) => {
          let owner;
          if (obj.pipeline) {
            owner = obj.pipeline.author;
          } else if (obj.info && obj.info.CP_OWNER) {
            owner = obj.info.CP_OWNER;
          }
          if (owner) {
            return (
              <UserName
                userName={owner}
                showIcon
              />
            );
          }
          return undefined;
        }
      },
      {
        key: 'description',
        title: 'Description',
        className: styles.analysisPipelineCell,
        render: (obj) => {
          let description;
          if (obj.pipeline) {
            description = obj.pipeline.description;
          } else if (obj.info && obj.info[CP_CELLPROFILER_PIPELINE_DESCRIPTION]) {
            description = obj.info[CP_CELLPROFILER_PIPELINE_DESCRIPTION];
          }
          return description;
        }
      }
    ];
    const onChange = (page) => this.setState({page: page - 1});
    return (
      <Table
        columns={columns}
        dataSource={pipelineFiles}
        loading={pending}
        rowKey="path"
        size="small"
        rowClassName={() => classNames(styles.analysisPipelineRow)}
        onRowClick={(pipeline, opts, event) => togglePipeline(pipeline, event)}
        bordered={false}
        pagination={{
          total: pipelineFiles.length,
          pageSize: PAGE_SIZE,
          page: page + 1,
          onChange
        }}
      />
    );
  };

  onOpenClicked = () => {
    const {onSelect} = this.props;
    const {selectedPipeline} = this.state;
    if (typeof onSelect === 'function' && selectedPipeline) {
      onSelect(selectedPipeline);
    }
  };

  render () {
    const {
      visible,
      onClose
    } = this.props;
    const {
      selectedPipeline
    } = this.state;
    return (
      <Modal
        visible={visible}
        width="50%"
        title="Open analysis pipeline"
        onCancel={onClose}
        footer={(
          <div className={styles.openPipelineModalFooter}>
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!selectedPipeline}
              onClick={this.onOpenClicked}
            >
              OPEN
            </Button>
          </div>
        )}
      >
        {this.renderPipelinesTable()}
      </Modal>
    );
  }
}

OpenPipelineModal.propTypes = {
  visible: PropTypes.bool,
  onSelect: PropTypes.func,
  onClose: PropTypes.func
};

export default OpenPipelineModal;
