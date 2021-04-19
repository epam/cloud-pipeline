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
import {observer} from 'mobx-react';
import ResolveChanges from './controls/resolve-changes';
import ConflictedFile from './utilities/conflicted-file';
import Branches, {HeadBranch, RemoteBranch, Merged} from './utilities/conflicted-file/branches';
import analyzeConflicts from './utilities/analyze-conflicts';
import buildModifications from './utilities/changes/build';
import renderModifications from './utilities/modifications-canvas';
import BranchCode from './utilities/branch-code';
import StickyDiv from './utilities/sticky-div';
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
    gridTemplateColumn(HeadBranch),
    `[CANVAS-${HeadBranch}] ${CANVAS_WIDTH}px`,
    gridTemplateColumn(Merged),
    `[CANVAS-${RemoteBranch}] ${CANVAS_WIDTH}px`,
    gridTemplateColumn(RemoteBranch)
  ].join(' ');
};

class Conflict extends React.PureComponent {
  state = {
    error: undefined,
    analysis: undefined,
    modifications: [],
    contents: undefined,
    headRaw: undefined,
    remoteRaw: undefined,
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
  stickyAreas = {};
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
    if (
      prevProps.run !== this.props.run ||
      prevProps.storage !== this.props.storage ||
      prevProps.file !== this.props.file
    ) {
      this.updateFromProps();
    }
    this.draw();
  }

  updateFromProps = () => {
    const {
      file,
      storage,
      run
    } = this.props;
    if (storage && file && run) {
      analyzeConflicts(run, storage, file)
        .then((analysis) => {
          this.setState({
            error: undefined,
            analysis,
            modifications: buildModifications(analysis)
          }, this.draw);
        })
        .catch(e => {
          this.setState({
            error: e.message,
            analysis: undefined,
            modifications: []
          }, this.draw);
        });
    } else {
      this.setState({
        error: undefined,
        analysis: undefined,
        modifications: []
      }, this.draw);
    }
  };

  keyDown = (e) => {
    if (e && e.keyCode === 90) {
      this.undoLastChange(e);
    }
  };

  undoLastChange = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    const {analysis} = this.state;
    if (analysis && typeof analysis.undo === 'function') {
      analysis.undo(this.refresh);
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
    }
  };

  renderIDE = () => {
    const {
      analysis,
      error,
      ide = {}
    } = this.state;
    if (!error && analysis) {
      const branches = {
        [HeadBranch]: analysis.getLines(HeadBranch),
        [Merged]: analysis.getLines(Merged),
        [RemoteBranch]: analysis.getLines(RemoteBranch)
      };
      const rawBranches = {
        [HeadBranch]: analysis.getLines(HeadBranch, new Set([])),
        [Merged]: analysis.getLines(Merged, new Set([])),
        [RemoteBranch]: analysis.getLines(RemoteBranch, new Set([]))
      };
      const attachScrollArea = (node, name) => {
        this.scrolls[name] = node;
      };
      const attachStickyArea = (node, branch) => {
        if (!this.stickyAreas[branch]) {
          this.stickyAreas[branch] = [];
        }
        if (this.stickyAreas[branch].indexOf(node) === -1 && node) {
          this.stickyAreas[branch].push(node);
        }
        if (node) {
          node.processPosition();
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
      const onScroll = (e, branch) => {
        const {target} = e;
        const stickyAreas = this.stickyAreas[branch];
        if (stickyAreas) {
          stickyAreas.forEach(area => area.processPosition(target.scrollLeft));
        }
        if (!this.blockScroll || this.blockScroll === branch) {
          const {
            scrollLeft,
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
                  const otherBranchStickyAreas = this.stickyAreas[otherBranch];
                  if (otherBranch !== branch) {
                    const oIdxStart = branches[otherBranch].indexOf(commonParent);
                    const oIdxEnd = branches[otherBranch].indexOf(commonChild);
                    const oRange = Math.max(1, oIdxEnd - oIdxStart);
                    const oLocalOffset = oRange * localRatio;
                    const oLocalCenter = oIdxStart + oLocalOffset;
                    if (scroll) {
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
                      scroll.scrollLeft = scrollLeft;
                      if (otherBranchStickyAreas) {
                        otherBranchStickyAreas.forEach(area => area.processPosition(scrollLeft));
                      }
                    }
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
      const renderBranchCodeLineNumbers = (branch, modificationsBranch, rtl = false) => (
        <BranchCode.LineNumbers
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
      const renderBranch = (branch) => {
        let left, right;
        switch (branch) {
          case HeadBranch:
            right = (
              <StickyDiv
                className={
                  classNames(
                    styles.stickyPanel,
                    styles.right
                  )
                }
                onInitialized={node => attachStickyArea(node, branch)}
                placement={StickyDiv.Placement.right}
                to={this.scrolls[branch]}
              >
                {renderBranchCodeLineNumbers(branch)}
              </StickyDiv>
            );
            break;
          case RemoteBranch:
            left = (
              <StickyDiv
                className={
                  classNames(
                    styles.stickyPanel,
                    styles.left
                  )
                }
                onInitialized={node => attachStickyArea(node, branch)}
                placement={StickyDiv.Placement.left}
                to={this.scrolls[branch]}
              >
                {renderBranchCodeLineNumbers(branch, undefined, true)}
              </StickyDiv>
            );
            break;
          case Merged:
            right = (
              <StickyDiv
                className={
                  classNames(
                    styles.stickyPanel,
                    styles.right
                  )
                }
                onInitialized={node => attachStickyArea(node, branch)}
                placement={StickyDiv.Placement.right}
                to={this.scrolls[branch]}
              >
                {renderBranchCodeLineNumbers(branch, RemoteBranch, true)}
              </StickyDiv>
            );
            left = (
              <StickyDiv
                className={
                  classNames(
                    styles.stickyPanel,
                    styles.left
                  )
                }
                onInitialized={node => attachStickyArea(node, branch)}
                placement={StickyDiv.Placement.left}
                to={this.scrolls[branch]}
              >
                {renderBranchCodeLineNumbers(branch, HeadBranch)}
              </StickyDiv>
            );
            break;
        }
        return (
          <div
            className={styles.panel}
            key={branch}
            ref={node => attachScrollArea(node, branch)}
            onScroll={e => onScroll(e, branch)}
            style={{
              gridColumn: branch
            }}
          >
            {left}
            <BranchCode
              branch={branch}
              lines={rawBranches[branch]}
              lineHeight={LINE_HEIGHT}
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
      return [
        renderBranch(HeadBranch),
        renderCanvas(HeadBranch),
        renderBranch(Merged),
        renderCanvas(RemoteBranch),
        renderBranch(RemoteBranch)
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

  draw = () => {
    const {analysis, modifications} = this.state;
    renderModifications(
      this.canvases[HeadBranch],
      analysis,
      modifications,
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
      analysis,
      modifications,
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
      analysis,
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
          conflictedFile={analysis}
        />
        <div
          data-conflicted-file-changes-hash={analysis ? analysis.changesHash : 0}
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
  file: PropTypes.string,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object
};

export default observer(Conflict);
