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

const ESCAPE_KEY = 27;

export default function (controller, area, onMoveFinished) {
  if (!area) {
    return () => {};
  }
  const mouseDown = (event) => {
    if (event.shiftKey || event.button !== 0) {
      return;
    }
    const {from} = controller.state;
    const dim = area.getBoundingClientRect();
    const x = event.clientX - dim.left;
    let eventProperties = controller.state.move;
    if (!eventProperties) {
      eventProperties = {};
    }
    eventProperties.mousePosition = x;
    eventProperties.config = {
      from,
      range: controller.dataRange,
      ratio: controller.canvasToPlotRatio
    };
    controller.setState({move: eventProperties});
    event.preventDefault();
    event.stopPropagation();
  };

  const mouseMove = (event) => {
    const {move: moveState} = controller.state;
    if (moveState) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      move(x, moveState, false);
      event.preventDefault();
      event.stopPropagation();
    }
  };

  const mouseUp = (event) => {
    const {move: moveState} = controller.state;
    if (moveState) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      move(x, moveState, true);
      event.preventDefault();
      event.stopPropagation();
    }
    cancelMove();
  };

  const keydown = (event) => {
    switch (event.keyCode) {
      case ESCAPE_KEY: cancelMove(); break;
      default: break;
    }
  };

  const move = (mousePosition, moveInfo, final) => {
    const {from} = controller.state;
    if (!from) {
      return;
    }
    const {minimum, maximum} = controller.props;
    const delta = (mousePosition - moveInfo.mousePosition) * moveInfo.config.ratio;
    let start = Math.max(minimum || -Infinity, moveInfo.config.from - delta);
    const end = Math.min(maximum || Infinity, start + moveInfo.config.range);
    start = Math.max(minimum || -Infinity, end - moveInfo.config.range);
    controller.setState({
      from: start,
      to: end
    }, () => {
      if (onMoveFinished) {
        onMoveFinished(start, end, final);
      }
    });
  };

  const cancelMove = () => {
    controller.setState({move: undefined});
  };

  area.addEventListener('mousedown', mouseDown);
  window.addEventListener('mousemove', mouseMove);
  window.addEventListener('mouseup', mouseUp);
  window.addEventListener('keydown', keydown);
  return () => {
    area.removeEventListener('mouseDown', mouseDown);
    window.removeEventListener('mousemove', mouseMove);
    window.removeEventListener('mouseup', mouseUp);
    window.removeEventListener('keydown', keydown);
  };
}
