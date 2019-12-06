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
    let {move} = controller.state;
    if (!move) {
      move = {};
    }
    move.mousePosition = x;
    move.moved = false;
    move.config = {
      from,
      range: controller.dataRange,
      ratio: controller.canvasToPlotRatio
    };
    controller.setState({move});
    event.preventDefault();
    event.stopPropagation();
  };

  const mouseMove = (event) => {
    const {move} = controller.state;
    if (move) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      move.moved = move.moved || Math.abs(move.mousePosition - x) > 1;
      performMove(x, move, false);
      event.preventDefault();
      event.stopPropagation();
    }
  };

  const mouseUp = (event) => {
    const {move} = controller.state;
    if (move) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      performMove(x, move, true);
      event.preventDefault();
      event.stopPropagation();
    }
    cancelMove();
  };

  const keydown = (event) => {
    switch (event.keyCode) {
      case ESCAPE_KEY: cancelMove(true); break;
      default: break;
    }
  };

  const performMove = (mousePosition, move, final) => {
    const {from} = controller.state;
    if (!from) {
      return;
    }
    const {minimum, maximum} = controller.props;
    const delta = (mousePosition - move.mousePosition) * move.config.ratio;
    let start = Math.max(minimum || -Infinity, move.config.from - delta);
    const end = Math.min(maximum || Infinity, start + move.config.range);
    start = Math.max(minimum || -Infinity, end - move.config.range);
    controller.setState({
      from: start,
      to: end,
      move
    }, () => {
      if (onMoveFinished && move.moved) {
        onMoveFinished(start, end, final);
      }
    });
  };

  const cancelMove = (revert = false) => {
    let {move} = controller.state;
    if (revert && move) {
      const newState = {
        move: undefined
      };
      newState.from = move.config.from;
      newState.end = move.config.from + move.config.range;
      controller.setState(newState, () => {
        if (onMoveFinished) {
          onMoveFinished(newState.from, newState.end, true);
        }
      });
    } else {
      controller.setState({move: undefined});
    }
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
