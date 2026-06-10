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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Alert, Button, Input, message, Modal, Spin, Splitter, Table, Tree} from 'antd';
import {FolderOutlined, LockOutlined, SolutionOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import {
  formatTreeItems,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes,
  search,
} from '../../model/treeStructureFunctions';
import LoadingView from '../../../special/LoadingView.tsx';
import MetadataEntityFilter from '../../../../models/folderMetadata/MetadataEntityFilter';
import MetadataClassLoadAll from '../../../../models/folderMetadata/MetadataClassLoadAll';
import MetadataEntityDelete from '../../../../models/folderMetadata/MetadataEntityDelete';
import MetadataEntitySave from '../../../../models/folderMetadata/MetadataEntitySave';
import styles from './CopyMetadataEntitiesDialog.module.css';
import HiddenObjects from '../../../../utils/hidden-objects';

const removeEntity = async (id) => {
  const removeRequest = new MetadataEntityDelete(id);
  await removeRequest.fetch();
  if (removeRequest.error) {
    return removeRequest.error;
  }
  return undefined;
};

class CopyMetadataEntitiesDialog extends React.Component {
  state = {
    disabled: false,
    expandedKeys: [],
    pending: false,
    selectedFolder: undefined,
    selectedKeys: [],
    tree: [],
    search: undefined,
  };

  componentDidMount() {
    this.updateStateFromProps();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateStateFromProps();
    }
  }

  updateStateFromProps = () => {
    const {pipelinesLibrary} = this.props;
    this.setState(
      {
        expandedKeys: [],
        pending: true,
        selectedFolder: undefined,
        selectedKeys: [],
      },
      () => {
        pipelinesLibrary.fetchIfNeededOrWait();
        const tree = generateTreeData(pipelinesLibrary.value || {}, {
          types: [ItemTypes.folder],
          filter: this.props.hiddenObjectsTreeFilter(),
        });
        this.setState({
          pending: false,
          tree,
        });
      },
    );
    this.setState({
      selectedFolder: undefined,
    });
  };

  onSearchChanged = (e) => {
    const {tree} = this.state;
    const searchCriteria = e.target.value;
    this.setState(
      {
        search: searchCriteria,
      },
      () => {
        search(searchCriteria, tree).then(() => {
          if (searchCriteria === this.state.search) {
            const expandedKeys = getExpandedKeys(tree);
            this.setState({
              tree,
              expandedKeys,
            });
          }
        });
      },
    );
  };

  renderFolderContent = () => {
    const {disabled, expandedKeys, pending, selectedFolder, tree} = this.state;
    if (!selectedFolder) {
      return <Alert type="info" title={<div>Select folder to copy entities</div>} />;
    }
    const columns = [
      {
        key: 'icon',
        className: styles.icon,
        render: (item) => {
          if (
            item.isProject ||
            (item.objectMetadata &&
              item.objectMetadata.type &&
              (item.objectMetadata.type.value || '').toLowerCase() === 'project')
          ) {
            return <SolutionOutlined />;
          } else {
            return <FolderOutlined />;
          }
        },
      },
      {
        key: 'name',
        dataIndex: 'name',
        className: styles.name,
      },
    ];
    const onSelect = (item) => {
      if (disabled) {
        return;
      }
      const treeItem = getTreeItemByKey(item.key, tree);
      this.setState({
        selectedKeys: [item.key],
        selectedFolder: treeItem,
        expandedKeys: [...new Set([...expandedKeys, item.key])],
      });
    };
    let IconComponent = FolderOutlined;
    if (
      selectedFolder.isProject ||
      (selectedFolder.objectMetadata &&
        selectedFolder.objectMetadata.type &&
        (selectedFolder.objectMetadata.type.value || '').toLowerCase() === 'project')
    ) {
      IconComponent = SolutionOutlined;
    }
    return (
      <div className={styles.treeContainer}>
        <div style={{fontSize: 'large', marginLeft: 5}}>
          <IconComponent />
          <b style={{marginLeft: 5}}>{selectedFolder.name}</b>
        </div>
        <Table
          className={styles.table}
          columns={columns}
          dataSource={(selectedFolder.children || []).map(({children, ...rest}) => rest)}
          loading={pending}
          pagination={false}
          rowClassName={() => styles.row}
          showHeader={false}
          onRow={(record) => ({onClick: () => onSelect(record)})}
          size="small"
        />
      </div>
    );
  };

  renderContent = () => {
    const {pipelinesLibrary} = this.props;
    const {
      disabled,
      expandedKeys = [],
      pending,
      selectedKeys = [],
      search: searchValue,
      tree,
    } = this.state;
    if (pipelinesLibrary.pending && !pipelinesLibrary.loaded) {
      return <LoadingView />;
    }
    if (pipelinesLibrary.error) {
      return <Alert type="error" title={pipelinesLibrary.error} />;
    }
    if (!pipelinesLibrary.loaded) {
      return <Alert type="error" title="Error fetching library" />;
    }
    const onSelect = (selection) => {
      const [selected] = selection || [];
      const newExpandedKeys = expandedKeys.slice();
      if (selected && expandedKeys.indexOf(selected) === -1) {
        newExpandedKeys.push(selected);
      }
      if (selected) {
        const selectedItem = getTreeItemByKey(selected, tree);
        this.setState({
          selectedFolder: selectedItem,
          selectedKeys: selectedItem ? [selectedItem.key] : [],
          expandedKeys: newExpandedKeys,
        });
      } else {
        this.setState({
          selectedFolder: undefined,
          expandedKeys: newExpandedKeys,
          selectedKeys: (selection || []).slice(),
        });
      }
    };
    const correctExpandedKeys = (keys) => {
      const result = [];
      const checkRecursively = (item) => {
        if (item && keys.indexOf(item.key) >= 0) {
          return item.parent ? checkRecursively(item.parent) : true;
        }
        return false;
      };
      for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const item = getTreeItemByKey(key, tree);
        if (checkRecursively(item)) {
          result.push(key);
        }
      }
      return result;
    };
    const onExpand = (keys) => {
      this.setState({
        expandedKeys: correctExpandedKeys(keys || []),
      });
    };
    const renderItemTitle = (item) => {
      let IconComponent;
      if (
        item.isProject ||
        (item.objectMetadata &&
          item.objectMetadata.type &&
          (item.objectMetadata.type.value || '').toLowerCase() === 'project')
      ) {
        IconComponent = SolutionOutlined;
      } else {
        IconComponent = FolderOutlined;
      }
      let {name} = item;
      if (item.searchResult) {
        name = (
          <span>
            <span>{item.name.substring(0, item.searchResult.index)}</span>
            <span className={classNames(styles.searchResult, 'cp-search-highlight-text')}>
              {item.name.substring(
                item.searchResult.index,
                item.searchResult.index + item.searchResult.length,
              )}
            </span>
            <span>{item.name.substring(item.searchResult.index + item.searchResult.length)}</span>
          </span>
        );
      }
      return (
        <span>
          {IconComponent && <IconComponent />}
          {item.locked ? <LockOutlined /> : null}
          <span className={styles.treeItemName}>{name}</span>
        </span>
      );
    };
    const getTreeData = (items) =>
      formatTreeItems(items, {preferences: this.props.preferences})
        .filter((item) => item.searchHit)
        .map((item) => ({
          key: item.key,
          title: renderItemTitle(item),
          isLeaf: item.isLeaf,
          className: styles.treeItem,
          ...(item.children && !item.isLeaf ? {children: getTreeData(item.children)} : {}),
        }));
    return (
      <Splitter>
        <Splitter.Panel defaultSize={300} min={200} resizable>
          <div key="library">
            <div>
              <Input.Search value={searchValue} onChange={this.onSearchChanged} />
            </div>
            <div className={styles.treeContainer}>
              <Spin spinning={pending}>
                <Tree
                  disabled={disabled}
                  treeData={getTreeData(tree)}
                  onSelect={onSelect}
                  onExpand={onExpand}
                  checkStrictly
                  expandedKeys={expandedKeys}
                  selectedKeys={selectedKeys}
                />
              </Spin>
            </div>
          </div>
        </Splitter.Panel>
        <Splitter.Panel resizable>
          <div key="content">{this.renderFolderContent()}</div>
        </Splitter.Panel>
      </Splitter>
    );
  };

  perform = async (folder, itemsToRemove, move = false) => {
    const {entities, folderId: initialFolderId, metadataClass, onCopy} = this.props;
    const classesRequest = new MetadataClassLoadAll();
    await classesRequest.fetch();
    let success = false;
    if (classesRequest.error) {
      message.error(`Error fetching metadata classes: ${classesRequest.error}`, 5);
    } else {
      const classes = classesRequest.value || [];
      const classObj = classes.find((c) => c.name === metadataClass);
      if (!classObj) {
        message.error(`${metadataClass} class not found`, 5);
      } else {
        const classId = classObj.id;
        const saveEntity = async (entity) => {
          const existingEntity = (itemsToRemove || []).find(
            (i) => i.externalId === (entity.ID ? entity.ID.value : undefined),
          );
          if (existingEntity) {
            const removeError = await removeEntity(existingEntity.id);
            if (removeError) {
              return removeError;
            }
          }
          const payload = {
            classId,
            className: metadataClass,
            externalId: entity.ID ? entity.ID.value : undefined,
            data: Object.keys(entity)
              .filter((k) => ['ID', 'createdDate', 'rowKey'].indexOf(k) === -1)
              .map((k) => ({[k]: entity[k]}))
              .reduce((r, c) => ({...r, ...c}), {}),
            parentId: folder,
          };
          const saveRequest = new MetadataEntitySave();
          await saveRequest.send(payload);
          return saveRequest.error;
        };
        const saveEntities = () => {
          return new Promise((resolve) => {
            Promise.all(entities.map(saveEntity)).then((errors) => {
              const saveErrors = errors.filter(Boolean);
              resolve(saveErrors);
            });
          });
        };
        const errors = await saveEntities();
        if (errors.length > 0) {
          message.error(
            <div>
              {errors.map((e, i) => (
                <div key={i}>{e}</div>
              ))}
            </div>,
            5,
          );
        } else {
          if (move && `${initialFolderId}` !== `${folder}`) {
            const ids = entities
              .map((e) => (e.rowKey ? e.rowKey.value : undefined))
              .filter(Boolean);
            const removeResult = await Promise.all(ids.map(removeEntity));
            const moveErrors = removeResult.filter(Boolean);
            if (moveErrors.length > 0) {
              message.error(
                <div>
                  {moveErrors.map((e, i) => (
                    <div key={i}>{e}</div>
                  ))}
                </div>,
                5,
              );
            } else {
              success = true;
            }
          } else {
            success = true;
          }
        }
      }
    }
    this.setState(
      {
        disabled: false,
      },
      () => {
        if (success && onCopy) {
          onCopy(folder);
        }
      },
    );
  };

  doOperation = (move = false) => {
    const {entities, metadataClass} = this.props;
    const {selectedFolder} = this.state;
    const checkIdentifiers = (folder, identifiers) => {
      const checkIdentifier = async (id) => {
        const check = new MetadataEntityFilter();
        await check.send({
          folderId: folder,
          externalIdQueries: [id],
          metadataClass,
          page: 1,
          pageSize: 1,
        });
        if (check.loaded && check.value && check.value.totalCount > 0) {
          return (check.value.elements || [])[0];
        }
        return undefined;
      };
      return new Promise((resolve) => {
        Promise.all(identifiers.filter(Boolean).map(checkIdentifier)).then((results) => {
          resolve(results.filter(Boolean));
        });
      });
    };
    const perform = (folder, itemsToRemove) => this.perform(folder, itemsToRemove, move);
    const cancel = () => {
      this.setState({
        disabled: false,
      });
    };
    this.setState(
      {
        disabled: true,
      },
      async () => {
        const folderId = selectedFolder.id;
        if (!folderId) {
          message.error(`Unknown parent folder for ${selectedFolder.name}`);
          this.setState({
            disabled: false,
          });
        } else {
          const duplicates = await checkIdentifiers(
            folderId,
            entities.map((e) => (e.ID ? e.ID.value : undefined)),
          );
          if (duplicates.length > 0) {
            Modal.confirm({
              title: 'Are you sure you want to override entities?',
              content: (
                <div>
                  <div>
                    <b>The following entities already exists:</b>
                  </div>
                  {duplicates.map((e) => (
                    <div key={e.externalId}>{e.externalId}</div>
                  ))}
                </div>
              ),
              style: {
                wordWrap: 'break-word',
              },
              async onOk() {
                return perform(folderId, duplicates);
              },
              onCancel() {
                cancel();
              },
              okText: 'Yes',
              cancelText: 'No',
            });
          } else {
            await perform(folderId);
          }
        }
      },
    );
  };

  render() {
    const {entities = [], onCancel, visible, folderId} = this.props;
    const {disabled, selectedFolder} = this.state;
    return (
      <Modal
        title={`Copy ${entities.length} entit${entities.length > 1 ? 'ies' : 'y'}`}
        onCancel={() => onCancel()}
        width="80%"
        footer={
          <div className={styles.footer}>
            <Button disabled={disabled} onClick={() => onCancel()}>
              Cancel
            </Button>
            <div className={styles.actions}>
              <Button
                disabled={disabled || !selectedFolder || selectedFolder.id === folderId}
                type="primary"
                onClick={() => this.doOperation()}
              >
                Copy
              </Button>
              <Button
                disabled={disabled || !selectedFolder || selectedFolder.id === folderId}
                type="primary"
                onClick={() => this.doOperation(true)}
              >
                Move
              </Button>
            </div>
          </div>
        }
        open={visible}
      >
        {this.renderContent()}
      </Modal>
    );
  }
}

CopyMetadataEntitiesDialog.propTypes = {
  entities: PropTypes.array,
  folderId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  metadataClass: PropTypes.string,
  onCancel: PropTypes.func,
  onCopy: PropTypes.func,
  visible: PropTypes.bool,
};

export default inject(
  'pipelinesLibrary',
  'preferences',
)(HiddenObjects.injectTreeFilter(observer(CopyMetadataEntitiesDialog)));
