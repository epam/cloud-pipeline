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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import PropTypes from 'prop-types';
import {Switch, Alert, Button, Row, Col, Modal, Spin} from 'antd';
import CodeEditor from '../../../special/CodeEditor';
import HotTable from 'react-handsontable';
import Papa from 'papaparse';
import styles from './DataStorageCodeForm.css';
import GenerateDownloadUrlRequest from '../../../../models/dataStorage/GenerateDownloadUrl';
import DataStorageItemContent from '../../../../models/dataStorage/DataStorageItemContent';

@inject(({routing}, params) => ({
  routing
}))
@inject('cancel', 'storageId', 'save')
@observer
export default class DataStorageCodeForm extends React.Component {

  static propTypes = {
    file: PropTypes.object,
    cancel: PropTypes.func,
    save: PropTypes.func
  };

  editor;
  hotEditor;
  _parseOpts = {};

  state = {
    editMode: false,
    editTabularAsText: false
  };

  _modifiedCode = null;
  @observable
  _originalCode = null;
  @observable
  _fileContents = null;
  @observable
  _generateDownloadUrl = null;

  @computed
  get codeTruncated () {
    return this._fileContents && this._fileContents.loaded && this._fileContents.value.truncated;
  }

  @computed
  get downloadUrl () {
    if (this._generateDownloadUrl && this._generateDownloadUrl.loaded) {
      return this._generateDownloadUrl.value.url;
    }
    return undefined;
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.file && nextProps.file !== this.props.file) {
      this._generateDownloadUrl = new GenerateDownloadUrlRequest(
        nextProps.storageId,
        nextProps.file.path,
        nextProps.file.version
      );
      this._generateDownloadUrl.fetch();
      this._fileContents = new DataStorageItemContent(
        nextProps.storageId,
        nextProps.file.path,
        nextProps.file.version
      );
      this._originalCode = '';
      this._modifiedCode = null;
    } else if (!nextProps.file) {
      this._fileContents = null;
      this._originalCode = '';
      this._modifiedCode = null;
    }
  }

  componentDidUpdate () {
    if (this._fileContents && !this._fileContents.pending && !this._originalCode) {
      this._originalCode = this._fileContents.value.content
        ? atob(this._fileContents.value.content)
        : '';
    }
  }

  onCodeChange = (newText) => {
    this._modifiedCode = newText;
  };

  onClose = () => {
    const close = () => {
      this.setState({
        editMode: false,
        editTabularAsText: false
      });
      this._modifiedCode = this._originalCode;
      if (this.editor) {
        this.editor.clear();
      }
      this.props.cancel();
    };
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    if (this._modifiedCode && this._originalCode !== this._modifiedCode) {
      Modal.confirm({
        title: 'All changes will be lost. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          close();
        }
      });
    } else {
      close();
    }
  };

  onSave = () => {
    const close = () => {
      this.setState({
        editMode: false,
        editTabularAsText: false
      });
      this._modifiedCode = this._originalCode;
      if (this.editor) {
        this.editor.clear();
      }
      this.props.cancel();
    };
    const save = async () => {
      await this.props.save(this.props.file.path, this._modifiedCode);
    };
    if (this._modifiedCode && this._originalCode !== this._modifiedCode) {
      Modal.confirm({
        title: `Save changes to ${this.props.file.name}?`,
        style: {
          wordWrap: 'break-word'
        },
        async onOk () {
          await save();
          close();
        }
      });
    } else {
      close();
    }
  };

  toggleEditMode = async() => {
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    if (!this.state.editMode) {
      this._modifiedCode = null;
      this.setState({editMode: !this.state.editMode});
    } else if (this._modifiedCode !== null && this._modifiedCode !== this._originalCode) {
      this.onSave();
    } else {
      this._modifiedCode = null;
      this.setState({editMode: !this.state.editMode});
    }
  };

  toggleEditTabularAsText = (editTabularAsText) => {
    if (this.hotEditor && this.state.editMode) {
      this._modifiedCode = this.stringTableData;
    }
    this.setState({editTabularAsText});
  };

  @computed
  get isEditable () {
    return !!(this._fileContents.loaded && !this.codeTruncated && this.props.file.editable);
  }

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  initializeTableEditor = (tableEditor) => {
    this.hotEditor = tableEditor;
  };

  get fileType () {
    const name = this.props.file ? this.props.file.name : null;
    if (!name) {
      return null;
    }
    const extension = name.split('.').pop().toLowerCase();
    switch (extension) {
      case 'dsv': return 'dsv';
      case 'tsv': return 'tsv';
      case 'csv': return 'csv';
      case 'txt':
      default: return 'text';
    }
  }

  get isTabular () {
    return this.fileType === 'dsv' ||
      this.fileType === 'tsv' ||
      this.fileType === 'csv';
  }

  get structuredTableData () {
    const result = {};
    const csvString = this._modifiedCode !== null
      ? this._modifiedCode
      : (this._originalCode || '');
    const parseRes = Papa.parse(csvString);

    if (parseRes.errors.length) {
      const firstErr = parseRes.errors.shift();
      result.error = true;
      result.message = `${firstErr.code}: ${firstErr.message}. at row ${firstErr.row + 1}`;

      return result;
    }

    if (parseRes.meta && parseRes.meta.delimiter) {
      this._parseOpts.delimiter = parseRes.meta.delimiter;
    }
    if (parseRes.meta && parseRes.meta.linebreak) {
      this._parseOpts.linebreak = parseRes.meta.linebreak;
    }

    result.data = parseRes.data;

    return result;
  }

  get stringTableData () {
    const unparseConfig = {};
    if (this._parseOpts && this._parseOpts.delimiter) {
      unparseConfig.delimiter = this._parseOpts.delimiter;
    }
    if (this._parseOpts && this._parseOpts.linebreak) {
      unparseConfig.newline = this._parseOpts.linebreak;
    }
    return Papa.unparse(this.hotEditor.hotInstance.getData(), unparseConfig);
  }

  get fileEditor () {
    if (this._fileContents && this._fileContents.pending) {
      return null;
    }
    if (this.isTabular && !this.state.editTabularAsText) {
      this._tableData = this.structuredTableData;

      return !this._tableData.error
        ? (
          <HotTable
            root="hot"
            ref={this.initializeTableEditor}
            data={this._tableData.data}
            colHeaders={true}
            rowHeaders={true}
            readOnly={!this.state.editMode}
            readOnlyCellClassName={'readonly-cell'}
            manualColumnResize={true}
            manualRowResize={true}
            contextMenu={this.state.editMode
              ? [
                'row_above',
                'row_below',
                '---------',
                'col_left',
                'col_right',
                '---------',
                'remove_row',
                'remove_col',
                '---------',
                'undo',
                'redo',
                '---------',
                'cut',
                'copy'
              ] : [
                'copy'
              ]}
          />
        )
        : (
          <Alert
            message={`Error parsing tabular file ${this.props.file.name}:
              ${this._tableData.message}`}
            type="error"/>
        );
    } else {
      return (
        <CodeEditor
          ref={this.initializeEditor}
          readOnly={!this.state.editMode}
          code={this._modifiedCode !== null ? this._modifiedCode : (this._originalCode || '')}
          onChange={this.onCodeChange}
          supportsFullScreen={true}
          language="text"
          fileName={this.props.file ? this.props.file.name : undefined}
          delayedUpdate={true}
        />
      );
    }
  }

  render () {
    const tableClassName = this.state.editMode ? styles.tableEditor : styles.tableEditorReadonly;
    const title = this.props.file
      ? (
        <Row type="flex" justify="space-between">
          <Col>{this.props.file.name}</Col>
          {
            this.codeTruncated && this.downloadUrl &&
            <Col>
              File is too large to be shown. <a href={this.downloadUrl} download={this.props.file.name}>Download file</a> to view full contents
            </Col>
          }
          <Col>
            {
              this.isEditable && this.isTabular &&
              <span>{this.state.editMode ? 'Edit' : 'View'} as text: <Switch
                className={styles.button}
                onChange={(checked) => this.toggleEditTabularAsText(checked)}
              /></span>
            }
            {
              this.isEditable && this.state.editMode &&
              <Button type="primary" className={styles.button} onClick={this.toggleEditMode}>
                Save
              </Button>
            }
            {
              this.isEditable && !this.state.editMode &&
              <Button className={styles.button} onClick={this.toggleEditMode}>
                Edit
              </Button>
            }
            <Button className={styles.button} onClick={this.onClose}>
              Close
            </Button>
          </Col>
        </Row>)
      : null;
    return (
      <Modal
        visible={!!this.props.file}
        onCancel={this.onClose}
        width="80%"
        title={title}
        closable={false}
        maskClosable={false}
        footer={false}
        style={{top: 20}}
      >
        <Spin spinning={this._fileContents && this._fileContents.pending}>
          <div className={`${styles.editorContainer} ${tableClassName}`}>
            { this.fileEditor }
          </div>
        </Spin>
      </Modal>
    );
  }
}
