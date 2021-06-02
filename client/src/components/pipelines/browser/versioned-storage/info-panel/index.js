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
import {observer} from 'mobx-react';
import {
  Alert,
  Row,
  Button,
  Icon,
  Spin,
  Input
} from 'antd';
import Papa from 'papaparse';
import VersionFile from '../../../../../models/pipelines/VersionFile';
import localization from '../../../../../utils/localization';
import {SplitPanel} from '../../../../special/splitPanel';
import VSHistory from '../history';
import styles from './info-panel.css';

const MAX_SIZE_TO_PREVIEW = 1024 * 75; // 25kb
const CONTENT_INFO = [{
  key: 'preview',
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    priority: 0,
    percentDefault: 33,
    pxMinimum: 100
  }
}, {
  key: 'history',
  containerStyle: {
    display: 'flex',
    flexDirection: 'column'
  },
  size: {
    priority: 2,
    percentDefault: 66,
    pxMinimum: 200
  }
}];

function fillEmptyCells (count, cb) {
  const result = [];
  for (let i = 0; i < count; i++) {
    result.push(cb(i));
  }
  return result;
}

@localization.localizedComponent
@observer
class InfoPanel extends localization.LocalizedReactComponent {
  state = {
    inProgress: false,
    fileIsFetching: false,
    fileFetchingError: undefined,
    binaryFile: false,
    tabularFile: false,
    fileContent: null,
    editFile: false,
    fileEditable: true,
    fileSizeExceeded: false
  };

  componentDidMount () {
    this.getFileContent();
  };

  componentDidUpdate (prevProps) {
    if (prevProps.file !== this.props.file) {
      this.getFileContent();
    }
  }

  get previewAvailable () {
    const {file} = this.props;
    if (file) {
      const {fileSizeExceeded, fileEditable} = this.state;
      return !fileSizeExceeded && fileEditable;
    }
    return false;
  };

  handleFileEdit = () => {
    const {onFileEdit} = this.props;
    onFileEdit && onFileEdit();
  };

  handleGoBackClick = () => {
    const {onGoBack} = this.props;
    onGoBack && onGoBack();
  };

  getFileContent = () => {
    const {
      pipelineId,
      lastCommitId,
      file
    } = this.props;
    if (
      !pipelineId ||
      !file ||
      !file.path ||
      file.size > MAX_SIZE_TO_PREVIEW ||
      !lastCommitId
    ) {
      this.setState({
        fileIsFetching: false,
        fileFetchingError: undefined,
        fileContent: undefined,
        binaryFile: false,
        tabularFile: false
      });
      return null;
    }
    const request = new VersionFile(
      pipelineId,
      file.path,
      lastCommitId
    );
    this.setState({
      fileIsFetching: true
    }, () => {
      const reject = error => {
        this.setState({
          fileIsFetching: false,
          fileFetchingError: error.message,
          fileContent: undefined,
          binaryFile: false,
          tabularFile: false
        });
      };
      const resolve = result => {
        try {
          const content = atob(result);
          // eslint-disable-next-line
          const isBinary = o => /[\x00-\x08\x0B-\x0C\x0E-\x1F]/.test(o);
          const binary = isBinary(content);
          const parseAsTabular = Papa.parse(content);
          const isTabular = !binary &&
            /\.(csv|tsv)$/i.test(file.path) &&
            parseAsTabular.errors.length === 0 &&
            !parseAsTabular.data.find(item => item.find(isBinary));
          this.setState({
            fileContent: binary ? undefined : content,
            binaryFile: !isTabular && binary,
            tabularFile: isTabular
              ? parseAsTabular.data
              : false,
            fileIsFetching: false,
            fileFetchingError: undefined
          });
        } catch (e) {
          reject(new Error(`Error parsing file: ${e.message}`));
        }
      };
      request
        .fetch()
        .then(() => {
          if (request.error) {
            reject(new Error(request.error || `Error fetching ${file.path} content`));
          } else {
            resolve(request.response);
          }
        })
        .catch(reject);
    });
  };

  renderDownloadLink = (description) => {
    const {file, onFileDownload} = this.props;
    if (!file) {
      return null;
    }
    return (
      <span>
        <span
          className={styles.downloadBtn}
          onClick={() => onFileDownload(file)}
        >
          Download file
        </span>
        {description}
      </span>
    );
  };

  renderPreviewHeader = () => {
    const {file} = this.props;
    if (!file) {
      return null;
    }
    const {
      fileFetchingError,
      fileIsFetching,
      binaryFile
    } = this.state;
    const {fileEditable} = this.state;
    let content;
    if (fileIsFetching) {
      content = (
        <Row type="flex" justify="center">
          <Spin />
        </Row>
      );
    } else if (fileFetchingError) {
      content = (
        <Row
          type="flex"
          style={{color: '#777', marginTop: 5, marginBottom: 5}}
        >
          <Alert
            type="error"
            message={(
              <Row>
                <span style={{marginRight: 5}}>
                  {fileFetchingError}
                </span>
                {this.renderDownloadLink()}
              </Row>
            )}
          />
        </Row>
      );
    } else if (file.size > MAX_SIZE_TO_PREVIEW || binaryFile) {
      content = (
        <Row
          type="flex"
          style={{color: '#777', marginTop: 5, marginBottom: 5}}
        >
          <span style={{marginRight: 5}}>
            File preview is not available.
          </span>
          {this.renderDownloadLink('to view full contents')}
        </Row>
      );
    } else {
      content = (
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.previewHeaderRow}
        >
          <b>File preview</b>
          <Button
            size="small"
            onClick={this.handleFileEdit}
            disabled={!fileEditable}
            className={styles.previewHeaderBtn}
          >
            <Icon type="arrows-alt" />
          </Button>
        </Row>
      );
    }
    return (
      <div className={styles.previewHeaderContainer}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.previewHeaderRow}
        >
          <div style={{flex: 1}}>
            <Button
              size="small"
              className={styles.goBackHeaderBtn}
              onClick={this.handleGoBackClick}
            >
              <Icon type="left" />
            </Button>
            <b>{file.name}</b>
          </div>
          {/* button removed until blind/unblind api will be ready */}
          {/* <Button
            size="small"
            className={styles.previewHeaderBtn}
            disabled
          >
            <Icon type="eye" />
          </Button> */}
        </Row>
        {content}
      </div>
    );
  };

  renderFilePreview = () => {
    const {file} = this.props;
    const {
      binaryFile,
      tabularFile,
      fileContent,
      fileFetchingError,
      fileIsFetching
    } = this.state;
    if (
      !file ||
      binaryFile ||
      fileFetchingError ||
      file.size > MAX_SIZE_TO_PREVIEW
    ) {
      return null;
    }
    if (tabularFile && tabularFile.length > 0) {
      const columnsLength = tabularFile
        .reduce((length, row) => Math.max(length, row.length), 0);
      return (
        <div className={styles.tabularFileContainer}>
          <table className={styles.tabular}>
            <thead>
              <tr>
                <th>
                  {'\u00A0'}
                </th>
                {tabularFile[0].map((cell, index) => (
                  <th key={`column-${index}`}>
                    {cell}
                  </th>
                ))}
                {
                  fillEmptyCells(
                    columnsLength - tabularFile[0].length,
                    index => (
                      <th key={`column-${index + tabularFile[0].length}`}>
                        {'\u00A0'}
                      </th>
                    )
                  )
                }
              </tr>
            </thead>
            <tbody>
              {
                tabularFile.slice(1).map((columns, row) => (
                  <tr key={`row-${row}`}>
                    <th key={`row-${row}`}>
                      {row + 1}
                    </th>
                    {columns.map((cell, column) => (
                      <td key={`cell-${row}-${column}`}>
                        {cell}
                      </td>
                    ))}
                    {
                      fillEmptyCells(
                        columnsLength - columns.length,
                        index => (
                          <td key={`cell-${row}-${index + columns.length}`}>
                            {'\u00A0'}
                          </td>
                        )
                      )
                    }
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      );
    } else {
      return (
        <Input.TextArea
          disabled={fileIsFetching}
          spellCheck="false"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          autosize={false}
          className={styles.filePreviewInput}
          value={fileContent || (fileIsFetching && '') || 'empty'}
          style={
            fileContent
              ? {height: '100%'}
              : {color: '#aaa', height: '100%', fontStyle: 'italic'}
          }
          readOnly
        />
      );
    }
  };

  render () {
    const {
      lastCommitId,
      pipelineId,
      path,
      file
    } = this.props;
    return (
      <SplitPanel
        style={{overflow: 'auto'}}
        contentInfo={CONTENT_INFO}
        orientation="vertical"
      >
        {
          file && (
            <div
              key="preview"
              className={styles.previewContainer}
            >
              {this.renderPreviewHeader()}
              {this.renderFilePreview()}
            </div>
          )
        }
        <VSHistory
          key="history"
          path={file ? file.path : path}
          versionedStorageId={pipelineId}
          revision={lastCommitId}
          isFolder={!file}
          style={{
            flex: 1
          }}
        />
      </SplitPanel>
    );
  }
}

InfoPanel.propTypes = {
  file: PropTypes.object,
  path: PropTypes.string,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  lastCommitId: PropTypes.string,
  pending: PropTypes.bool,
  onFileEdit: PropTypes.func,
  onFileDownload: PropTypes.func,
  onGoBack: PropTypes.func
};

export default InfoPanel;
