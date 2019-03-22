/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import PipelineStorageRuleCreateDialog from './forms/PipelineStorageRuleCreateDialog';
import displayDate from '../../../../utils/displayDate';
import {message, Button, Checkbox, Modal, Row, Table} from 'antd';
import DataStorageRules from '../../../../models/dataStorage/DataStorageRules';
import styles from './PipelineStorageRules.css';
import roleModel from '../../../../utils/roleModel';

@inject(({pipelines, routing}, {params}) => ({
  rules: new DataStorageRules(params.id),
  pipelineId: params.id,
  pipeline: pipelines.getPipeline(params.id),
}))
@observer
export default class PipelineStorageRules extends React.Component {

  state = {createRuleDialogVisible: false};

  rulesTableColumns = [
    {
      dataIndex: 'fileMask',
      key: 'fileMask',
      title: 'Mask'
    },
    {
      dataIndex: 'createdDate',
      key: 'createdDate',
      title: 'Created',
      render: date => displayDate(date)
    },
    {
      dataIndex: 'moveToSts',
      key: 'moveToSts',
      title: 'Move to Short-Term Storage',
      render: (value) => {
        if (roleModel.writeAllowed(this.props.pipeline.value)) {
          return <Checkbox checked={value} disabled={true} />
        } else {
          return undefined;
        }
      }
    },
    {
      key: 'actions',
      title: '',
      render: (rule) => {
        if (roleModel.writeAllowed(this.props.pipeline.value)) {
          return <span><a onClick={() => this.deleteRuleDialog(rule)}>Delete</a></span>
        } else {
          return undefined;
        }
      }
    }
  ];

  deleteRuleDialog = (rule) => {
    Modal.confirm({
      title: `Do you want to delete rule "${rule.fileMask}"?`,
      style: {
        wordWrap: 'break-word'
      },
      content: null,
      okText: 'OK',
      cancelText: 'Cancel',
      onOk: async () => {
        await this.props.rules.deleteRule(rule);
        if (this.props.rules.deleteRuleLastError) {
          message.error(this.props.rules.deleteRuleLastError, 5);
        } else {
          message.destroy();
        }
      }
    });
  };

  createRule = async(rule) => {
    await this.props.rules.createRule(rule);
    if (this.props.rules.createRuleLastError) {
      message.error(this.props.rules.createRuleLastError, 5);
    } else {
      message.destroy();
      this.closeCreateRuleDialog();
    }
  };

  openCreateRuleDialog = () => {
    this.setState({createRuleDialogVisible: true});
  };

  closeCreateRuleDialog = () => {
    this.setState({createRuleDialogVisible: false});
  };

  render () {

    const header = (
      roleModel.writeAllowed(this.props.pipeline.value)
      ? (
        <Row type="flex" justify="end" style={{paddingRight: 5}}>
          <Button type="primary" onClick={this.openCreateRuleDialog}>Add new rule</Button>
        </Row>
      )
        : false
    );

    return (
      <Row className={styles.container} style={{overflowY: 'auto'}}>
        <Table
          className={styles.table}
          rowKey="fileMask"
          title={() => header}
          loading={this.props.rules.pending}
          columns={this.rulesTableColumns}
          dataSource={this.props.rules.list.map(i => i)}
          locale={{emptyText: 'Rules are not configured'}}
          pagination={{pageSize: 20}}
          size="small" />
        <PipelineStorageRuleCreateDialog visible={this.state.createRuleDialogVisible}
                                         onCancel={this.closeCreateRuleDialog}
                                         onSubmit={this.createRule}
                                         pending={this.props.rules.pending}
                                         pipelineId={this.props.pipelineId} />
      </Row>
    );
  }
}
