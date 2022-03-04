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
import classNames from 'classnames';
import styles from '../../conflicts.css';
import caretStyles from './caret.css';

const MOVING_TIMEOUT = 100;

function correctLineOffset (text, offset) {
  const textLength = (text || '').length;
  return Math.min(offset || 0, textLength);
}

class Caret extends React.PureComponent {
  state = {
    moving: false
  };

  componentDidMount () {
    this.updateCaretPosition();
  }

  componentWillUnmount () {
    this.invalidateStopMovingTimeout();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.line !== prevProps.line ||
      this.props.offset !== prevProps.offset ||
      this.props.text !== prevProps.text
    ) {
      this.updateCaretPosition();
    }
  }

  getPosition () {
    const {
      characterSize,
      marginLeft,
      marginTop,
      line,
      lineHeight,
      offset,
      text
    } = this.props;
    const correctedOffset = correctLineOffset(text, offset);
    return {
      top: marginTop + (line - 1) * lineHeight,
      left: marginLeft + correctedOffset * characterSize - 1
    };
  }

  reportPositionChange = () => {
    const {onPositionChanged} = this.props;
    const {
      characterSize,
      line,
      offset,
      text,
      lineHeight
    } = this.props;
    const correctedOffset = correctLineOffset(text, offset);
    const px = this.getPosition();
    const pxTo = {...px};
    const pxFrom = {...px};
    pxTo.left += characterSize * 2;
    pxTo.top += lineHeight * 2;
    pxFrom.left -= characterSize * 2;
    pxFrom.top -= lineHeight * 2;
    const result = {
      pxTo,
      pxFrom,
      px,
      lineNumber: line,
      offset: correctedOffset
    };
    onPositionChanged && onPositionChanged(result);
  };

  invalidateStopMovingTimeout = () => {
    if (this.stopMovingTimeout) {
      clearTimeout(this.stopMovingTimeout);
      this.stopMovingTimeout = undefined;
    }
  };

  stopMoving = () => {
    this.invalidateStopMovingTimeout();
    this.setState({
      moving: false
    });
  };

  updateCaretPosition () {
    this.setState({
      moving: true
    }, () => {
      this.reportPositionChange();
      this.stopMovingTimeout = setTimeout(this.stopMoving, MOVING_TIMEOUT);
    });
  }

  render () {
    const {lineHeight} = this.props;
    const {moving} = this.state;
    return (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox={`0 0 2 ${lineHeight}`}
        className={
          classNames(
            'cp-text',
            styles.caret,
            caretStyles.caret,
            {
              [caretStyles.moving]: moving
            }
          )
        }
        height={lineHeight}
        width={2}
        style={this.getPosition()}
      >
        <rect
          x={0}
          y={0}
          width="100%"
          height="100%"
        />
      </svg>
    );
  }
}

Caret.propTypes = {
  line: PropTypes.number,
  lineHeight: PropTypes.number,
  offset: PropTypes.number,
  text: PropTypes.string,
  marginLeft: PropTypes.number,
  marginTop: PropTypes.number,
  characterSize: PropTypes.number,
  onPositionChanged: PropTypes.func
};

export default Caret;
