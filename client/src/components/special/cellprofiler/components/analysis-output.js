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
import {Analysis} from '../model/analysis';
import AnalysisOutputTable, {fetchContents} from './analysis-output-table';
import {generateResourceUrl} from '../model/analysis/output-utilities';
import styles from './cell-profiler.css';

/**
 * @param {AnalysisOutputResult} output
 * @returns {Promise<string>}
 */
function getDownloadUrl (output) {
  if (!output) {
    return Promise.resolve(undefined);
  }
  if (typeof output.fetchUrl === 'function') {
    return output.fetchUrl();
  }
  return Promise.resolve(output.url);
}

@observer
class AnalysisOutputWithDownload extends React.Component {
  state = {
    url: undefined,
    downloadUrl: undefined,
    data: undefined,
    output: undefined,
    pending: false,
    error: undefined
  };

  componentDidMount () {
    this.updateUrl();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.url !== this.props.url ||
      prevProps.downloadUrl !== this.props.downloadUrl ||
      prevProps.storageId !== this.props.storageId ||
      prevProps.path !== this.props.path ||
      prevProps.downloadPath !== this.props.downloadPath
    ) {
      this.updateUrl();
    }
  }

  updateUrl = () => {
    const {
      url,
      downloadUrl,
      storageId,
      path,
      downloadPath
    } = this.props;
    this.setState({
      pending: true,
      error: undefined,
      url: undefined,
      downloadUrl: undefined,
      data: undefined
    }, async () => {
      const state = {
        pending: false,
        error: undefined,
        data: undefined
      };
      try {
        const _url = await generateResourceUrl({url, storageId, path});
        if (!_url) {
          throw new Error('Cannot generate download url');
        }
        state.data = await fetchContents(_url);
        if (state.data) {
          state.url = _url;
        }
        const _downloadUrl = await generateResourceUrl({
          url: downloadUrl,
          storageId,
          path: downloadPath,
          checkExists: true
        });
        if (_downloadUrl) {
          state.downloadUrl = _downloadUrl;
        } else {
          state.downloadUrl = _url;
        }
      } catch (error) {
        state.error = error.message;
      } finally {
        this.setState(state);
      }
    });
  };

  handleDownload = async (e) => {
    e.preventDefault();
    const {
      downloadUrl
    } = this.state;
    if (!downloadUrl) {
      return null;
    }
    const {filePath, dateTime, analysisName} = this.props;
    const hcsFileName = filePath.split('/').pop().split('.')[0].replaceAll(' ', '_');
    const analysisInfo = analysisName ? `${analysisName}-` : '';
    const fileName = `${hcsFileName}-${analysisInfo}${dateTime}.xlsx`;
    fetch(downloadUrl)
      .then(res => res.blob())
      .then(blob => FileSaver.saveAs(blob, fileName));
  };

  renderHeader () {
    const {
      url,
      pending
    } = this.state;
    const {
      onClose
    } = this.props;
    if (!url) {
      return null;
    }
    return (
      <div
        className={styles.analysisOutputHeader}
      >
        <b>Analysis results</b>
        {
          pending && (<Icon type="loading" style={{marginLeft: 5}} />)
        }
        <a
          style={{marginLeft: 5}}
          onClick={this.handleDownload}
        >
          Download
        </a>
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
  url: PropTypes.string,
  downloadUrl: PropTypes.string,
  onClose: PropTypes.func,
  filePath: PropTypes.string,
  dateTime: PropTypes.string,
  analysisName: PropTypes.string
};

@observer
class AnalysisOutput extends React.Component {
  state = {
    url: undefined,
    downloadUrl: undefined,
    output: undefined,
    pending: false,
    error: undefined
  };

  componentDidMount () {
    if (this.props.analysis) {
      this.props.analysis.addEventListener(Analysis.Events.analysisDone, this.updateUrls);
    }
    this.updateUrls();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.analysis !== prevProps.analysis) {
      if (prevProps.analysis) {
        prevProps.analysis.removeEventListeners(
          Analysis.Events.analysisDone,
          this.updateUrls
        );
      }
      this.props.analysis.addEventListener(Analysis.Events.analysisDone, this.updateUrls);
    }
  }

  componentWillUnmount () {
    if (this.props.analysis) {
      this.props.analysis.removeEventListeners(Analysis.Events.analysisDone, this.updateUrls);
    }
  }

  updateUrls = () => {
    const outputs = this.props.analysis
      ? this.props.analysis.defineResultsOutputs
      : [];
    const output = outputs.find(o => o.table);
    const xlsx = outputs.find(o => o.xlsx);
    if (output) {
      this.setState({
        pending: true,
        error: undefined,
        url: undefined,
        downloadUrl: undefined
      }, () => {
        Promise.all([
          getDownloadUrl(output),
          getDownloadUrl(xlsx)
        ])
          .then(([url, downloadUrl]) => this.setState({
            pending: false,
            error: undefined,
            url,
            downloadUrl
          }))
          .catch(e => this.setState({
            pending: false,
            error: e.message,
            url: undefined,
            downloadUrl: undefined
          }));
      });
    } else {
      this.setState({
        pending: true,
        error: undefined,
        url: undefined,
        downloadUrl: undefined
      });
    }
  };

  render () {
    const {
      analysis,
      className,
      style,
      onClose
    } = this.props;
    if (
      !analysis ||
      !analysis.defineResultsOutputs ||
      analysis.defineResultsOutputs.length === 0
    ) {
      return null;
    }
    const {
      url,
      downloadUrl
    } = this.state;
    return (
      <AnalysisOutputWithDownload
        className={className}
        style={style}
        url={url}
        downloadUrl={downloadUrl}
        onClose={onClose}
      />
    );
  }
}

AnalysisOutput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  analysis: PropTypes.object,
  onClose: PropTypes.func
};

export {AnalysisOutputWithDownload};
export default AnalysisOutput;
