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

function renderCustomTooltip (item, model, chart, renderTooltipFn) {
  if (!model || !model.body || !renderTooltipFn) {
    return;
  }
  const tooltip = createUpdateTooltip(item, model, chart);
  if (!tooltip) {
    return;
  }
  const content = tooltip.querySelector('.chartjs-tooltip-content');
  content.innerHTML = `<div class="tooltip-container">
    ${renderTooltipFn(item, model, chart) || ''}
  </div>`;
  setTooltipStyles(tooltip, model, chart);
  setCaretStyles(tooltip, model);
}

function createUpdateTooltip (item, model, chart) {
  let tooltip = document.getElementById('chartjs-tooltip');
  if (!tooltip) {
    const content = document.createElement('div');
    const caret = document.createElement('div');
    tooltip = document.createElement('div');
    tooltip.id = 'chartjs-tooltip';
    content.classList.add('chartjs-tooltip-content');
    caret.classList.add('chartjs-tooltip-caret');
    tooltip.appendChild(content);
    tooltip.appendChild(caret);
    chart.canvas.parentNode.appendChild(tooltip);
  }
  if (model.opacity === 0 || !item) {
    tooltip.style.opacity = 0;
    return;
  }
  return tooltip;
};

function setTooltipStyles (tooltip, model, chart) {
  const tooltipContainer = tooltip.querySelector('.tooltip-container');
  const {xAlign, yAlign, caretY, caretX} = model;
  const {
    offsetLeft: positionX,
    offsetTop: positionY
  } = chart.canvas;
  const {height, width} = tooltip.getBoundingClientRect();
  const space = 10;
  let top = positionY + caretY - height;
  let left = positionX + caretX - width / 2;
  if (yAlign === 'top') {
    top += height + space;
  } else if (yAlign === 'center') {
    top += height / 2;
  } else if (yAlign === 'bottom') {
    top -= space;
  }
  if (xAlign === 'left') {
    left = left + (width / 2) - model.xPadding - (space / 2);
    if (yAlign === 'center') {
      left += space * 2;
    }
  } else if (xAlign === 'right') {
    left -= width / 2;
    if (yAlign === 'center') {
      left -= space;
    } else {
      left += space;
    }
  }
  tooltip.style.cssText = `
    opacity: 1;
    position: absolute;
    color: ${model.bodyFontColor};
    font-family: ${model._bodyFontFamily};
    font-style: ${model._bodyFontStyle};
    padding: ${model.yPadding}px ${model.xPadding}px;
    pointer-events: none;
    transition: all 0.3s ease;
    background-color: ${model.backgroundColor};
    left: ${left}px;
    top: ${top}px;
    will-change: left, top;
  `;
  tooltipContainer.style.cssText = `
    display: flex;
    flex-direction: column;
  `;
};

function setCaretStyles (tooltip, model) {
  const {xAlign, yAlign} = model;
  const {
    height: tooltipHeight,
    width: tooltipWidth
  } = tooltip.getBoundingClientRect();
  const caretSize = 7;
  const caret = tooltip.querySelector('.chartjs-tooltip-caret');
  if (!caret) {
    return;
  }
  caret.style.position = 'absolute';
  caret.style.display = 'block';
  caret.style.width = '12px';
  caret.style.height = '12px';
  caret.style.borderColor = 'transparent';
  caret.style.borderStyle = 'solid';
  caret.style.borderWidth = `${caretSize}px`;
  caret.style.top = 'unset';
  caret.style.right = 'unset';
  caret.style.bottom = 'unset';
  caret.style.left = 'unset';
  switch (`${yAlign}|${xAlign}`) {
    case 'center|left':
      caret.style.borderRightColor = model.backgroundColor;
      caret.style.top = `${(tooltipHeight / 2) - (caretSize)}px`;
      caret.style.left = `-${caretSize * 2}px`;
      break;
    case 'center|right':
      caret.style.borderLeftColor = model.backgroundColor;
      caret.style.top = `${(tooltipHeight / 2) - (caretSize)}px`;
      caret.style.right = `-${caretSize * 2}px`;
      break;
    case 'bottom|left':
      caret.style.borderTopColor = model.backgroundColor;
      caret.style.bottom = `-${caretSize * 2}px`;
      caret.style.left = `${caretSize}px`;
      break;
    case 'bottom|center':
      caret.style.borderTopColor = model.backgroundColor;
      caret.style.bottom = `-${caretSize * 2}px`;
      caret.style.left = `${tooltipWidth / 2 - caretSize}px`;
      break;
    case 'bottom|right':
      caret.style.borderTopColor = model.backgroundColor;
      caret.style.bottom = `-${caretSize * 2}px`;
      caret.style.left = `${tooltipWidth - caretSize * 2}px`;
      break;
  }
};

export default renderCustomTooltip;
