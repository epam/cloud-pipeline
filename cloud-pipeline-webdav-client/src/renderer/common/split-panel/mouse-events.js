import { MINIMUM_WIDTH } from './context';

/**
 * @typedef {object} ResizerOptions
 * @property {Node} resizerContainer
 * @property {string} dataPanelLeft
 * @property {string} dataPanelRight
 */

/**
 * @param event
 * @param resizeEventRef
 * @param {ResizerOptions} [options]
 */
function onMouseDownCallback(event, resizeEventRef, options) {
  const {
    resizerContainer: resizer,
    dataPanelLeft,
    dataPanelRight,
  } = options || {};
  if (resizer) {
    let previousPanel;
    let nextPanel;
    resizer.childNodes.forEach((child) => {
      if (
        child.dataset
        && child.dataset.panel
      ) {
        if (child.dataset.panel === dataPanelLeft) {
          previousPanel = child.clientWidth;
        } else if (child.dataset.panel === dataPanelRight) {
          nextPanel = child.clientWidth;
        }
      }
    });
    if (previousPanel && nextPanel) {
      event.stopPropagation();
      const {
        clientX,
      } = event;
      // eslint-disable-next-line no-param-reassign
      resizeEventRef.current = {
        start: clientX,
        previousPanel,
        nextPanel,
      };
    }
  }
}

function onMouseMoveCallback(event, resizeEventRef, onChange) {
  if (resizeEventRef.current) {
    event.stopPropagation();
    event.preventDefault();
    const {
      start,
      previousPanel,
      nextPanel,
    } = resizeEventRef.current;
    const maxDelta = Math.max(0, nextPanel - MINIMUM_WIDTH);
    const minDelta = Math.min(0, MINIMUM_WIDTH - previousPanel);
    const {
      clientX,
    } = event;
    const delta = Math.max(minDelta, Math.min(maxDelta, clientX - start));
    const newPreviousPanelSize = previousPanel + delta;
    const newNextPanelSize = nextPanel - delta;
    onChange(newPreviousPanelSize, newNextPanelSize);
  }
}

function onMouseUpCallback(event, resizeEventRef, onCommit) {
  if (resizeEventRef.current && event && /^mouseup$/i.test(event.type)) {
    onMouseMoveCallback(event, resizeEventRef, onCommit);
  }
  // eslint-disable-next-line no-param-reassign
  resizeEventRef.current = undefined;
}

export {
  onMouseDownCallback,
  onMouseUpCallback,
  onMouseMoveCallback,
};
