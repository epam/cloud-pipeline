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
import {
  Alert,
  Button,
  Checkbox,
  Input,
  Modal,
  Pagination,
  Table
} from 'antd';
import MetadataEntityKeys from '../../../../models/folderMetadata/MetadataEntityKeys';
import MetadataEntityFilter from '../../../../models/folderMetadata/MetadataEntityFilter';
import styles from './SelectMetadataItems.css';

const PAGE_SIZE = 15;

function fetchMetadataKeys (folder, entityClass) {
  if (!folder || !entityClass) {
    return Promise.resolve([]);
  }
  return new Promise((resolve, reject) => {
    const keysRequest = new MetadataEntityKeys(folder, entityClass);
    keysRequest
      .fetch()
      .then(() => {
        if (keysRequest.error || !keysRequest.loaded) {
          reject(new Error(keysRequest.error || `Error fetching ${entityClass} fields`));
        } else {
          const keys = keysRequest.value || [];
          resolve(
            keys
              .map(({name, predefined}) => ({name, predefined}))
              .sort((a, b) => b.predefined - a.predefined)
              .filter(({name, predefined}) => !predefined || /^externalId$/i.test(name))
              .map(({name}) => name)
          );
        }
      })
      .catch(reject);
  });
}

function fetchMetadataItems (folder, entityClass, search, page, pageSize) {
  if (!folder || !entityClass) {
    return Promise.resolve({elements: [], totalCount: 0});
  }
  return new Promise((resolve, reject) => {
    const filter = new MetadataEntityFilter();
    const payload = {
      filters: [],
      folderId: folder,
      metadataClass: entityClass,
      orderBy: [{field: 'externalId', desc: false}],
      page,
      pageSize,
      searchQueries: [search].filter(Boolean)
    };
    filter
      .send(payload)
      .then(() => {
        if (filter.error || !filter.loaded) {
          reject(new Error(filter.error || `Error fetching ${entityClass} entities`));
        } else {
          const {elements = [], totalCount = 0} = filter.value || {};
          resolve({elements, totalCount});
        }
      })
      .catch(reject);
  });
}

function renderCell (cell) {
  if (typeof cell === 'object') {
    const {type, value} = cell;
    if (/^array\(.*\)$/i.test(type)) {
      try {
        const array = JSON.parse(value);
        if (array && array.length) {
          return (
            <span>
              {array.join(', ')}
            </span>
          );
        }
      } catch (_) {
        return value;
      }
    }
    return value;
  }
  return cell;
}

class SelectMetadataItems extends React.Component {
  state = {
    total: 0,
    items: [],
    type: undefined,
    pending: false,
    error: undefined,
    fields: [],
    fieldsPending: false,
    fieldsError: undefined,
    selection: [],
    search: undefined,
    searchCriteria: undefined
  };

  componentDidMount () {
    this.updateState();
    this.updateMetadataFields();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateState();
      this.updateMetadataFields();
    }
  }

  updateState = () => {
    const {type, selection} = this.props;
    this.setState({
      total: 0,
      type,
      selection: (selection || []),
      items: [],
      page: 1,
      search: undefined,
      searchCriteria: undefined
    }, () => {
      this.fetchItems(this.state.page);
    });
  };

  updateMetadataFields = () => {
    const {folderId, type} = this.props;
    this.setState({
      fieldsPending: true
    }, () => {
      fetchMetadataKeys(folderId, type)
        .then(keys => {
          this.setState({fields: keys, fieldsPending: false, fieldsError: undefined});
        })
        .catch(e => this.setState({
          fields: [],
          fieldsPending: false,
          fieldsError: e.message
        }));
    });
  };

  searchChanged = (e) => {
    this.setState({search: e.target.value}, () => {
      if (!this.state.search) {
        this.handleSearch();
      }
    });
  };

  handleSearch = () => {
    const {search, searchCriteria} = this.state;
    if ((search || '') !== (searchCriteria || '')) {
      this.setState({searchCriteria: search}, () => {
        this.fetchItems(1);
      });
    }
  };

  renderItems = () => {
    const {multiple} = this.props;
    const {
      items,
      pending,
      error,
      fields,
      fieldsPending,
      fieldsError,
      page,
      total
    } = this.state;
    let {selection} = this.state;
    if (error || fieldsError) {
      return null;
    }
    const cellIsSelected = id => selection.map(o => `${o}`).indexOf(`${id}`) >= 0;
    const toggleSelection = id => {
      const index = selection.map(o => `${o}`).indexOf(`${id}`);
      if (index >= 0) {
        selection.splice(index, 1);
      } else {
        if (multiple) {
          selection.push(id);
        } else {
          selection = [id];
        }
      }
      this.setState({selection});
    };
    const clearSelection = () => {
      this.setState({selection: []});
    };
    const selectAll = () => {
      const select = (id) => {
        const index = selection.map(o => `${o}`).indexOf(`${id}`);
        if (index === -1) {
          selection.push(id);
        }
      };
      const ids = items.map(item => item.externalId);
      for (let i = 0; i < ids.length; i++) {
        select(ids[i]);
      }
      this.setState({selection});
    };
    const disabled = fieldsPending || pending;
    const columns = [
      {
        key: 'selection',
        className: styles.selectionColumn,
        fixed: 'left',
        render: ({externalId}) => (
          <Checkbox
            checked={cellIsSelected(externalId)}
            onChange={() => toggleSelection(externalId)}
            disabled={disabled}
          />
        )
      },
      ...fields.map(field => ({
        dataIndex: /^externalId$/i.test(field)
          ? field
          : `data.${field}`,
        key: field,
        fixed: /^externalId$/i.test(field)
          ? 'left'
          : undefined,
        className: `${styles.column} ${styles[field]}`,
        title: /^externalId$/i.test(field) ? 'ID' : field,
        render: renderCell
      }))
    ];
    return (
      <Table
        columns={columns}
        dataSource={items}
        pagination={false}
        rowKey="externalId"
        size="small"
        scroll={{x: 100}}
        footer={() => (
          <div>
            {
              multiple && (
                <div
                  style={{marginTop: 5}}
                >
                  <Button
                    disabled={selection.length === 0}
                    size="small"
                    style={{marginRight: 5}}
                    onClick={clearSelection}
                  >
                    Clear selection
                  </Button>
                  <Button
                    size="small"
                    onClick={selectAll}
                    disabled={selection.length === total}
                  >
                    Select all
                  </Button>
                </div>
              )
            }
            <div style={{marginTop: 5}}>
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={total}
                onChange={this.fetchItems}
                disabled={disabled}
                size="small"
              />
            </div>
          </div>
        )}
      />
    );
  };

  fetchItems = (page) => {
    const {
      folderId,
      type
    } = this.props;
    const {searchCriteria} = this.state;
    this.setState({
      pending: true,
      page
    }, () => {
      fetchMetadataItems(
        folderId,
        type,
        searchCriteria,
        page,
        PAGE_SIZE
      )
        .then(({elements: items, totalCount: total}) => {
          this.setState({
            total,
            items,
            pending: false,
            error: undefined
          });
        })
        .catch(e => {
          this.setState({
            pending: false,
            error: e.message
          });
        });
    });
  }

  onOkClicked = () => {
    const {onSave} = this.props;
    const {selection} = this.state;
    onSave && onSave(selection);
  }

  render () {
    const {
      onClose,
      visible
    } = this.props;
    const {
      error,
      pending,
      type,
      fieldsError,
      fieldsPending,
      search,
      selection = []
    } = this.state;
    const disabled = fieldsPending || pending;
    return (
      <Modal
        visible={visible}
        title={`Select ${type} entities`}
        onCancel={onClose}
        width="80%"
        style={{top: 20}}
        footer={(
          <div className={styles.footer}>
            <Button
              onClick={onClose}
            >
              Cancel
            </Button>
            <Button
              type="primary"
              disabled={disabled || selection.length === 0}
              onClick={this.onOkClicked}
            >
              OK {selection.length > 0 ? `(${selection.length} entities)` : ''}
            </Button>
          </div>
        )}
      >
        {
          (error || fieldsError) && (
            <Alert type="error" message={error || fieldsError} />
          )
        }
        {
          !error && !fieldsError && (
            <div style={{margin: '5px 0'}}>
              <Input.Search
                value={search}
                placeholder="Filter entities"
                onChange={this.searchChanged}
                onSearch={this.handleSearch}
                onBlur={this.handleSearch}
              />
            </div>
          )
        }
        {this.renderItems()}
      </Modal>
    );
  }
}

SelectMetadataItems.propTypes = {
  folderId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  multiple: PropTypes.bool,
  onClose: PropTypes.func,
  onSave: PropTypes.func,
  type: PropTypes.string,
  visible: PropTypes.bool,
  selection: PropTypes.array
};

export default SelectMetadataItems;
