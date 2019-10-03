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
import {observer, Provider} from 'mobx-react';
import {computed, observable} from 'mobx';
import {PlotContext} from './utilities';
import Tooltip from './tooltip';
import ZoomArea from './zoom-area';

const ESCAPE_KEY = 27;
const ZOOM_EVENT = 'zoom';
const MOVE_EVENT = 'move';

@observer
class Plot extends React.Component {
  static propTypes = {
    identifier: PropTypes.string.isRequired,
    data: PropTypes.object,
    instanceFrom: PropTypes.number,
    width: PropTypes.number.isRequired,
    height: PropTypes.number.isRequired,
    padding: PropTypes.number,
    xAxis: PropTypes.string,
    onChangeRange: PropTypes.func,
    rangeChangeEnabled: PropTypes.bool
  };

  static defaultProps = {
    padding: 2,
    rangeChangeEnabled: true
  };

  svgElement;

  state = {

  };

  @observable plotContext = new PlotContext(this);

  @computed
  get width () {
    const {width, padding} = this.props;
    return width - 2 * padding;
  }

  @computed
  get height () {
    const {height, padding} = this.props;
    return height - 2 * padding;
  }

  componentDidMount () {
    this.plotContext.setData(this.props.data);
    window.addEventListener('mousemove', this.mouseMove);
    window.addEventListener('mouseup', this.mouseUp);
    window.addEventListener('keydown', this.keydown);
  }

  componentWillUnmount () {
    window.removeEventListener('mousemove', this.mouseMove);
    window.removeEventListener('mouseup', this.mouseUp);
    window.removeEventListener('keydown', this.keydown);
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.data !== this.props.data) {
      this.plotContext.setData(nextProps.data);
    }
  }

  setNewRange = (positionA, positionB, loadData) => {
    const start = Math.min(positionA, positionB);
    const end = Math.max(positionA, positionB);
    const {onChangeRange} = this.props;
    if (onChangeRange) {
      onChangeRange(start, end, loadData);
    }
  };

  registerSvgElement = (svgElement) => {
    this.svgElement = svgElement;
  };

  keydown = (event) => {
    switch (event.keyCode) {
      case ESCAPE_KEY: this.cancelEvents(); break;
      default: break;
    }
  };

  mouseDown = (event) => {
    const {rangeChangeEnabled} = this.props;
    if (!rangeChangeEnabled) {
      return;
    }
    if (event.shiftKey) {
      this.startEvent(ZOOM_EVENT, event);
    } else if (!event.shiftKey) {
      this.startEvent(MOVE_EVENT, event);
    }
  };

  startEvent = (eventName, event) => {
    if (this.svgElement) {
      const {xAxis} = this.props;
      const dim = this.svgElement.getBoundingClientRect();
      const x = event.clientX - dim.left;
      const axis = xAxis ? this.plotContext.getAxis(xAxis) : this.plotContext.xAxis;
      if (axis) {
        const start = axis.getPlotCoordinate(x);
        let eventProperties = this.state[eventName];
        if (!eventProperties) {
          eventProperties = {};
        }
        eventProperties.start = start;
        eventProperties.mousePosition = x;
        eventProperties.axisRange = {
          start: axis.start,
          end: axis.end,
          ratio: axis.canvasToPlotRatio
        };
        this.plotContext.hoveredItem = undefined;
        this.setState({[eventName]: eventProperties, event: eventName});
        event.preventDefault();
        event.stopPropagation();
      }
    }
  };

  continueEvent = (eventName, event, handler) => {
    if (this.svgElement) {
      const {xAxis} = this.props;
      const dim = this.svgElement.getBoundingClientRect();
      const x = event.clientX - dim.left;
      const axis = xAxis ? this.plotContext.getAxis(xAxis) : this.plotContext.xAxis;
      if (axis) {
        const position = axis.getPlotCoordinate(x);
        if (handler && handler(event, position, x)) {
          event.preventDefault();
          event.stopPropagation();
        }
      }
    }
  };

  continueZoom = (event, position, mousePosition, callback) => {
    const {zoom} = this.state;
    if (!zoom) {
      this.cancelZoom();
      return false;
    }
    zoom.end = position;
    this.plotContext.hoveredItem = undefined;
    this.setState({zoom}, callback);
    return true;
  };

  move = (mousePosition, moveInfo, loadData) => {
    const delta = (mousePosition - moveInfo.mousePosition) * moveInfo.axisRange.ratio;
    const range = moveInfo.axisRange.end - moveInfo.axisRange.start;
    const start = moveInfo.axisRange.start - delta;
    const end = start + range;
    this.setNewRange(start, end, loadData);
    return {start, end};
  };

  continueMove = (event, position, mousePosition, callback) => {
    const {move} = this.state;
    if (!move || move.start === undefined || !move.axisRange) {
      this.cancelMove();
      return false;
    }
    this.move(mousePosition, move, false);
    if (callback) {
      callback();
    }
    return true;
  };

  finishEvent = (eventName, event, handler) => {
    if (this.svgElement) {
      const {xAxis} = this.props;
      const dim = this.svgElement.getBoundingClientRect();
      const x = event.clientX - dim.left;
      const axis = xAxis ? this.plotContext.getAxis(xAxis) : this.plotContext.xAxis;
      if (axis) {
        const position = axis.getPlotCoordinate(x);
        if (handler && handler(event, position, x)) {
          event.preventDefault();
          event.stopPropagation();
        }
      }
    }
  };

  finishZoom = (event, position) => {
    const {zoom} = this.state;
    if (!zoom) {
      this.cancelZoom();
      return false;
    }
    this.setNewRange(zoom.start, position, true);
    this.cancelZoom();
    return true;
  };

  finishMove = (event, position, mousePosition) => {
    const {move} = this.state;
    if (!move || move.start === undefined || !move.axisRange) {
      this.cancelMove();
      return false;
    }
    this.move(mousePosition, move, true);
    this.cancelMove();
    return true;
  };

  onHover = (event) => {
    const {xAxis} = this.props;
    const dim = this.svgElement.getBoundingClientRect();
    let newHoverElement = null;
    if (
      event.clientX > dim.left &&
      event.clientX < dim.right &&
      event.clientY > dim.top &&
      event.clientY < dim.bottom
    ) {
      const x = event.clientX - dim.left;
      const y = event.clientY - dim.top;
      newHoverElement = this.plotContext.getNearestItem({x, y}, xAxis);
    }
    this.plotContext.hoveredItem = newHoverElement;
  };

  mouseMove = (event) => {
    const {event: eventName} = this.state;
    if (event && this.svgElement) {
      let handler;
      switch (eventName) {
        case ZOOM_EVENT: handler = this.continueZoom; break;
        case MOVE_EVENT: handler = this.continueMove; break;
        default:
          this.onHover(event);
          break;
      }
      this.continueEvent(eventName, event, handler);
    }
  };

  mouseUp = (event) => {
    const {event: eventName} = this.state;
    if (event && this.svgElement) {
      let handler;
      switch (eventName) {
        case ZOOM_EVENT: handler = this.finishZoom; break;
        case MOVE_EVENT: handler = this.finishMove; break;
        default: break;
      }
      this.finishEvent(eventName, event, handler);
    }
  };

  cancelZoom = () => {
    this.plotContext.hoveredItem = undefined;
    this.setState({zoom: undefined, event: undefined});
  };

  cancelMove = () => {
    this.plotContext.hoveredItem = undefined;
    this.setState({move: undefined, event: event});
  };

  cancelEvents = () => {
    this.plotContext.hoveredItem = undefined;
    this.setState({zoom: undefined, move: undefined, event: undefined});
  };

  render () {
    const {
      children,
      identifier,
      data,
      width,
      height,
      padding,
      xAxis
    } = this.props;
    const {zoom} = this.state;
    return (
      <Provider
        plotContext={this.plotContext}
        plotData={data.data}
      >
        <svg
          width={width - 2 * padding} height={height - 2 * padding}
          style={{margin: padding, fontSize: '8pt'}}
          shapeRendering={'crispEdges'}
          onMouseDown={this.mouseDown}
          ref={this.registerSvgElement}
        >
          <mask id={`plot-mask-${identifier}`}>
            <rect
              stroke={'transparent'}
              strokeWidth={0}
              fill={'white'}
              x={this.plotContext.left}
              y={this.plotContext.top}
              width={this.plotContext.width - this.plotContext.right - this.plotContext.left}
              height={this.plotContext.height - this.plotContext.bottom - this.plotContext.top}
            />
          </mask>
          {children}
          <Tooltip />
          <ZoomArea
            from={zoom?.start}
            to={zoom?.end}
            xAxis={xAxis}
          />
        </svg>
      </Provider>
    );
  }
}

export default Plot;
