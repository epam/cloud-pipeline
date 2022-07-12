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
import {Alert} from 'antd';
import styles from './cell-profiler.css';
import LoadingView from '../../LoadingView';

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
 * @param {string} contents
 * @returns {Promise<{columns: ([]|*), rows}>}
 */
export async function parseContents (contents) {
  if (!contents || /<\?xml/i.test(contents)) {
    return undefined;
  }
  const [header, ...data] = contents.split(/\r?\n/).filter(line => line.length);
  return {
    columns: splitHeaderColumns(header),
    rows: data
      .map(row => row.split(','))
  };
}

/**
 * @param {string} url
 * @returns {Promise<{columns: ([]|*), rows}>}
 */
export async function fetchContents (url) {
  if (!url) {
    return undefined;
  }
  const response = await fetch(url);
  const content = await response.text();
  return parseContents(content);
}

@observer
class AnalysisOutputTable extends React.Component {
  state = {
    contents: undefined,
    pending: false,
    error: undefined
  };

  componentDidMount () {
    this.updateContents();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.url !== this.props.url || prevProps.data !== this.props.data) {
      this.updateContents();
    }
  }

  updateContents () {
    const {
      url,
      data
    } = this.props;
    if (data) {
      this.setState({
        contents: data,
        pending: false,
        error: false
      });
    } else if (!url) {
      this.setState({
        contents: undefined,
        pending: false,
        error: false
      });
    } else {
      this.setState({
        pending: true,
        error: undefined,
        contents: undefined
      }, async () => {
        const state = {
          pending: false,
          error: undefined
        };
        try {
          state.contents = await fetchContents(url);
        } catch (error) {
          state.error = error.message;
        } finally {
          this.setState(state);
        }
      });
    }
  }

  render () {
    const {
      error,
      contents,
      pending
    } = this.state;
    const {
      className,
      style
    } = this.props;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    if (pending) {
      return (
        <LoadingView />
      );
    }
    if (contents) {
      const {
        rows = [],
        columns = []
      } = contents;
      return (
        <table
          className={
            classNames(
              className,
              styles.analysisOutputTable,
              'cell-profiler-results-table'
            )
          }
          style={style}
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
      );
    }
    return null;
  }
}

AnalysisOutputTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  url: PropTypes.string,
  data: PropTypes.object
};

export default AnalysisOutputTable;
