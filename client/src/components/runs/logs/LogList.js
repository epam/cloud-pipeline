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
import PropTypes from 'prop-types';
import {Row, Col, Spin, Input, Button, Checkbox} from 'antd';
import {AutoSizer, List} from 'react-virtualized';
import AU from 'ansi_up';
import displayDate from '../../../utils/displayDate';
import PipelineRunLog from '../../../models/pipelines/PipelineRunLog';
import continuousFetch from '../../../utils/continuous-fetch';
import styles from './Log.css';

// formatNumber converts a 'number' to string with leading zeros.
// 'mask' is a string with N zeros, where N is a fixed number of digits in a resulted string
const formatNumber = (number, mask) => (mask + '' + number).substring((number + '').length);

class LogList extends React.Component {
  ansiUp = new AU();

  state = {
    pending: false,
    logs: [],
    search: false,
    searchText: null,
    searchResults: [],
    currentSearchResultIndex: null,
    autoScroll: true
  };
  _searchInput;

  _lineNumberMask = '000';

  _spanHeightCalculator;

  getRowHeight = ({index}) => {
    const {logs} = this.state;
    if (!this._spanHeightCalculator || !logs || logs.length <= index) {
      return 30;
    }
    this._spanHeightCalculator.innerHTML = this.ansiUp.ansi_to_text(logs[index]);
    const spanHeight = (this._spanHeightCalculator.offsetHeight || 30) + 16;

    return Math.max(spanHeight, 30);
  };

  isError = (text) => {
    const parts = text.split(/[ :\\,\\.!\\~?\[\]\\(\\)\\=\\+\\*\\@\\#]/);
    return parts.indexOf('error') >= 0 || parts.indexOf('fail') >= 0 || parts.indexOf('fatal') >= 0;
  };

  isWarning = (text) => {
    const parts = text.split(/[ :\\,\\.!\\~?\[\]\\(\\)\\=\\+\\*\\@\\#]/);
    return parts.indexOf('warning') >= 0;
  };

  processSearch = (html, text, searchString, colorDefault, colorActive, isActive, position) => {
    const parts = html.split(/(<span[^>]*>|<\/span>)/g);
    const matchRegExp = new RegExp(searchString.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&'), 'g');
    let result;
    let ranges = [];
    while ((result = matchRegExp.exec(text)) !== null) {
      ranges.push({
        start: result.index,
        end: result.index + searchString.length
      });
    }
    const resultParts = [];
    let index = 0;
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i].toLowerCase();
      if (!((part.startsWith('<span') || part.startsWith('</span')) && part.endsWith('>'))) {
        let modifiedPart = parts[i];
        const start = index;
        const end = index + part.length;
        for (let r = ranges.length - 1; r >= 0; r--) {
          let color = colorDefault;
          const range = ranges[r];
          if (isActive && range.start === position) {
            color = colorActive;
          }
          if (range.start < end && range.end > start) {
            const selectFrom = Math.max(range.start, start) - index;
            const selectTo = Math.min(range.end, end) - index;
            const before = modifiedPart.substring(0, selectFrom);
            const match = modifiedPart.substring(selectFrom, selectTo);
            const after = modifiedPart.substring(selectTo);
            modifiedPart = `${before}<span style="background-color:${color}">${match}</span>${after}`;
          }
        }
        resultParts.push(modifiedPart);
        index += part.length;
      } else {
        resultParts.push(parts[i]);
      }
    }
    return resultParts.join('');
  };

  renderLogRow = ({index, style}) => {
    const {
      logs = []
    } = this.state;
    const log = logs[index];
    const textStyle = {};
    const lowerCasedText = this.ansiUp.ansi_to_text(log).toLowerCase();
    if (this.isError(lowerCasedText)) {
      textStyle.color = '#ff4632';
    } else if (this.isWarning(lowerCasedText)) {
      textStyle.color = '#ffa93a';
    }
    const searchText = this.state.searchText || '';
    let currentSearchResultLineIndex = null;
    let currentSearchResultLinePosition = null;
    if (this.state.currentSearchResultIndex !== null &&
      this.state.searchResults.length > this.state.currentSearchResultIndex) {
      currentSearchResultLineIndex =
        this.state.searchResults[this.state.currentSearchResultIndex].index;
      currentSearchResultLinePosition =
        this.state.searchResults[this.state.currentSearchResultIndex].position;
    }
    const renderText = () => {
      if (searchText.length > 0 && lowerCasedText.indexOf(searchText) >= 0) {
        this.ansiUp.bg = null;
        this.ansiUp.fg = null;
        const result = this.processSearch(
          this.ansiUp.ansi_to_html(log),
          lowerCasedText,
          searchText,
          '#877616',
          '#dbbf24',
          index === currentSearchResultLineIndex,
          currentSearchResultLinePosition
        );
        return (
          <span
            style={textStyle}
            dangerouslySetInnerHTML={{__html: result}} />
        );
      } else {
        this.ansiUp.bg = null;
        this.ansiUp.fg = null;
        return (
          <span
            style={textStyle}
            dangerouslySetInnerHTML={{__html: this.ansiUp.ansi_to_html(log)}} />
        );
      }
    };
    return (
      <Row key={index} gutter={16} style={style} className={styles.logRow}>
        <span className={styles.number} style={textStyle}>
          {formatNumber(index + 1, this._lineNumberMask)}:
        </span>
        {renderText()}
      </Row>
    );
  };

  onDataReceived = () => {
    const {logs = []} = this.state;
    if (this.listElement && logs && logs.length > 0 && this.state.autoScroll) {
      this.listElement.scrollToRow(logs.length - 1);
    }
  };

  componentDidUpdate (prevProps) {
    if (
      prevProps.autoUpdate !== this.props.autoUpdate ||
      prevProps.runId !== this.props.runId ||
      prevProps.taskName !== this.props.taskName ||
      prevProps.taskParameters !== this.props.taskParameters ||
      prevProps.taskInstance !== this.props.taskInstance
    ) {
      this.updateFromProps();
    }
  }

  componentDidMount () {
    window.onkeydown = (e) => {
      if (e.code.toLowerCase() === 'keyf' && (e.ctrlKey || e.metaKey)) {
        this.handleSearch();
        e.preventDefault();
        e.stopPropagation();
        return false;
      } else if (e.code.toLowerCase() === 'keyg' && (e.ctrlKey || e.metaKey)) {
        if (e.shiftKey) {
          this.goToPreviousSearchResult();
        } else {
          this.goToNextSearchResult();
        }
        e.preventDefault();
        e.stopPropagation();
        return false;
      } else if (e.keyCode === 27) {
        this.closeSearch();
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
      return true;
    };
    this.updateFromProps();
  }

  fetchToken = 0;

  updateFromProps = () => {
    this.stopAutoUpdate();
    const {
      autoUpdate,
      runId,
      taskName,
      taskParameters,
      taskInstance
    } = this.props;
    this.fetchToken += 1;
    const token = this.fetchToken;
    if (runId) {
      this.setState({
        pending: true
      }, () => {
        const request = new PipelineRunLog(
          runId,
          taskName,
          {
            parameters: taskParameters,
            instance: taskInstance
          }
        );
        const call = async () => {
          await request.fetch();
          if (request.networkError) {
            throw new Error(request.networkError);
          }
        };
        const commit = () => {
          if (token === this.fetchToken) {
            const parseEntry = (entry) => (entry.logText || '')
              .split('\n')
              .map(line => entry.date ? `[${displayDate(entry.date)}] ${line}` : line);
            this.setState({
              logs: (request.value || [])
                .map(parseEntry)
                .reduce((r, c) => ([...r, ...c]), []),
              pending: false
            }, this.onDataReceived);
          }
        };
        const {
          stop
        } = continuousFetch({
          continuous: autoUpdate,
          call,
          afterInvoke: commit,
          fetchImmediate: true
        });
        this.stop = stop;
      });
    } else {
      this.setState({
        pending: false,
        logs: [],
        search: undefined
      });
    }
  };

  stopAutoUpdate = () => {
    if (typeof this.stop === 'function') {
      this.stop();
    }
    this.stop = undefined;
  };

  performSearch = (event) => {
    const {logs = []} = this.state;
    const searchText = event.target.value.toLowerCase();
    if (searchText && searchText.length > 0) {
      if (searchText !== this.state.searchText) {
        if (logs.length > 0) {
          const searchRegExp = new RegExp(
            searchText.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&'),
            'g'
          );
          const result = logs
            .map((log, index) => {
              return {
                log,
                logText: this.ansiUp.ansi_to_text(log.toLowerCase()),
                index
              };
            })
            .filter(log => {
              return log.logText.indexOf(searchText) >= 0;
            }).map(log => {
              let findResult;
              const positions = [];
              while ((findResult = searchRegExp.exec(log.logText)) !== null) {
                positions.push({...log, position: findResult.index});
              }
              return positions;
            }).reduce((acc, current) => {
              acc.push(...current);
              return acc;
            }, []);
          this.setState({
            searchText,
            searchResults: result,
            currentSearchResultIndex: result.length > 0 ? 0 : null
          }, () => {
            this.scrollToCurrentSearchResult();
          });
        }
      } else {
        this.goToNextSearchResult();
      }
    } else {
      this.setState({
        searchText: null,
        searchResults: [],
        currentSearchResultIndex: null
      });
    }
  };

  scrollToCurrentSearchResult = () => {
    if (this.listElement && this.state.currentSearchResultIndex !== null) {
      let currentSearchResultLineIndex = null;
      if (this.state.currentSearchResultIndex !== null &&
        this.state.searchResults.length > this.state.currentSearchResultIndex) {
        currentSearchResultLineIndex =
          this.state.searchResults[this.state.currentSearchResultIndex].index;
      }
      this.listElement.scrollToRow(currentSearchResultLineIndex);
    }
  };

  goToNextSearchResult = () => {
    if (this.state.searchResults.length > 0) {
      let next = (this.state.currentSearchResultIndex || 0) + 1;
      if (next >= this.state.searchResults.length) {
        next = 0;
      }
      this.setState({currentSearchResultIndex: next}, () => {
        this.scrollToCurrentSearchResult();
      });
    }
  };

  goToPreviousSearchResult = () => {
    if (this.state.searchResults.length > 0) {
      let prev = (this.state.currentSearchResultIndex || 0) - 1;
      if (prev < 0) {
        prev = this.state.searchResults.length - 1;
      }
      this.setState({currentSearchResultIndex: prev}, () => {
        this.scrollToCurrentSearchResult();
      });
    }
  };

  handleSearch = () => {
    this.setState({
      search: !this.state.search,
      searchText: null,
      searchResults: [],
      currentSearchResultIndex: null
    }, () => {
      if (this.state.search &&
        this._searchInput &&
        this._searchInput.refs &&
        this._searchInput.refs.input) {
        this._searchInput.refs.input.focus();
      }
    });
  };

  closeSearch = () => {
    this.setState({
      search: false,
      searchText: null,
      searchResults: [],
      currentSearchResultIndex: null
    });
  };

  componentWillUnmount () {
    window.onkeydown = null;
    this.stopAutoUpdate();
  }

  initializeHeightCalculator = (span) => {
    if (span) {
      this._spanHeightCalculator = span;
      if (this.listElement) {
        this.listElement.recomputeRowHeights();
      }
    }
  };

  componentWillUpdate () {
    if (this.listElement) {
      this.listElement.recomputeRowHeights();
    }
  }

  onResize = () => {
    if (this.listElement) {
      this.listElement.recomputeRowHeights();
    }
  };

  render () {
    let Logs;
    const {
      autoUpdate
    } = this.props;
    const {
      logs = [],
      pending
    } = this.state;
    if (pending) {
      this.listElement = null;
      Logs = <Spin />;
    } else if (logs.length === 0) {
      this.listElement = null;
      Logs = <Row key={-1} gutter={16}>
        <Col span={22} offset={2} className={styles.number}>
          No data
        </Col>
      </Row>;
    } else {
      const linesCount = logs.length;
      const lineNumberMaxDigitsCount = Math.max((linesCount + '').length, 3);
      // i.e. '1000' => '000', '10000' => '0000' etc
      this._lineNumberMask = (Math.pow(10, lineNumberMaxDigitsCount) + '').substring(1);
      Logs = <AutoSizer ref={(element) => {
        if (element) {
          this.listElement = element.refs.list;
        }
      }} onResize={this.onResize}>
        {({width, height}) => (
          <List
            ref="list"
            className={styles.logsTable}
            height={height}
            rowHeight={this.getRowHeight}
            rowCount={linesCount}
            rowRenderer={this.renderLogRow}
            width={width}
          />)
        }
      </AutoSizer>;
    }

    const logsTableContainerStyle = {};
    if (this.state.search) {
      logsTableContainerStyle.top = 40;
    }

    return (
      <div>
        { this.state.search &&
        <Row
          align="middle"
          className={styles.searchContainer}
          type="flex">
          <Col span={12} style={{paddingLeft: 10}}>
            <Input
              placeholder="Search"
              onPressEnter={this.performSearch}
              ref={(input) => { this._searchInput = input; }}
              style={{width: '100%'}} />
          </Col>
          <Col span={8} className={styles.searchButtonsContainer}>
            <Button
              size="small"
              onClick={() => this.goToPreviousSearchResult()}
              disabled={
                !this.state.search ||
                !this.state.searchText ||
                this.state.searchResults.length === 0 ||
                this.state.currentSearchResultIndex === 0
              }>
              Prev
            </Button>
            <Button
              size="small"
              onClick={() => this.goToNextSearchResult()}
              disabled={
                !this.state.search ||
                !this.state.searchText ||
                this.state.searchResults.length === 0 ||
                this.state.currentSearchResultIndex === this.state.searchResults.length - 1
              }>
              Next
            </Button>
          </Col>
          <Col span={4} style={{textAlign: 'right', paddingRight: 10}}>
            <Button size="small" onClick={() => this.handleSearch()}>
              Done
            </Button>
          </Col>
        </Row>
        }
        <div className={styles.logsTableContainer} style={logsTableContainerStyle}>
          {
            autoUpdate && (
              <Row
                style={{
                  position: 'absolute',
                  right: 0,
                  top: 0,
                  textAlign: 'right',
                  zIndex: 2,
                  paddingTop: 10,
                  paddingRight: 15
                }}>
                <Checkbox
                  className={styles.scrollToLastCheckBox}
                  onChange={e => this.setState({autoScroll: e.target.checked})}
                  checked={this.state.autoScroll}>
                  Follow log
                </Checkbox>
              </Row>
            )
          }
          <Row gutter={16} className={styles.logRowHeightCalculator}>
            <span className={styles.number}>{formatNumber(0, this._lineNumberMask)}:</span>
            <span ref={this.initializeHeightCalculator} />
          </Row>
          { Logs }
        </div>
      </div>
    );
  }
}

LogList.propTypes = {
  autoUpdate: PropTypes.bool,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  taskName: PropTypes.string,
  taskParameters: PropTypes.string,
  taskInstance: PropTypes.string
};

export default LogList;
