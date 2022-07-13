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
import {Icon} from 'antd';
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
      prevProps.storageId !== this.props.storageId ||
      prevProps.path !== this.props.path
    ) {
      this.updateUrl();
    }
  }

  updateUrl = () => {
    const {
      url,
      storageId,
      path
    } = this.props;
    this.setState({
      pending: true,
      error: undefined,
      url: undefined,
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
      url
    } = this.state;
    if (!url) {
      return null;
    }
    window.open(url, '_blank');
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
  url: PropTypes.string,
  onClose: PropTypes.func
};

@observer
class AnalysisOutput extends React.Component {
  state = {
    url: undefined,
    output: undefined,
    pending: false,
    error: undefined
  };

  componentDidMount () {
    if (this.props.analysis) {
      this.props.analysis.addEventListener(Analysis.Events.analysisDone, this.updateCSVFileUrl);
    }
    this.updateCSVFileUrl();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.analysis !== prevProps.analysis) {
      if (prevProps.analysis) {
        prevProps.analysis.removeEventListeners(
          Analysis.Events.analysisDone,
          this.updateCSVFileUrl
        );
      }
      this.props.analysis.addEventListener(Analysis.Events.analysisDone, this.updateCSVFileUrl);
    }
  }

  componentWillUnmount () {
    if (this.props.analysis) {
      this.props.analysis.removeEventListeners(Analysis.Events.analysisDone, this.updateCSVFileUrl);
    }
  }

  updateCSVFileUrl = () => {
    const output = this.props.analysis ? this.props.analysis.analysisOutput : undefined;
    if (output) {
      this.setState({
        pending: true,
        error: undefined,
        url: undefined
      }, () => {
        getDownloadUrl(output)
          .then(url => this.setState({
            pending: false,
            error: undefined,
            url
          }))
          .catch(e => this.setState({
            pending: false,
            error: e.message,
            url: undefined
          }));
      });
    } else {
      this.setState({
        pending: true,
        error: undefined,
        url: undefined
      });
    }
  };

  handleDownload = async (e) => {
    e.preventDefault();
    const {
      analysis
    } = this.props;
    if (!analysis || !analysis.analysisOutput) {
      return null;
    }
    const downloadUrl = await getDownloadUrl(analysis.analysisOutput);
    if (downloadUrl) {
      window.open(downloadUrl, '_blank');
    }
  };

  renderHeader () {
    const {
      analysis,
      onClose
    } = this.props;
    if (!analysis || !analysis.analysisOutput) {
      return null;
    }
    const {
      pending
    } = this.state;
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
        <Icon
          type="close"
          style={{
            marginLeft: 'auto',
            cursor: 'pointer'
          }}
          onClick={onClose}
        />
      </div>
    );
  }

  render () {
    const {
      analysis,
      className,
      style,
      onClose
    } = this.props;
    if (!analysis || !analysis.analysisOutput) {
      return null;
    }
    const {url} = this.state;
    return (
      <AnalysisOutputWithDownload
        className={className}
        style={style}
        url={url}
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
