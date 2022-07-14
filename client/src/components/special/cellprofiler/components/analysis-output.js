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
import {Alert, Icon} from 'antd';
import readBlobContents from '../../../../utils/read-blob-contents';
import {Analysis} from '../model/analysis';
import styles from './cell-profiler.css';

function splitHeaderColumns (string) {
  if (!string || !string.length) {
    return [];
  }
  if (!['"', '\''].includes(string[0])) {
    const [column, ...rest] = string.split(',');
    return [column, ...splitHeaderColumns(rest.join(','))];
  }
  const quota = string[0];
  const r = new RegExp(`^${quota}([^${quota}]*)${quota}(,\\s*|$)(.*)$`);
  const e = r.exec(string);
  if (e) {
    return [e[1], ...splitHeaderColumns(e[3])];
  }
  return [];
}

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

/**
 * @param {AnalysisOutputResult} output
 * @returns {Promise<{columns: ([]|*), rows}>}
 */
async function fetchContents (output) {
  const url = await getDownloadUrl(output);
  if (!url) {
    return undefined;
  }
  const response = await fetch(url);
  const blob = await response.blob();
  if (!blob) {
    throw new Error('Error fetching results file');
  }
  const content = await readBlobContents(blob);
  if (!content) {
    throw new Error('Error fetching results file: empty');
  }
  const [header, ...data] = content.split(/\r?\n/);
  return {
    columns: splitHeaderColumns(header),
    rows: data.map(row => row.split(','))
  };
}

@observer
class AnalysisOutput extends React.Component {
  state = {
    contents: undefined,
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
        error: undefined
      }, () => {
        fetchContents(output)
          .then(contents => this.setState({
            pending: false,
            error: undefined,
            contents
          }))
          .catch(e => this.setState({
            pending: false,
            error: e.message,
            contents: undefined
          }));
      });
    } else {
      this.setState({
        pending: true,
        error: undefined,
        contents: undefined
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

  renderContents = () => {
    const {
      error,
      contents
    } = this.state;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    if (contents) {
      const {
        rows = [],
        columns = []
      } = contents;
      return (
        <div
          className={styles.analysisOutputTableContainer}
        >
          <table
            className={
              classNames(
                styles.analysisOutputTable,
                'cell-profiler-results-table'
              )
            }
          >
            <thead>
              <tr>
                {
                  columns.map((column, index) => (
                    <th key={`${column}-${index}`}>
                      {column}
                    </th>
                  ))
                }
              </tr>
            </thead>
            <tbody>
              {
                rows.map((data, rowIndex) => (
                  <tr key={`line-${rowIndex}`}>
                    {
                      data.map((cell, columnIndex) => (
                        <td key={`data-${rowIndex}-${columnIndex}`}>
                          {cell}
                        </td>
                      ))
                    }
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      );
    }
    return null;
  };

  render () {
    const {
      analysis,
      className
    } = this.props;
    if (!analysis || !analysis.analysisOutput) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.analysisOutputContainer,
            className
          )
        }
      >
        {
          this.renderHeader()
        }
        {
          this.renderContents()
        }
      </div>
    );
  }
}

AnalysisOutput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  analysis: PropTypes.object,
  onClose: PropTypes.func
};

export default AnalysisOutput;
