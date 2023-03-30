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
import classNames from 'classnames';
import {observer} from 'mobx-react';
import {Icon, Alert} from 'antd';
import FileSaver from 'file-saver';
import AnalysisOutputTable, {fetchContents} from './analysis-output-table';
import {generateResourceUrl} from '../model/analysis/output-utilities';
import displayDate from '../../../../utils/displayDate';
import auditStorageAccessManager from '../../../../utils/audit-storage-access';
import styles from './cell-profiler.css';

function getFileNameExtensionFromUrl (url) {
  try {
    const urlObject = new URL(url);
    return (urlObject.pathname || '').split(/\//).pop().split('.').pop();
  } catch (_) {
    return undefined;
  }
}

@observer
class AnalysisOutputWithDownload extends React.Component {
  state = {
    url: undefined,
    downloadUrl: undefined,
    downloadPath: undefined,
    analysisUrl: undefined,
    data: undefined,
    output: undefined,
    pending: false,
    error: undefined,
    analysisPending: false
  };

  componentDidMount () {
    this.updateUrl();
    this.updateAnalysisUrl();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storageId !== this.props.storageId ||
      prevProps.path !== this.props.path ||
      prevProps.downloadPath !== this.props.downloadPath
    ) {
      this.updateUrl();
    }
    if (
      prevProps.analysisStorageId !== this.props.analysisStorageId ||
      prevProps.analysisPath !== this.props.analysisPath
    ) {
      this.updateAnalysisUrl();
    }
  }

  updateUrl = () => {
    const {
      storageId,
      path,
      downloadPath
    } = this.props;
    this.setState({
      pending: true,
      error: undefined,
      url: undefined,
      downloadUrl: undefined,
      downloadPath: undefined,
      data: undefined
    }, async () => {
      const state = {
        pending: false,
        error: undefined,
        data: undefined
      };
      try {
        const _url = await generateResourceUrl({storageId, path});
        if (!_url) {
          throw new Error('Cannot generate download url');
        }
        auditStorageAccessManager.reportReadAccess({
          storageId,
          path,
          reportStorageType: 'S3'
        });
        state.data = await fetchContents(_url);
        if (state.data) {
          state.url = _url;
        }
        const _downloadUrl = await generateResourceUrl({
          storageId,
          path: downloadPath,
          checkExists: true
        });
        if (_downloadUrl) {
          state.downloadUrl = _downloadUrl;
          state.downloadPath = downloadPath;
        } else {
          state.downloadUrl = _url;
          state.downloadPath = path;
        }
      } catch (error) {
        state.error = error.message;
      } finally {
        this.setState(state);
      }
    });
  };

  updateAnalysisUrl = () => {
    const {
      analysisPath,
      analysisStorageId
    } = this.props;
    this.setState({
      analysisPending: true,
      analysisUrl: undefined
    }, async () => {
      const state = {
        analysisPending: false,
        analysisUrl: undefined
      };
      try {
        const _url = await generateResourceUrl({
          storageId: analysisStorageId,
          path: analysisPath,
          checkExists: true
        });
        if (_url) {
          state.analysisUrl = _url;
        }
      } catch (_) {
      } finally {
        this.setState(state);
      }
    });
  };

  handleDownload = async (e) => {
    e.preventDefault();
    const {
      downloadUrl,
      downloadPath
    } = this.state;
    if (!downloadUrl) {
      return null;
    }
    const {
      input = 'analysis',
      analysisDate,
      analysisName,
      storageId
    } = this.props;
    const hcsFileName = input
      .split(/[\\/]/)
      .pop()
      .split('.')
      .slice(0, -1)
      .join('.')
      .replace(/\s/g, '_');
    const dateTime = analysisDate
      ? displayDate(analysisDate, 'YYYYMMDD_HHmmss')
      : undefined;
    let fileName = [
      hcsFileName,
      analysisName ? analysisName.replace(/\s/g, '_') : undefined,
      dateTime
    ].filter(Boolean).join('-');
    const extension = getFileNameExtensionFromUrl(downloadUrl) || 'xlsx';
    fileName = fileName.concat('.').concat(extension);
    auditStorageAccessManager.reportReadAccess({
      storageId,
      path: downloadPath,
      reportStorageType: 'S3'
    });
    fetch(downloadUrl)
      .then(res => res.blob())
      .then(blob => FileSaver.saveAs(blob, fileName));
  };

  handleDownloadAnalysisFile = async (e) => {
    e.preventDefault();
    const {
      analysisUrl
    } = this.state;
    if (!analysisUrl) {
      return null;
    }
    const {
      input = 'analysis',
      analysisName,
      analysisStorageId,
      analysisPath
    } = this.props;
    const hcsFileName = input
      .split(/[\\/]/)
      .pop()
      .split('.')
      .slice(0, -1)
      .join('.')
      .replace(/\s/g, '_');
    let fileName = [
      hcsFileName,
      analysisName ? analysisName.replace(/\s/g, '_') : undefined
    ].filter(Boolean).join('_');
    const extension = getFileNameExtensionFromUrl(analysisUrl) || 'xlsx';
    fileName = fileName.concat('.').concat(extension);
    auditStorageAccessManager.reportReadAccess({
      storageId: analysisStorageId,
      path: analysisPath,
      reportStorageType: 'S3'
    });
    fetch(analysisUrl)
      .then(res => res.blob())
      .then(blob => FileSaver.saveAs(blob, fileName));
  };

  renderHeader () {
    const {
      url,
      pending,
      analysisUrl,
      analysisPending
    } = this.state;
    const {
      onClose
    } = this.props;
    if (!url && !analysisUrl) {
      return null;
    }
    return (
      <div
        className={styles.analysisOutputHeader}
      >
        {
          url && (<b>Analysis results</b>)
        }
        {
          (pending || analysisPending) && (<Icon type="loading" style={{marginLeft: 5}} />)
        }
        {
          url && (
            <a
              style={{marginLeft: 5}}
              onClick={this.handleDownload}
            >
              Download results
            </a>
          )
        }
        {
          analysisUrl && (
            <a
              style={{marginLeft: 5}}
              onClick={this.handleDownloadAnalysisFile}
            >
              Download analysis file
            </a>
          )
        }
        {
          typeof onClose === 'function' && (
            <Icon
              type="close"
              style={{
                marginLeft: 'auto',
                cursor: 'pointer'
              }}
              onClick={onClose}
            />
          )
        }
      </div>
    );
  }

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      url,
      pending,
      data
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.analysisOutputContainer,
            className
          )
        }
        style={style}
      >
        {
          this.renderHeader()
        }
        {
          pending && !url && (
            <Icon type="loading" />
          )
        }
        {
          !pending && (!url || !data) ? (
            <Alert
              message="Analysis results not found."
              type="info"
            />
          ) : null
        }
        <div
          className={styles.analysisOutputTableContainer}
        >
          <AnalysisOutputTable
            data={data}
          />
        </div>
      </div>
    );
  }
}

AnalysisOutputWithDownload.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  path: PropTypes.string,
  downloadPath: PropTypes.string,
  analysisStorageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  analysisPath: PropTypes.string,
  onClose: PropTypes.func,
  input: PropTypes.string,
  analysisDate: PropTypes.string,
  analysisName: PropTypes.string
};

function AnalysisOutput (
  {
    analysis,
    className,
    style,
    onClose
  }
) {
  if (
    !analysis ||
    !analysis.defineResultsOutputs ||
    analysis.defineResultsOutputs.length === 0
  ) {
    return null;
  }
  const outputs = analysis.defineResultsOutputs;
  const output = outputs.find(o => o.table);
  const xlsx = outputs.find(o => o.xlsx);
  return (
    <AnalysisOutputWithDownload
      className={className}
      style={style}
      storageId={output ? output.storageId : undefined}
      path={output ? output.storagePath : undefined}
      downloadPath={xlsx ? xlsx.storagePath : undefined}
      onClose={onClose}
      input={analysis.path}
      analysisDate={analysis.analysisDate}
      analysisName={analysis.pipeline ? analysis.pipeline.name : undefined}
    />
  );
}

AnalysisOutput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  analysis: PropTypes.object,
  onClose: PropTypes.func
};

export {AnalysisOutputWithDownload};
export default observer(AnalysisOutput);
