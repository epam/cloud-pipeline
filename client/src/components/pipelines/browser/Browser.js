/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import connect from '../../../utils/connect';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import pipelines from '../../../models/pipelines/Pipelines';
import LoadingView from '../../special/LoadingView';
import AWSRegionTag from '../../special/AWSRegionTag';
import {
  Alert,
  Button,
  Col,
  Icon,
  Input,
  message,
  Popover,
  Row,
  Table,
  Tooltip
} from 'antd';
import dataStorages from '../../../models/dataStorage/DataStorages';
import {generateTreeData, ItemTypes} from '../model/treeStructureFunctions';
import highlightText from '../../special/highlightText';
import styles from './Browser.css';
import UserName from '../../special/UserName';

const MAX_INLINE_METADATA_KEYS = 10;

@localization.localizedComponent
@connect({
  pipelines,
  dataStorages
})
@roleModel.authenticationInfo
@inject(({pipelines, dataStorages}, params) => {
  let {browserLocation, onReloadTree} = params;
  browserLocation = browserLocation || 'pipelines';
  let folderRequest = pipelines;
  if (/^storages$/i.test(browserLocation)) {
    folderRequest = dataStorages;
  }
  return {
    folder: folderRequest,
    onReloadTree,
    browserLocation,
    pipelines,
    dataStorages
  };
})
@observer
export default class Folder extends localization.LocalizedReactComponent {
  state = {
    filter: undefined
  };

  componentDidUpdate (prevProps) {
    if (prevProps.browserLocation !== this.props.browserLocation) {
      // eslint-disable-next-line
      this.setState({filter: undefined});
    }
  }

  onFilterChanged = (e) => {
    this.setState({filter: e.target.value});
  };

  @computed
  get items () {
    const {folder, browserLocation} = this.props;
    if (folder.loaded) {
      const items = (folder.value || []).map(i => i);
      const payload = {};
      if (/^storages$/i.test(browserLocation)) {
        payload.storages = items;
      } else {
        payload.pipelines = items;
      }
      return generateTreeData(payload, true);
    }
    return [];
  }

  renderTreeItemType = (item) => {
    switch (item.type) {
      case ItemTypes.pipeline: return <Icon type="fork" />;
      case ItemTypes.storage:
        const style = {};
        if (item.sensitive) {
          style.color = '#ff5c33';
        }
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          return <Icon type="inbox" style={style} />;
        } else {
          return <Icon type="hdd" style={style} />;
        }
      default: return <div />;
    }
  };

  renderTreeItemActions = (item) => {
    const actions = [];
    switch (item.type) {
      case ItemTypes.pipeline:
        if (roleModel.executeAllowed(item)) {
          actions.push(
            <Button
              key="launch"
              id={`folder-item-${item.key}-launch-button`}
              size="small"
              type="primary"
              onClick={(e) => this.launchPipeline(item, e)}>
              RUN
            </Button>
          );
        }
        break;
    }
    if (actions.filter(action => !!action).length > 0) {
      return (
        <Row type="flex" justify="end">
          {actions}
        </Row>
      );
    } else {
      return <div />;
    }
  };

  launchPipeline = async (pipeline, event) => {
    if (event) {
      event.stopPropagation();
    }
    const hide = message.loading('Fetching versions info...', -1);
    const pipelineRequest = this.props.pipelines.getPipeline(pipeline.id);
    await pipelineRequest.fetchIfNeededOrWait();
    hide();
    if (pipelineRequest.error) {
      message.error(pipelineRequest.error, 5);
    } else if (pipelineRequest.value && pipelineRequest.value.currentVersion) {
      this.props.router.push(`/launch/${pipeline.id}/${pipelineRequest.value.currentVersion.name}`);
    } else {
      message.error('Error fetching last version info', 5);
    }
  };

  navigate = (item) => {
    this.props.router.push(item.url());
  };

  renderMetadataKeyValue = (key, value) => {
    return (
      <Tooltip key={key} overlay={
        <Row>
          <Row>{key}:</Row>
          <Row>{value}</Row>
        </Row>
      }>
        <div key={key} className={styles.metadataItemContainer}>
          <Row className={styles.metadataItemKey}>
            {key}
          </Row>
          <Row className={styles.metadataItemValue}>
            {value}
          </Row>
        </div>
      </Tooltip>
    );
  };

  renderMetadata = (metadata) => {
    if (!metadata) {
      return null;
    }
    const items = [];
    for (let key in metadata) {
      if (metadata.hasOwnProperty(key)) {
        items.push(this.renderMetadataKeyValue(key, metadata[key].value));
      }
    }
    if (items.length > MAX_INLINE_METADATA_KEYS) {
      return (
        <Row className="object-metadata">
          {items.slice(0, MAX_INLINE_METADATA_KEYS - 1)}
          <div style={{
            margin: 5,
            display: 'inline-block'
          }}>
            <Popover content={
              <div
                className={styles.metadataPopover}
                style={{minWidth: 300, display: 'flex', flexDirection: 'column'}}
              >
                {items}
              </div>
            }>
              <a>+{items.length - MAX_INLINE_METADATA_KEYS + 1} more</a>
            </Popover>
          </div>
        </Row>
      );
    } else {
      return (
        <Row className="object-metadata">
          {items}
        </Row>
      );
    }
  };

  renderContent = () => {
    const {browserLocation} = this.props;
    const isStorages = /^storages$/i.test(browserLocation);
    const {filter} = this.state;
    const items = this.items
      .filter((item) => !filter || item.name.toLowerCase().indexOf(filter.toLowerCase()) >= 0);
    const columns = [
      {
        key: 'type',
        className: styles.treeItemType,
        render: (item) => this.renderTreeItemType(item)
      },
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        render: (name, item) => {
          const nameSearch = highlightText(name, filter);
          let nameComponent;
          if (item.locked) {
            nameComponent = (
              <span>
                <Icon type="lock" />
                {
                  item.type === ItemTypes.storage && (
                    <AWSRegionTag
                      regionId={item.regionId}
                    />
                  )
                }
                <span className={`object-name ${styles.objectNameBold}`}>
                  {nameSearch}
                </span>
              </span>
            );
          } else {
            nameComponent = (
              <span>
                {
                  item.type === ItemTypes.storage && (
                    <AWSRegionTag
                      regionId={item.regionId}
                    />
                  )
                }
                <span className={`object-name ${styles.objectNameBold}`}>
                  {nameSearch}
                </span>
              </span>
            );
          }
          if (item.description || item.hasMetadata) {
            nameComponent = (
              <Row>
                <Row style={{marginTop: 2}}>{nameComponent}</Row>
                <Row
                  style={{
                    marginTop: 5
                  }}>
                  {
                    item.description && (
                      <Row
                        style={
                          item.hasMetadata
                            ? {marginBottom: 5, wordWrap: 'normal'}
                            : {wordWrap: 'normal'}
                        }
                        className="object-description"
                      >
                        {item.description}
                      </Row>
                    )
                  }
                  {
                    this.renderMetadata(item.objectMetadata)
                  }
                </Row>
              </Row>
            );
          }
          return nameComponent;
        }
      },
      {
        key: 'owner',
        dataIndex: 'owner',
        width: 150,
        render: (owner) => <UserName userName={owner} />
      },
      isStorages
        ? false
        : {
          key: 'actions',
          width: 75,
          render: (item) => this.renderTreeItemActions(item)
        }
    ].filter(Boolean);
    return (
      <Table
        className={`${styles.childrenContainer} ${styles.childrenContainerLarger}`}
        dataSource={items}
        columns={columns}
        rowKey={(item) => item.key}
        title={null}
        showHeader={false}
        rowClassName={(row) => `folder-item-${row.key}`}
        expandedRowRender={null}
        loading={this.props.folder.pending}
        pagination={{pageSize: 40}}
        locale={{emptyText: 'Folder is empty'}}
        onRowClick={(item) => {
          this.navigate(item);
        }}
        size="small" />
    );
  };

  render () {
    if (!this.props.folder.pending && this.props.folder.error) {
      return <Alert message={this.props.folder.error} type="error" />;
    }
    if (!this.props.folder.loaded && this.props.folder.pending) {
      return <LoadingView />;
    }
    const {browserLocation} = this.props;
    const isStorages = /^storages$/i.test(browserLocation);
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
          <Col className={styles.itemHeader}>
            <Icon
              className={styles.editableControl}
              style={{marginRight: 5}}
              type={isStorages ? 'hdd' : 'fork'}
            />
            <span>
              {isStorages ? 'All storages' : `All ${this.localizedString('pipeline')}s`}
            </span>
          </Col>
        </Row>
        <Row
          type="flex"
          align="middle"
          style={{margin: '5px 0px'}}
        >
          <Input
            value={this.state.filter}
            onChange={this.onFilterChanged}
            placeholder={isStorages ? 'Search storages' : `Search ${this.localizedString('pipeline')}s`}
          />
        </Row>
        {this.renderContent()}
      </div>
    );
  }
}
