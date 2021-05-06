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
import {Alert} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import ResolveChanges from './controls/resolve-changes';
import Scrollbar from './controls/scrollbar';
import ConflictedFile from './utilities/conflicted-file';
import Branches, {HeadBranch, RemoteBranch, Merged} from './utilities/conflicted-file/branches';
import renderModifications from './utilities/modifications-canvas';
import BranchCode from './utilities/branch-code';
import {findLineIndexByPosition, findLinePositionByIndex} from './utilities/line-positioning';
import styles from './conflicts.css';

const LINE_HEIGHT = 22.0;
const SCROLL_CENTER_ORIGIN = 0.5;
const CANVAS_WIDTH = 20;
const MINIMUM_PANEL_SIZE_PIXELS = 200;

const getGridColumns = (panels) => {
  const gridColumnSize = size => /fr$/i.test(size) ? size : `${size}px`;
  const gridTemplateColumn = branch => `[${branch}] ${gridColumnSize(panels[branch])}`;
  return [
    `[SCROLL-${HeadBranch}] 0px`,
    gridTemplateColumn(HeadBranch),
    `[CANVAS-${HeadBranch}] ${CANVAS_WIDTH}px`,
    gridTemplateColumn(Merged),
    `[SCROLL-${Merged}] 0px`,
    `[CANVAS-${RemoteBranch}] ${CANVAS_WIDTH}px`,
    gridTemplateColumn(RemoteBranch),
    `[SCROLL-${RemoteBranch}] 0px`
  ].join(' ');
};

class Conflict extends React.PureComponent {
  state = {
    error: undefined,
    conflictedFile: undefined,
    ide: {
      width: undefined,
      height: undefined
    },
    panels: {
      [HeadBranch]: `1fr`,
      [Merged]: `1fr`,
      [RemoteBranch]: `1fr`
    }
  };

  scrolls = {};
  codeAreas = {};
  verticalScrollBars = {};
  horizontalScrollBars = {};
  blockScroll;
  canvases = {};
  ideContainer;
  ideContainerSizeTimer;
  resizeInfo;

  componentDidMount () {
    window.addEventListener('keydown', this.keyDown);
    window.addEventListener('mousemove', this.onResizeMove);
    window.addEventListener('mouseup', this.onFinishResizing);
    this.updateFromProps();
    this.ideContainerSizeTimer = setInterval(this.updateIDEContainerSize, 100);
  }

  componentWillUnmount () {
    window.removeEventListener('keydown', this.keyDown);
    window.removeEventListener('mousemove', this.onResizeMove);
    window.removeEventListener('mouseup', this.onFinishResizing);
    if (this.ideContainerSizeTimer) {
      clearInterval(this.ideContainerSizeTimer);
      this.ideContainerSizeTimer = undefined;
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.file !== this.props.file) {
      this.updateFromProps();
    }
    this.draw();
    this.updateScrollBars();
  }

  updateFromProps = () => {
    const {
      conflictsSession,
      file
    } = this.props;
    if (conflictsSession && file) {
      const conflictsSessionFile = conflictsSession.getFile(file);
      if (conflictsSessionFile) {
        conflictsSessionFile
          .getInfo()
          .then((conflictedFile) => {
            this.setState({
              conflictedFile,
              error: undefined
            }, this.draw);
          })
          .catch(e => {
            this.setState({
              conflictedFile: undefined,
              error: e.message
            }, this.draw);
          });
      }
    } else {
      this.setState({
        conflictedFile: undefined,
        error: undefined
      }, this.draw);
    }
  };

  keyDown = (e) => {
    if (e && e.keyCode === 90) {
      this.undoLastChange(e);
    }
  };

  undoLastChange = (e) => {
    const {disabled} = this.props;
    if (disabled) {
      return;
    }
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    const {conflictedFile} = this.state;
    if (conflictedFile && typeof conflictedFile.undo === 'function') {
      conflictedFile.undo(this.refresh);
    }
  };

  refresh = () => {
    this.forceUpdate();
    this.draw();
  };

  onStartResizing = (branch) => (e) => {
    e.stopPropagation();
    e.preventDefault();
    const panels = Branches.map(b => ({
      [b]: (this.scrolls[b] ? this.scrolls[b].clientWidth : 0) || '1fr'
    })).reduce((r, c) => ({...r, ...c}), {});
    this.resizeInfo = {
      branch,
      start: e.clientX,
      panels,
      gridColumns: panels
    };
  };

  onResizeMove = (e) => {
    if (this.resizeInfo) {
      e.stopPropagation();
      e.preventDefault();
      const {
        start,
        branch,
        panels
      } = this.resizeInfo;
      const delta = (e.clientX - start) * (branch === HeadBranch ? 1 : -1);
      const total = [branch, Merged]
        .reduce((sum, affectedBranch) => sum + panels[affectedBranch], 0);
      const correctSize = size => Math.min(
        total - MINIMUM_PANEL_SIZE_PIXELS,
        Math.max(
          MINIMUM_PANEL_SIZE_PIXELS,
          size
        )
      );
      const mainPanelSize = correctSize(panels[branch] + delta);
      const mergedPanelSize = correctSize(total - mainPanelSize);
      const newPanelSizes = {
        ...panels,
        ...{
          [branch]: mainPanelSize,
          [Merged]: mergedPanelSize
        }
      };
      if (this.ideContainer) {
        this.ideContainer.style['grid-template-columns'] = getGridColumns(newPanelSizes);
      }
      this.resizeInfo.gridColumns = newPanelSizes;
      this.updateScrollBars();
    }
  };

  onFinishResizing = (e) => {
    if (this.resizeInfo) {
      this.onResizeMove(e);
      const {gridColumns} = this.resizeInfo;
      this.resizeInfo = null;
      const total = Object
        .values(gridColumns).reduce((sum, current) => sum + current, 0);
      const frValue = branch => `${(gridColumns[branch] / total * 3)}fr`;
      this.setState({
        panels: {
          [HeadBranch]: frValue(HeadBranch),
          [Merged]: frValue(Merged),
          [RemoteBranch]: frValue(RemoteBranch)
        }
      });
      this.updateScrollBars();
    }
  };

  renderIDE = () => {
    const {
      conflictedFile,
      error,
      ide = {}
    } = this.state;
    const {
      disabled
    } = this.props;
    if (!error && conflictedFile) {
      const branches = {
        [HeadBranch]: conflictedFile.getLines(HeadBranch),
        [Merged]: conflictedFile.getLines(Merged),
        [RemoteBranch]: conflictedFile.getLines(RemoteBranch)
      };
      const rawBranches = {
        [HeadBranch]: conflictedFile.getLines(HeadBranch, new Set([])),
        [Merged]: conflictedFile.getLines(Merged, new Set([])),
        [RemoteBranch]: conflictedFile.getLines(RemoteBranch, new Set([]))
      };
      const attachScrollArea = (node, branch) => {
        this.scrolls[branch] = node;
        if (this.verticalScrollBars[branch]) {
          this.verticalScrollBars[branch].attach(this.scrolls[branch]);
        }
      };
      const attachCodeArea = (node, branch) => {
        this.codeAreas[branch] = node;
        if (this.horizontalScrollBars[branch]) {
          this.horizontalScrollBars[branch].attach(this.codeAreas[branch]);
        }
      };
      const attachVerticalScrollBar = (node, branch) => {
        if (this.verticalScrollBars[branch]) {
          this.verticalScrollBars[branch].detach();
        }
        this.verticalScrollBars[branch] = node;
        if (this.verticalScrollBars[branch]) {
          this.verticalScrollBars[branch].attach(this.scrolls[branch]);
        }
      };
      const attachHorizontalScrollBar = (node, branch) => {
        if (this.horizontalScrollBars[branch]) {
          this.horizontalScrollBars[branch].detach();
        }
        this.horizontalScrollBars[branch] = node;
        if (this.horizontalScrollBars[branch]) {
          this.horizontalScrollBars[branch].attach(this.codeAreas[branch]);
        }
      };
      const correctBranchItemIndex = (index, branch) => {
        if (branch && Array.isArray(branches[branch])) {
          return Math.max(
            0,
            Math.min(branches[branch].length - 1,
              index
            )
          );
        }
        return index;
      };
      const onVerticalScroll = (e, branch) => {
        const {target} = e;
        if (this.verticalScrollBars[branch]) {
          this.verticalScrollBars[branch].renderBar();
        }
        if (!this.blockScroll || this.blockScroll === branch) {
          const {
            scrollTop,
            clientHeight
          } = target;
          this.blockScroll = branch;
          const doSynchronousScroll = () => {
            if (branch && Array.isArray(branches[branch]) && branches[branch].length > 0) {
              const center = clientHeight * SCROLL_CENTER_ORIGIN + scrollTop;
              const centerIdx = findLineIndexByPosition(
                center,
                branches[branch],
                branch,
                LINE_HEIGHT
              );
              const up = correctBranchItemIndex(Math.floor(centerIdx), branch);
              const down = correctBranchItemIndex(Math.ceil(centerIdx), branch);
              const upItem = branches[branch][up];
              const downItem = branches[branch][down];
              const commonParent = ConflictedFile.findCommonParentFor(
                upItem,
                ...Branches.map(b => branches[b])
              );
              const commonChild = ConflictedFile.findCommonChildFor(
                downItem,
                ...Branches.map(b => branches[b])
              );
              if (commonParent && commonChild) {
                const idxStart = branches[branch].indexOf(commonParent);
                const idxEnd = branches[branch].indexOf(commonChild);
                const range = Math.max(1, idxEnd - idxStart);
                const localOffset = centerIdx - idxStart;
                const localRatio = localOffset / range;
                for (let otherBranch of Branches) {
                  const scroll = this.scrolls[otherBranch];
                  if (scroll && otherBranch !== branch) {
                    const oIdxStart = branches[otherBranch].indexOf(commonParent);
                    const oIdxEnd = branches[otherBranch].indexOf(commonChild);
                    const oRange = Math.max(1, oIdxEnd - oIdxStart);
                    const oLocalOffset = oRange * localRatio;
                    const oLocalCenter = oIdxStart + oLocalOffset;
                    scroll.scrollTop = Math.round(
                      Math.max(
                        0,
                        findLinePositionByIndex(
                          branches[otherBranch],
                          oLocalCenter,
                          otherBranch,
                          LINE_HEIGHT
                        ) - scroll.clientHeight * SCROLL_CENTER_ORIGIN
                      )
                    );
                  }
                }
              }
            }
            this.draw();
            if (this.setBlockScroll) {
              clearTimeout(this.setBlockScroll);
              this.setBlockScroll = undefined;
            }
            this.setBlockScroll = setTimeout(() => {
              this.blockScroll = undefined;
            }, 100);
          };
          requestAnimationFrame(doSynchronousScroll);
        }
      };
      const onHorizontalScroll = (e, branch) => {
        const {target} = e;
        if (!this.blockScroll || this.blockScroll === branch) {
          e.stopPropagation();
          e.preventDefault();
          const {scrollLeft} = target;
          this.blockScroll = branch;
          if (this.horizontalScrollBars[branch]) {
            this.horizontalScrollBars[branch].renderBar();
          }
          for (let otherBranch of Branches) {
            if (otherBranch !== branch) {
              const scroll = this.codeAreas[otherBranch];
              if (scroll) {
                scroll.scrollLeft = scrollLeft;
              }
            }
          }
          if (this.setBlockScroll) {
            clearTimeout(this.setBlockScroll);
            this.setBlockScroll = undefined;
          }
          this.setBlockScroll = setTimeout(() => {
            this.blockScroll = undefined;
          }, 100);
        }
      };
      const renderBranchCodeLineNumbers = (branch, modificationsBranch, rtl = false) => (
        <BranchCode.LineNumbers
          disabled={disabled}
          key={`${branch}-code-line-numbers`}
          branch={branch}
          hideModificationActions={branch === Merged}
          lines={rawBranches[branch]}
          modificationsBranch={modificationsBranch || branch}
          lineHeight={LINE_HEIGHT}
          onRefresh={this.refresh}
          rtl={rtl}
          style={{
            minHeight: ide.height || 0,
            cursor: 'col-resize'
          }}
          onMouseDown={this.onStartResizing(modificationsBranch || branch)}
        />
      );
      const getNumbersContainerWidthCss = (branch) => {
        const numbersLength = Math.floor(Math.log10(rawBranches[branch].length)) + 1;
        return {
          em: numbersLength + (branch === Merged),
          px: branch !== Merged ? 50 : 0
        };
      };
      const multiplyWidthCssByTimes = (widthCss, times) => {
        const {
          em = 0,
          px = 0
        } = widthCss || {};
        return {
          em: em * times,
          px: px * times
        };
      };
      const addWidthsCss = (...widthCss) => {
        return widthCss.reduce((res, cur) => ({
          em: (res.em || 0) + (cur.em || 0),
          px: (res.px || 0) + (cur.px || 0)
        }), {});
      };
      const getCalcPresentation = (widthCss) => {
        const {
          em = 0,
          px = 0
        } = widthCss || {};
        const parts = [
          em && {value: em, unit: 'em'},
          px && {value: px, unit: 'px'}
        ].filter(Boolean);
        if (parts.length === 0) {
          return '0px';
        }
        const result = parts.reduce((result, current, index) => {
          if (index > 0 && current.value >= 0) {
            return result.concat(` + ${current.value}${current.unit}`);
          } else if (index > 0) {
            return result.concat(` - ${Math.abs(current.value)}${current.unit}`);
          }
          return result.concat(`${current.value}${current.unit}`);
        }, '');
        return `calc(${result})`;
      };
      const renderBranch = (branch) => {
        let left, right;
        switch (branch) {
          case HeadBranch:
            right = (
              <div
                style={{gridColumn: 'RIGHT-NUMBERS'}}
                className={
                  classNames(
                    styles.numbersPanel,
                    styles.right
                  )
                }
              >
                {renderBranchCodeLineNumbers(branch)}
              </div>
            );
            break;
          case RemoteBranch:
            left = (
              <div
                style={{gridColumn: 'LEFT-NUMBERS'}}
                className={
                  classNames(
                    styles.numbersPanel,
                    styles.left
                  )
                }
              >
                {renderBranchCodeLineNumbers(branch, undefined, true)}
              </div>
            );
            break;
          case Merged:
            right = (
              <div
                style={{gridColumn: 'RIGHT-NUMBERS'}}
                className={
                  classNames(
                    styles.numbersPanel,
                    styles.right
                  )
                }
              >
                {renderBranchCodeLineNumbers(branch, RemoteBranch, true)}
              </div>
            );
            left = (
              <div
                style={{gridColumn: 'LEFT-NUMBERS'}}
                className={
                  classNames(
                    styles.numbersPanel,
                    styles.left
                  )
                }
              >
                {renderBranchCodeLineNumbers(branch, HeadBranch)}
              </div>
            );
            break;
        }
        const numbersWidth = getCalcPresentation(getNumbersContainerWidthCss(branch));
        const templateColumns = [
          left && `[LEFT-NUMBERS] ${numbersWidth}`,
          '[CODE] 1fr',
          right && `[RIGHT-NUMBERS] ${numbersWidth}`
        ].filter(Boolean);
        return (
          <div
            className={styles.panel}
            key={branch}
            ref={node => attachScrollArea(node, branch)}
            onScroll={e => onVerticalScroll(e, branch)}
            style={{
              gridColumn: branch,
              gridTemplateColumns: templateColumns.join(' ')
            }}
          >
            {left}
            <BranchCode
              className={styles.code}
              onInitialized={node => attachCodeArea(node, branch)}
              onScroll={e => onHorizontalScroll(e, branch)}
              branch={branch}
              lines={rawBranches[branch]}
              lineHeight={LINE_HEIGHT}
              style={{gridColumn: 'CODE'}}
              lineStyle={{
                paddingLeft: Scrollbar.size,
                paddingRight: Scrollbar.size
              }}
            />
            {right}
          </div>
        );
      };
      const renderCanvas = (branch) => (
        <canvas
          className={styles.resize}
          width={CANVAS_WIDTH * window.devicePixelRatio}
          height={(ide.height || 0) * window.devicePixelRatio}
          key={`${branch}-canvas`}
          ref={canvas => this.initializeCanvas(branch, canvas)}
          style={{
            width: CANVAS_WIDTH,
            height: ide.height || 0,
            gridColumn: `CANVAS-${branch}`
          }}
          onMouseDown={this.onStartResizing(branch)}
        />
      );
      const renderVerticalScroll = (branch, offsets) => {
        const {
          left = 0,
          right = 0,
          top = 0,
          bottom = Scrollbar.size
        } = offsets || {};
        return (
          <div
            className={
              classNames(
                styles.scrollContainer,
                styles.vertical
              )
            }
            key={`vertical scroll ${branch}`}
            style={{gridColumn: `SCROLL-${branch}`}}
          >
            <Scrollbar
              onInitialized={node => attachVerticalScrollBar(node, branch)}
              className={styles.scrollbar}
              direction="vertical"
              width={Scrollbar.size}
              style={{
                left,
                right,
                top,
                bottom
              }}
            />
          </div>
        );
      };
      const renderHorizontalScroll = (branch, offsets) => {
        const {
          left = 0,
          right = 0,
          top = -Scrollbar.size,
          bottom = 0
        } = offsets || {};
        return (
          <div
            className={
              classNames(
                styles.scrollContainer,
                styles.horizontal
              )
            }
            key={`horizontal scroll ${branch}`}
            style={{gridColumn: branch}}
          >
            <Scrollbar
              onInitialized={node => attachHorizontalScrollBar(node, branch)}
              className={styles.scrollbar}
              direction="horizontal"
              height={Scrollbar.size}
              style={{
                left,
                right,
                top,
                bottom
              }}
            />
          </div>
        );
      };
      return [
        renderVerticalScroll(HeadBranch),
        renderBranch(HeadBranch),
        renderHorizontalScroll(
          HeadBranch,
          {
            left: Scrollbar.size,
            right: getCalcPresentation(getNumbersContainerWidthCss(HeadBranch))
          }
        ),
        renderCanvas(HeadBranch),
        renderBranch(Merged),
        renderHorizontalScroll(
          Merged,
          {
            left: getCalcPresentation(getNumbersContainerWidthCss(Merged)),
            right: getCalcPresentation(
              addWidthsCss(
                getNumbersContainerWidthCss(Merged),
                {px: Scrollbar.size}
              )
            )
          }
        ),
        renderVerticalScroll(
          Merged,
          {
            left: getCalcPresentation(
              addWidthsCss(
                multiplyWidthCssByTimes(
                  getNumbersContainerWidthCss(Merged),
                  -1
                ),
                {px: -Scrollbar.size}
              )
            )
          }
        ),
        renderCanvas(RemoteBranch),
        renderBranch(RemoteBranch),
        renderHorizontalScroll(
          RemoteBranch,
          {
            left: getCalcPresentation(getNumbersContainerWidthCss(RemoteBranch)),
            right: Scrollbar.size
          }
        ),
        renderVerticalScroll(RemoteBranch, {left: -Scrollbar.size})
      ];
    }
    return null;
  };

  initializeIDEContainer = (node) => {
    this.ideContainer = node;
  }

  initializeCanvas = (branch, canvas) => {
    this.canvases[branch] = canvas;
    this.draw();
  }

  updateScrollBars = () => {
    Object.values(this.verticalScrollBars || {}).forEach(scrollBar => scrollBar.renderBar());
    Object.values(this.horizontalScrollBars || {}).forEach(scrollBar => scrollBar.renderBar());
  };

  draw = () => {
    const {conflictedFile} = this.state;
    renderModifications(
      this.canvases[HeadBranch],
      conflictedFile,
      HeadBranch,
      {
        width: CANVAS_WIDTH,
        top: this.scrolls[HeadBranch] ? this.scrolls[HeadBranch].scrollTop : 0,
        mergedTop: this.scrolls[Merged]
          ? this.scrolls[Merged].scrollTop
          : 0,
        lineHeight: LINE_HEIGHT
      }
    );
    renderModifications(
      this.canvases[RemoteBranch],
      conflictedFile,
      RemoteBranch,
      {
        width: CANVAS_WIDTH,
        top: this.scrolls[RemoteBranch]
          ? this.scrolls[RemoteBranch].scrollTop
          : 0,
        mergedTop: this.scrolls[Merged]
          ? this.scrolls[Merged].scrollTop
          : 0,
        lineHeight: LINE_HEIGHT,
        rtl: true
      }
    );
  };

  updateIDEContainerSize = () => {
    if (this.ideContainer && !this.resizeInfo) {
      const width = this.ideContainer.clientWidth;
      const height = this.ideContainer.clientHeight;
      const {
        ide
      } = this.state;
      if (ide.width !== width || ide.height !== height) {
        this.setState({
          ide: {
            width,
            height
          }
        });
      }
    }
  };

  render () {
    const {
      conflictedFile,
      error,
      panels
    } = this.state;
    return (
      <div className={styles.conflictContainer}>
        {
          error && (
            <Alert type="error" message={error} />
          )
        }
        <ResolveChanges
          className={styles.resolveChanges}
          conflictedFile={conflictedFile}
        />
        <div
          data-conflicted-file-changes-hash={conflictedFile ? conflictedFile.changesHash : 0}
          className={styles.resolveArea}
          ref={this.initializeIDEContainer}
          style={{
            gridTemplateColumns: getGridColumns(panels)
          }}
        >
          {this.renderIDE()}
        </div>
      </div>
    );
  }
}

Conflict.propTypes = {
  disabled: PropTypes.bool,
  file: PropTypes.string
};

export default inject('conflictsSession')(observer(Conflict));
