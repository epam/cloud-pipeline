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
import classNames from 'classnames';
import {
  Alert,
  Button,
  Icon,
  Input,
  Modal,
  Table
} from 'antd';
import VSVersions from '../vs-versions-select';
import styles from './vs-browse-dialog.css';

class VSBrowseDialog extends React.Component {
  state = {
    disabled: false,
    error: undefined,
    pipelineId: undefined,
    pipelineInfo: undefined,
    version: undefined,
    versions: [],
    pending: false,
    filter: undefined
  };

  vsTableColumns = [{
    key: 'selection',
    className: styles.checkCell,
    render: () => (<Icon type="check" className={styles.check} />),
    onCellClick: storage => this.wrapPromiseAction(this.onSelectStorage)(storage)
  }, {
    title: 'Storage',
    key: 'name',
    dataIndex: 'name',
    className: styles.cell,
    onCellClick: storage => this.wrapPromiseAction(this.onSelectStorage)(storage)
  }, {
    key: 'versions',
    className: styles.versions,
    render: storage => this.renderVSVersions(storage),
    onCellClick: storage => this.onVersionsCellClicked(storage)
  }];

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevState.pipelineId !== this.state.pipelineId) {
      this.pipelineChanged();
    }
    if (prevProps.visible !== this.props.visible && !this.props.visible) {
      this.clearSelection();
    }
  }

  clearSelection = () => {
    this.setState({
      disabled: false,
      error: undefined,
      pipelineId: undefined,
      pipelineInfo: undefined,
      version: undefined,
      versions: [],
      pending: false,
      filter: undefined
    });
  };

  wrapPromiseAction = (f, property = 'pending') => (...opts) => {
    return new Promise((resolve, reject) => {
      this.setState({
        [property]: true
      }, () => {
        f(...opts)
          .then((o) => {
            this.setState({
              [property]: false,
              ...(o || {})
            });
            resolve(o);
          })
          .catch(e => {
            this.setState({
              [property]: false
            });
            reject(e);
          });
      });
    });
  }

  pipelineChanged = () => {
    this.setState({
      version: undefined
    });
  };

  onVersionsCellClicked = storage => {
    const {
      disabled,
      pipelineId
    } = this.state;
    if (disabled || (storage && +pipelineId === +(storage.id))) {
      return Promise.resolve();
    }
    return this.wrapPromiseAction(this.onSelectStorage)(storage);
  };

  onSelectStorage = storage => {
    const {
      repositories = []
    } = this.props;
    const {
      disabled,
      pipelineId
    } = this.state;
    if ((storage && repositories.find(r => +(r.id) === +(storage.id))) ||
      disabled ||
      (storage && +pipelineId === +(storage.id))
    ) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      resolve({
        pipelineId: storage?.id,
        pipelineInfo: storage,
        error: undefined,
        versions: [],
        version: undefined
      });
    });
  };

  onSelect = () => {
    const {
      onSelect
    } = this.props;
    const {
      pipelineId,
      pipelineInfo = {},
      version,
      versions
    } = this.state;
    if (onSelect) {
      return onSelect({
        ...pipelineInfo,
        id: pipelineId,
        version: versions.find(v => v.commitId === version),
        commitId: version
      });
    }
    return Promise.resolve();
  };

  renderVSVersions = (storage) => {
    const {
      pipelineId,
      version
    } = this.state;
    const {
      repositories
    } = this.props;
    if (pipelineId && +pipelineId === +(storage.id)) {
      const onChangeVersion = v => this.setState({version: v});
      return (
        <VSVersions
          className={styles.versionsSelect}
          rowClassName={styles.versionRow}
          value={version}
          onChange={onChangeVersion}
          repository={pipelineId}
          setFirstVersionByDefault
        />
      );
    } else {
      const repo = (repositories || []).find(r => +(r.id) === +(storage.id));
      if (repo) {
        return repo.revision;
      }
    }
    return null;
  };

  renderFilterControl = () => {
    const {pipelines} = this.props;
    if (!pipelines || pipelines.error) {
      return null;
    }
    const {disabled, filter} = this.state;
    const onChange = (e) => {
      this.setState({filter: e.target.value});
    };
    return (
      <div className={styles.filter}>
        <Input
          disabled={disabled}
          value={filter}
          onChange={onChange}
          placeholder="Filter versioned storages"
        />
      </div>
    );
  };

  renderVSTable = () => {
    const {pipelines, repositories} = this.props;
    if (!pipelines || pipelines.error) {
      return null;
    }
    const {
      pipelineId,
      filter
    } = this.state;
    const data = Array.from(pipelines.value || [])
      .filter(storage => /^VERSIONED_STORAGE$/i.test(storage.pipelineType))
      .filter(storage => !filter || storage.name.toLowerCase().includes(filter.toLowerCase()));
    const storageDisabled = storage => (repositories || []).find(r => +(r.id) === +(storage.id));
    return (
      <Table
        size="small"
        columns={this.vsTableColumns}
        dataSource={data}
        rowKey="id"
        rowClassName={storage => classNames(
          styles.row,
          {
            [styles.selected]: +(storage.id) === +pipelineId
          },
          {
            [styles.cloned]: storageDisabled(storage)
          }
        )}
      />
    );
  };

  render () {
    const {
      visible,
      onClose,
      pipelines
    } = this.props;
    const {
      disabled,
      pipelineId,
      version,
      pending
    } = this.state;
    return (
      <Modal
        width="60%"
        title="Select Version Storage"
        visible={visible}
        onCancel={onClose}
        closable={!disabled}
        maskClosable={!disabled}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              id="cancel-vs-storage-button"
              disabled={disabled}
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              id="select-vs-storage-button"
              type="primary"
              disabled={!pipelineId || !version || pending || disabled}
              onClick={this.wrapPromiseAction(this.onSelect, 'disabled')}
            >
              SELECT
            </Button>
          </div>
        )}
      >
        {
          pipelines && pipelines.error && (
            <Alert type="error" message={pipelines.error} />
          )
        }
        {
          this.renderFilterControl()
        }
        {
          this.renderVSTable()
        }
      </Modal>
    );
  }
}

VSBrowseDialog.propTypes = {
  onClose: PropTypes.func,
  onSelect: PropTypes.func,
  repositories: PropTypes.array,
  visible: PropTypes.bool
};

export default inject('pipelines')(observer(VSBrowseDialog));
