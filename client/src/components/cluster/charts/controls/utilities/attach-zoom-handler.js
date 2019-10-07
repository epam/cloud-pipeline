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

export default function (controller, area, onZoomFinished) {
  if (!area) {
    return () => {};
  }
  const mouseDown = (event) => {
    if (!event.shiftKey || event.button !== 0) {
      return;
    }
    const dim = area.getBoundingClientRect();
    const x = event.clientX - dim.left;
    const start = controller.getPlotCoordinate(x);
    let eventProperties = controller.state.zoom;
    if (!eventProperties) {
      eventProperties = {};
    }
    eventProperties.start = start;
    controller.setState({zoom: eventProperties});
    event.preventDefault();
    event.stopPropagation();
  };

  const mouseMove = (event) => {
    const {zoom: zoomState} = controller.state;
    if (zoomState) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      zoomState.end = controller.getPlotCoordinate(x);
      controller.setState({zoom: zoomState});
      event.preventDefault();
      event.stopPropagation();
    }
  };

  const mouseUp = (event) => {
    const {zoom: zoomState} = controller.state;
    if (zoomState) {
      const dim = area.getBoundingClientRect();
      const x = event.clientX - dim.left;
      zoomState.end = controller.getPlotCoordinate(x);
      controller.setState({zoom: zoomState});
      event.preventDefault();
      event.stopPropagation();
      zoom(zoomState);
    } else {
      cancelZoom();
    }
  };

  const keydown = (event) => {
    switch (event.keyCode) {
      case ESCAPE_KEY: cancelZoom(); break;
      default: break;
    }
  };

  const zoom = ({start, end}) => {
    console.log('zoom', start, end);
    controller.setState({
      from: Math.min(start, end),
      to: Math.max(start, end)
    }, () => {
      cancelZoom();
      if (onZoomFinished) {
        onZoomFinished(start, end);
      }
    });
  };

  const cancelZoom = () => {
    controller.setState({zoom: undefined});
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
