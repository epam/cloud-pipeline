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
import {Alert, Button, Checkbox, Icon, message, Modal, Table} from 'antd';
import classNames from 'classnames';
import {observer} from 'mobx-react';
import {loadAvailablePipelineGroups} from '../../model/analysis/analysis-pipeline-management';
import LoadingView from '../../../LoadingView';
import UserName from '../../../UserName';
import styles from '../cell-profiler.css';

const PAGE_SIZE = 20;

@observer
class OpenPipelineModal extends React.Component {
  state = {
    pipelineFiles: [],
    selectedPipeline: undefined,
    pending: false,
    error: undefined,
    page: 0,
    opening: false,
    expandedKeys: []
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
  }

  updatePipelineFilesList = () => {
    this.setState({
      pending: true,
      selectedPipeline: undefined,
      expandedKeys: []
    }, () => {
      loadAvailablePipelineGroups()
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
      selectedPipeline,
      expandedKeys
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
      pipeline.path === selectedPipeline.path &&
      pipeline.version === selectedPipeline.version;
    const togglePipeline = (pipelineFile, e) => {
      const {pipeline} = pipelineFile || {};
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
          if (obj.loading) {
            return (<Icon type="loading" />);
          }
          return (
            <span>
              <Checkbox
                style={{marginRight: 5}}
                checked={pipelineIsSelected(obj.pipeline)}
                onChange={(e) => togglePipeline(obj, e)}
              />
              {name}
            </span>
          );
        }
      },
      {
        key: 'author',
        title: 'Author',
        className: styles.analysisPipelineCell,
        render: (obj) => {
          if (obj.loading) {
            return null;
          }
          return (
            <UserName
              userName={obj.pipeline ? obj.pipeline.author : undefined}
              showIcon
            />
          );
        }
      },
      {
        key: 'description',
        title: 'Description',
        className: styles.analysisPipelineCell,
        render: (obj) => obj.loading || !obj.pipeline ? undefined : obj.pipeline.description
      }
    ];
    const onChange = (page) => this.setState({page: page - 1});
    const onExpand = (expanded, pipeline) => {
      if (expanded) {
        pipeline
          .loadVersions()
          .then(() => this.forceUpdate())
          .catch(() => {});
      }
    };
    return (
      <Table
        columns={columns}
        dataSource={pipelineFiles}
        loading={pending}
        rowKey="key"
        size="small"
        rowClassName={() => classNames(styles.analysisPipelineRow)}
        onRowClick={(pipeline, opts, event) => togglePipeline(pipeline, event)}
        onExpand={onExpand}
        expandedRowKeys={expandedKeys}
        onExpandedRowsChange={keys => this.setState({expandedKeys: keys})}
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

  loadSelectedPipeline = async () => {
    const {
      selectedPipeline,
      pipelineFiles = []
    } = this.state;
    if (!selectedPipeline) {
      return undefined;
    }
    const {
      path,
      version
    } = selectedPipeline;
    const pipelineGroup = pipelineFiles.find(o => o.path === path);
    if (!pipelineGroup) {
      return undefined;
    }
    if (pipelineGroup.version === version) {
      return pipelineGroup.load();
    }
    const {children = []} = pipelineGroup;
    const specificVersion = children.find(o => o.pipeline && o.pipeline.version === version);
    if (!specificVersion) {
      return undefined;
    }
    return specificVersion.load();
  };

  onOpenClicked = async () => {
    const {onSelect} = this.props;
    const {
      selectedPipeline
    } = this.state;
    if (typeof onSelect === 'function' && selectedPipeline) {
      const hide = message.loading('Opening...');
      this.setState({opening: true});
      try {
        const opened = await this.loadSelectedPipeline();
        if (!opened) {
          throw new Error('Error loading pipeline version');
        }
        onSelect(opened);
      } catch (e) {
        message.error(e.message);
      } finally {
        hide();
        this.setState({opening: false});
      }
    }
  };

  render () {
    const {
      visible,
      onClose
    } = this.props;
    const {
      selectedPipeline,
      opening
    } = this.state;
    return (
      <Modal
        visible={visible}
        width="50%"
        title="Open analysis pipeline"
        onCancel={onClose}
        closable={!opening}
        maskClosable={!opening}
        footer={(
          <div className={styles.modalFooter}>
            <Button
              onClick={onClose}
              disabled={opening}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!selectedPipeline || opening}
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
