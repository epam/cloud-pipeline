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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {Row, Col, Spin, Input, Button, Checkbox} from 'antd';
import {AutoSizer, List} from 'react-virtualized';
import AU from 'ansi_up';
import pipelineRun from '../../../models/pipelines/PipelineRun';
import connect from '../../../utils/connect';
import styles from './Log.css';

// formatNumber converts a 'number' to string with leading zeros.
// 'mask' is a string with N zeros, where N is a fixed number of digits in a resulted string
const formatNumber = (number, mask) => (mask + '' + number).substring((number + '').length);

@connect({
  pipelineRun
})
@inject(({pipelineRun, routing}, params) => {
  const parameters = routing.location.search &&
    routing.location.search.substring(1, routing.location.search.length);

  return {
    Run: pipelineRun.run(params.runId),
    logs: pipelineRun.logs(params.runId, params.taskName, parameters)
  };
})
@observer
class LogList extends Component {
  ansiUp = new AU();

  state = {
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
    if (!this._spanHeightCalculator || !this.props.logs.value) {
      return 30;
    }
    this._spanHeightCalculator.innerHTML = this.ansiUp.ansi_to_text(this.props.logs.value[index]);
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
    const log = this.props.logs.value[index];
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
        let color = '#877616';
        if (index === currentSearchResultLineIndex) {
          color = '#dbbf24';
        }
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

  componentWillReceiveProps (nextProps) {
    if (this.props.logs &&
      (
        nextProps.taskName !== this.props.taskName ||
        nextProps.runId !== this.props.runId
      )) {
      this.props.logs.clearInterval();
    }
  }

  onDataReceived = () => {
    if (this.listElement && this.props.logs.value && this.state.autoScroll) {
      this.listElement.scrollToRow((this.props.logs.value || []).length - 1);
    }
  };

  componentDidUpdate () {
    this.props.logs.onDataReceived = () => this.onDataReceived();
    if (this.props.Run.loaded) {
      const {status} = this.props.Run.value;
      if (status === 'RUNNING') {
        this.props.logs.startInterval();
      } else {
        this.props.logs.clearInterval();
      }
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
  }

  performSearch = (event) => {
    const searchText = event.target.value.toLowerCase();
    if (searchText && searchText.length > 0) {
      if (searchText !== this.state.searchText) {
        if (this.props.logs && !this.props.logs.error) {
          const logs = this.props.logs.value.map(l => l);
          const searchRegExp = new RegExp(searchText.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&'), 'g');
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
    this.props.logs.clearInterval();
    this.props.logs.clearInterval();
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
    if (this.props.logs.pending) {
      this.listElement = null;
      Logs = <Spin />;
    } else if (this.props.logs.value.length === 0) {
      this.listElement = null;
      Logs = <Row key={-1} gutter={16}>
        <Col span={22} offset={2} className={styles.number}>
          No data
        </Col>
      </Row>;
    } else if (this.props.logs.value) {
      const linesCount = this.props.logs.value.length;
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
            this.props.Run.loaded && this.props.Run.value.status === 'RUNNING' &&
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

export default LogList;
