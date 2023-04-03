/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Checkbox, Icon, Input} from 'antd';
import AnsiUP from 'ansi_up';
import FileSaver from 'file-saver';
import PipelineRunLog from '../../../models/pipelines/PipelineRunLog';
import continuousFetch from '../../../utils/continuous-fetch';
import displayDate from '../../../utils/displayDate';
import {searchDialogBlocker} from '../../search/SearchDialog';
import escapeRegExp from '../../../utils/escape-reg-exp';
import styles from './run-task-logs.css';

const SCROLLED_DOWN_OFFSET = 20;
const MAX_LINES_TO_DISPLAY = 500;

const ansiUp = new AnsiUP();

function formatLineNumber (lineNumber, length) {
  if (typeof String.prototype.padStart === 'function') {
    return `${lineNumber}`.padStart(length, '0');
  }
  let result = `${lineNumber}`;
  while (result.length < length) {
    result = '0'.concat(result);
  }
  return result;
}

function markSearch (source, search, options = {}) {
  const {
    lastIndex = 0,
    currentIndex
  } = options;
  const tagRegExp = new RegExp(`(${escapeRegExp(search)})`, 'ig');
  let result = '';
  let e = tagRegExp.exec(source);
  let position = 0;
  const positions = [];
  while (e) {
    const classes = classNames(
      'cp-search-highlight-text',
      {
        inactive: currentIndex !== undefined && lastIndex + positions.length !== currentIndex,
        active: currentIndex !== undefined && lastIndex + positions.length === currentIndex
      }
    );
    positions.push(e.index);
    result = result
      .concat(source.slice(position, e.index))
      .concat(`<span class="${classes}">`)
      .concat(e[0])
      .concat('</span>');
    position = e.index + e[0].length;
    e = tagRegExp.exec(source);
  }
  result = result.concat(source.slice(position));
  return {
    result,
    positions
  };
}

function findText (html, text, options = {}) {
  const {
    lastIndex = 0,
    currentIndex
  } = options;
  const tagRegExp = /<[^>]+>/g;
  let result = '';
  let e = tagRegExp.exec(html);
  let position = 0;
  const positions = [];
  let index = lastIndex;
  while (e) {
    const {
      result: marked,
      positions: partPositions = []
    } = markSearch(html.slice(position, e.index), text, {lastIndex: index, currentIndex});
    index += partPositions.length;
    positions.push(...partPositions.map((n) => n + position));
    result = result
      .concat(marked)
      .concat(e[0]);
    position = e.index + e[0].length;
    e = tagRegExp.exec(html);
  }
  const {
    result: other,
    positions: otherPositions = []
  } = markSearch(html.slice(position), text, {lastIndex: index, currentIndex});
  positions.push(...otherPositions.map((n) => n + position));
  result = result.concat(other);
  return {
    result,
    positions,
    lastIndex: index
  };
}

async function downloadTaskLogs (
  runId,
  task,
  parameters = undefined,
  instance = undefined
) {
  const request = new PipelineRunLog(runId, task, {parameters, instance});
  await request.fetch();
  if (request.error || !request.loaded) {
    throw new Error(request.error || `Error loading logs for task "${task}" (run #${runId})`);
  }
  const logs = (request.value || [])
    .filter((log) => log.logText && log.logText.length)
    .map((log) => log.logText)
    .join('\n');
  FileSaver.saveAs(new Blob([logs]), `${runId}-${task}-logs.txt`);
}

function isError (text) {
  return /([\s:;.!?]|^)(error|fatal|fail)([\s:;.!?]|$)/i.test(text);
}

function isWarning (text) {
  return /([\s:;.!?]|^)(warning|warn|deprecation)([\s:;.!?]|$)/i.test(text);
}

class RunTaskLogs extends React.Component {
  state = {
    logs: [],
    scrolledDown: false,
    maxLinesToDisplay: undefined,
    followLog: false,
    pending: false,
    searchControlVisible: false,
    search: undefined,
    searchResults: [],
    searchResultIndex: 0
  };

  /**
   * @typedef {HTMLDivElement}
   */
  consoleElement;

  componentDidMount () {
    this.loadData();
    this.registerSearchHotKeys();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.runId !== prevProps.runId ||
      this.props.autoUpdate !== prevProps.autoUpdate ||
      this.props.taskName !== prevProps.taskName ||
      this.props.taskParameters !== prevProps.taskParameters ||
      this.props.taskInstance !== prevProps.taskInstance
    ) {
      this.loadData();
    }
    if (this.props.searchAvailable !== prevProps.searchAvailable) {
      this.registerSearchHotKeys();
    }
  }

  componentWillUnmount () {
    this.stopAutoUpdate();
    this.consoleElement = undefined;
    this.unregisterSearchHotKeys();
  }

  get maxLinesToDisplay () {
    const {
      maxLinesToDisplay: maxLinesToDisplayProps = MAX_LINES_TO_DISPLAY
    } = this.props;
    const {
      maxLinesToDisplay = maxLinesToDisplayProps
    } = this.state;
    return maxLinesToDisplay;
  }

  get logsTruncated () {
    const {logs = []} = this.state;
    return logs.length > this.maxLinesToDisplay;
  }

  get currentLogs () {
    const {
      logs = [],
      searchResults = [],
      searchResultIndex
    } = this.state;
    const logsWithIndex = logs.map((log, index) => ({...log, index}));
    return logsWithIndex
      .slice(Math.max(0, logs.length - this.maxLinesToDisplay))
      .map((log) => {
        const lineSearchResult = searchResults.filter((aResult) => aResult.lineIndex === log.index);
        if (lineSearchResult.length === 0) {
          return log;
        }
        const current = lineSearchResult.find((aLine) => aLine.index === searchResultIndex);
        if (current) {
          return {
            ...log,
            logHTML: current.activeLogHTML
          };
        }
        const [any] = lineSearchResult;
        return {
          ...log,
          logHTML: any.logHTML
        };
      });
  }

  get scrolledDown () {
    if (this.consoleElement) {
      const {
        scrollTop,
        scrollHeight,
        clientHeight
      } = this.consoleElement;
      return scrollTop + clientHeight > scrollHeight - SCROLLED_DOWN_OFFSET;
    }
    return false;
  }

  loadData = () => {
    this.stopAutoUpdate();
    const {
      runId,
      taskName,
      taskParameters,
      taskInstance,
      autoUpdate
    } = this.props;
    if (runId && taskName) {
      this.setState({
        logs: [],
        maxLinesToDisplay: undefined,
        followLog: autoUpdate,
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
        const after = () => {
          const {scrolledDown} = this;
          const firstLogs = !this.state.logs || this.state.logs.length === 0;
          const currentLine = this.getCurrentLine();
          const logs = (request.value || [])
            .filter((log) => log.logText && log.logText.length)
            .map((log) => log.logText.split('\n').map((line) => ({
              date: displayDate(log.date),
              log: line,
              logHTML: ansiUp.ansi_to_html(line),
              isError: isError(line),
              isWarning: isWarning(line)
            })))
            .reduce((r, c) => ([...r, ...c]), []);
          this.setState(
            {
              logs,
              pending: false
            },
            () => {
              const {followLog} = this.state;
              const firstLineIndex = Math.max(0, this.state.logs.length - this.maxLinesToDisplay);
              if ((followLog || firstLogs) && (scrolledDown || !currentLine)) {
                this.scrollDown(!firstLogs);
              } else if (
                !scrolledDown &&
                currentLine &&
                currentLine.index < firstLineIndex
              ) {
                this.scrollUp();
              }
            }
          );
        };
        const {
          stop
        } = continuousFetch({
          continuous: autoUpdate,
          fetchImmediate: true,
          call,
          afterInvoke: after
        });
        this.stop = stop;
      });
    } else {
      this.setState({
        logs: [],
        pending: false,
        followLog: false,
        maxLinesToDisplay: undefined
      });
    }
  };

  stopAutoUpdate = () => {
    if (typeof this.stop === 'function') {
      this.stop();
      this.stop = undefined;
    }
  };

  initializeConsole = (div) => {
    this.consoleElement = div;
    this.scrollDown();
  };

  getCurrentLine = () => {
    if (this.consoleElement) {
      const [firstLog] = this.currentLogs;
      const {
        scrollTop,
        childNodes
      } = this.consoleElement;
      let lastVisible;
      let first;
      for (const child of childNodes) {
        if (child.dataset && child.dataset.line) {
          if (firstLog && child.dataset.line === `${firstLog.index}`) {
            first = child;
          }
          if (child.offsetTop <= scrollTop) {
            lastVisible = child;
          } else {
            break;
          }
        }
      }
      lastVisible = lastVisible || first;
      if (lastVisible && lastVisible.dataset && lastVisible.dataset.line) {
        return {
          index: Number(lastVisible.dataset.line),
          offset: scrollTop - lastVisible.offsetTop
        };
      }
      return {
        index: 0,
        offset: 0
      };
    }
    return undefined;
  };

  scrollUp = () => {
    if (this.consoleElement) {
      this.setState({scrolledDown: false}, () => {
        this.consoleElement.scrollTo({top: 0});
      });
    }
  };

  scrollToLine = (line, offset = 0) => {
    if (this.consoleElement) {
      const {
        searchControlVisible
      } = this.state;
      const {
        childNodes
      } = this.consoleElement;
      let lineElement;
      for (const child of childNodes) {
        if (child.dataset && child.dataset.line === `${line}`) {
          lineElement = child;
          break;
        }
      }
      if (lineElement) {
        this.consoleElement.scrollTo({
          top: lineElement.offsetTop + offset - (searchControlVisible ? 30 : 0)
        });
      }
    }
  };

  scrollDown = (animated = false) => {
    if (this.consoleElement) {
      const top = Math.max(0, this.consoleElement.scrollHeight - this.consoleElement.clientHeight);
      this.setState({scrolledDown: true}, () => {
        this.consoleElement.scrollTo({top, behavior: animated ? 'smooth' : 'auto'});
      });
    }
  };

  handleScroll = () => {
    const {scrolledDown} = this.state;
    if (scrolledDown !== this.scrolledDown) {
      this.setState({scrolledDown: this.scrolledDown});
    }
  };

  onExpandClicked = () => {
    const {
      maxLinesToDisplay: maxLinesToDisplayProps = MAX_LINES_TO_DISPLAY
    } = this.props;
    const {
      maxLinesToDisplay = maxLinesToDisplayProps
    } = this.state;
    const currentLine = this.getCurrentLine();
    this.setState({
      maxLinesToDisplay: maxLinesToDisplay + maxLinesToDisplayProps
    }, () => {
      if (currentLine) {
        this.scrollToLine(currentLine.index, currentLine.offset);
      }
      this.doSearch();
    });
  };

  onChangeFollowLog = (event) => {
    const {autoUpdate} = this.props;
    this.setState({
      followLog: event.target.checked
    }, () => {
      if (autoUpdate && this.state.followLog) {
        this.scrollDown(true);
      }
    });
  };

  registerSearchHotKeys = () => {
    this.unregisterSearchHotKeys();
    const {
      searchAvailable
    } = this.props;
    if (searchAvailable) {
      window.addEventListener('keydown', this.windowKeyDown);
      searchDialogBlocker.blocked = true;
    }
  };

  unregisterSearchHotKeys = () => {
    window.removeEventListener('keydown', this.windowKeyDown);
    searchDialogBlocker.blocked = false;
  };

  windowKeyDown = (event) => {
    if (/^KeyF$/i.test(event.code) && (event.ctrlKey || event.metaKey)) {
      // ctrl + F
      this.openSearch();
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
    if (/^KeyG$/i.test(event.code) && (event.ctrlKey || event.metaKey)) {
      if (event.shiftKey) {
        this.previousSearchResult();
      } else {
        this.nextSearchResult();
      }
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
    if (/^(Escape|esc)$/i.test(event.key) || event.keyCode === 27) {
      this.closeSearch();
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
    return true;
  };

  openSearch = () => {
    this.setState({
      searchControlVisible: true,
      search: undefined,
      searchResults: []
    });
  }

  closeSearch = () => {
    this.setState({
      searchControlVisible: false,
      search: undefined,
      searchResults: []
    });
  };

  scrollToSearchResult = () => {
    const {
      searchResults = [],
      searchResultIndex
    } = this.state;
    const searchResult = searchResults[searchResultIndex];
    if (searchResult) {
      this.scrollToLine(searchResult.lineIndex);
    }
  };

  nextSearchResult = () => {
    const {
      searchResults,
      searchResultIndex
    } = this.state;
    this.setState({
      searchResultIndex: searchResultIndex >= searchResults.length - 1
        ? 0
        : searchResultIndex + 1
    }, this.scrollToSearchResult);
  };

  previousSearchResult = () => {
    const {
      searchResults,
      searchResultIndex
    } = this.state;
    this.setState({
      searchResultIndex: searchResultIndex === 0
        ? Math.max(0, searchResults.length - 1)
        : searchResultIndex - 1
    }, this.scrollToSearchResult);
  };

  doSearch = () => {
    const {search} = this.state;
    const {
      logs = []
    } = this.state;
    const currentLogs = logs
      .map((log, index) => ({...log, index}))
      .slice(Math.max(0, logs.length - this.maxLinesToDisplay));
    const searchResults = currentLogs
      .map((log) => {
        if (!search || search.length < 2) {
          return [];
        }
        const {
          logHTML
        } = log;
        const {
          result,
          positions
        } = findText(logHTML, search, {currentIndex: -1});
        return positions.map((position, idx) => ({
          lineIndex: log.index,
          position,
          logHTML: result,
          activeLogHTML: findText(logHTML, search, {currentIndex: idx}).result
        }));
      })
      .reduce((r, c) => ([...r, ...c]), [])
      .map((result, index) => ({
        ...result,
        index
      }));
    this.setState({
      searchResults,
      searchResultIndex: 0
    }, this.scrollToSearchResult);
  };

  onChangeSearch = (event) => {
    const search = event.target.value;
    this.setState({
      search,
      searchResultIndex: 0
    }, () => this.doSearch());
  };

  render () {
    const {
      className,
      style = {},
      showDate,
      showLineNumber,
      autoUpdate
    } = this.props;
    const {
      followLog,
      scrolledDown,
      pending,
      searchResults = [],
      searchControlVisible,
      searchResultIndex,
      search
    } = this.state;
    const logs = this.currentLogs;
    const lineNumberLength = Math.max(3, Math.floor(Math.log10(logs.length) + 1));
    return (
      <div
        className={
          classNames(
            styles.container,
            className
          )
        }
        style={style}
      >
        <div
          ref={this.initializeConsole}
          className={
            classNames(
              'cp-console-output',
              styles.console
            )
          }
          onScroll={this.handleScroll}
        >
          {
            this.logsTruncated && (
              <div
                className={styles.expandMore}
              >
                Last {logs.length} lines are displayed.
                <a
                  style={{marginLeft: 5}}
                  onClick={this.onExpandClicked}
                >
                  Expand more
                </a>
              </div>
            )
          }
          {
            logs.length === 0 && (
              <div
                className={
                  classNames(
                    styles.emptyLogs,
                    {
                      [styles.emptyLogs]: !pending,
                      [styles.pendingContainer]: pending
                    }
                  )
                }
              >
                {
                  pending
                    ? (<Icon type="loading" />)
                    : (<span>No data</span>)
                }
              </div>
            )
          }
          {
            searchControlVisible && (
              <div className={styles.searchPlaceholder} />
            )
          }
          {
            logs.map((log) => (
              <div
                className={styles.consoleLine}
                key={`log-line-${log.index}`}
                data-line={log.index}
              >
                {
                  showLineNumber && (
                    <span
                      className={styles.consoleLineInfoData}
                    >
                      {formatLineNumber(log.index + 1, lineNumberLength)}:
                    </span>
                  )
                }
                {
                  showDate && (
                    <span
                      className={styles.consoleLineInfoData}
                    >
                      [{log.date}]
                    </span>
                  )
                }
                <span
                  className={
                    classNames({
                      'cp-error': log.isError,
                      'cp-warning': log.isWarning
                    })
                  }
                  dangerouslySetInnerHTML={{__html: log.logHTML}}
                />
              </div>
            ))
          }
        </div>
        {
          searchControlVisible && (
            <div
              className={
                classNames(
                  styles.searchContainer,
                  'cp-panel cp-panel-borderless'
                )
              }
            >
              <Input
                className={styles.searchInput}
                size="small"
                autoFocus
                value={search}
                onChange={this.onChangeSearch}
              />
              <Button
                size="small"
                style={{marginLeft: 5}}
                onClick={this.previousSearchResult}
              >
                Prev
              </Button>
              <Button
                size="small"
                style={{marginLeft: 5}}
                onClick={this.nextSearchResult}
              >
                Next
              </Button>
              {
                searchResults.length > 0 && (
                  <span
                    style={{marginLeft: 5, marginRight: 5}}
                  >
                    {searchResultIndex + 1} of {searchResults.length}
                  </span>
                )
              }
              <Button
                size="small"
                onClick={this.closeSearch}
                style={{marginLeft: 'auto'}}
              >
                Close
              </Button>
              {
                logs.length > 0 && (
                  <Checkbox
                    checked={followLog}
                    onChange={this.onChangeFollowLog}
                    style={{marginLeft: 15}}
                  >
                    Follow log
                  </Checkbox>
                )
              }
            </div>
          )
        }
        {
          autoUpdate && logs.length > 0 && !searchControlVisible && (
            <div
              className={
                classNames(
                  'cp-console-follow-log',
                  styles.followLogContainer
                )
              }
            >
              <Checkbox
                checked={followLog}
                onChange={this.onChangeFollowLog}
              >
                Follow log
              </Checkbox>
            </div>
          )
        }
        {
          !scrolledDown && (
            <div
              className={
                classNames(
                  'cp-console-scroll-down-indicator',
                  styles.scrollDownContainer
                )
              }
              onClick={() => this.scrollDown(true)}
            >
              <Icon type="down" />
            </div>
          )
        }
      </div>
    );
  }
}

RunTaskLogs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.number,
  taskName: PropTypes.string,
  taskParameters: PropTypes.string,
  taskInstance: PropTypes.string,
  autoUpdate: PropTypes.bool,
  maxLinesToDisplay: PropTypes.number,
  showLineNumber: PropTypes.bool,
  showDate: PropTypes.bool,
  searchAvailable: PropTypes.bool
};

RunTaskLogs.defaultProps = {
  showLineNumber: true,
  showDate: true,
  searchAvailable: true
};

export {downloadTaskLogs};
export default RunTaskLogs;
