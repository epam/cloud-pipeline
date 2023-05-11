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
import {
  computed
} from 'mobx';
import {
  inject,
  observer
} from 'mobx-react';
import {
  Button,
  Icon,
  Input,
  Modal
} from 'antd';
import ColorPicker from '../../../../../special/color-picker';
import AnnotationsTooltip from './annotations-tooltip';
import styles from './ome-tiff-annotations-renderer.css';

const MODES = {
  disabled: 'disabled',
  circle: 'circle',
  rectangle: 'rectangle',
  arrow: 'arrow',
  path: 'path',
  text: 'text'
};

const ACTIONS = {
  add: 'add',
  edit: 'edit',
  remove: 'remove'
};

@inject('annotations', 'hcsSourceState')
@observer
class OMETiffAnnotationsRenderer extends React.Component {
  state = {
    mode: MODES.disabled,
    color: '#FFFFFF',
    actions: [],
    annotations: [],
    position: 0,
    actionsPending: false,
    label: undefined
  };

  container;

  componentDidMount () {
    this.initializeListeners();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.annotations !== this.props.annotations) {
      this.initializeListeners(prevProps);
    }
  }

  componentWillUnmount () {
    this.removeListeners();
    this.removeMouseListeners();
    this.container = undefined;
  }

  initializeListeners = (prevProps) => {
    this.removeListeners(prevProps);
    const {annotations} = this.props;
    if (annotations) {
      annotations.onAnnotationChanged(this.onAnnotationChanged);
    }
  };

  removeListeners = (props = this.props) => {
    const {annotations} = props;
    if (annotations) {
      annotations.removeOnAnnotationChanged(this.onAnnotationChanged);
    }
  };

  @computed
  get selectedAnnotation () {
    const {
      annotations,
      hcsSourceState
    } = this.props;
    if (
      annotations &&
      hcsSourceState
    ) {
      return annotations.getAnnotationByIdentifier(hcsSourceState.selectedAnnotation);
    }
    return undefined;
  }

  get currentColor () {
    const {
      color
    } = this.state;
    const {selectedAnnotation} = this;
    if (selectedAnnotation) {
      switch (selectedAnnotation.type) {
        case 'text':
          return (selectedAnnotation.label ? selectedAnnotation.label.color : undefined) || color;
        default:
          return selectedAnnotation.lineColor || color;
      }
    }
    return color;
  }

  insertAction = (action) => {
    const {
      actions = [],
      position = 0
    } = this.state;
    this.setState({
      actions: [
        ...actions.slice(0, position),
        action
      ],
      position: position + 1
    });
  };

  onAnnotationChanged = (payload) => {
    const {
      before,
      after: annotation
    } = payload;
    this.insertAction({
      type: ACTIONS.edit,
      annotation,
      before
    });
  };

  initializeMouseListeners = () => {
    this.removeMouseListeners();
    if (!this.container) {
      return;
    }
    let eventData;
    let annotationId;
    const updateAnnotation = () => {
      const {
        annotations
      } = this.props;
      const points = eventData ? (eventData.points || []).filter(Boolean) : [];
      if (annotations && annotations.myAnnotations && points.length > 1) {
        const first = points[0];
        const last = points[points.length - 1];
        let updated;
        const {
          mode,
          color
        } = this.state;
        const xx = [first, last].map((point) => point.x);
        const yy = [first, last].map((point) => point.y);
        const x1 = Math.min(...xx);
        const x2 = Math.max(...xx);
        const y1 = Math.min(...yy);
        const y2 = Math.max(...yy);
        const getPoint = ({x, y}) => ([x, y]);
        switch (mode) {
          case MODES.circle:
            const radius = Math.min(x2 - x1, y2 - y1) / 2.0;
            if (radius > 0) {
              updated = {
                type: 'circle',
                center: [(x1 + x2) / 2.0, (y1 + y2) / 2.0],
                radius: Math.min(x2 - x1, y2 - y1) / 2.0,
                lineColor: color
              };
            }
            break;
          case MODES.rectangle:
            const width = x2 - x1;
            const height = y2 - y1;
            if (width > 0 && height > 0) {
              updated = {
                type: 'rectangle',
                center: [(x1 + x2) / 2.0, (y1 + y2) / 2.0],
                width: x2 - x1,
                height: y2 - y1,
                lineColor: color
              };
            }
            break;
          case MODES.path:
            updated = {
              type: 'polyline',
              lineColor: color,
              points: points.map(getPoint)
            };
            break;
          case MODES.arrow:
            const size = Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);
            if (size > 0) {
              updated = {
                type: 'arrow',
                lineColor: color,
                points: [
                  getPoint(first),
                  getPoint(last)
                ]
              };
            }
            break;
          default:
            break;
        }
        if (updated) {
          updated.identifier = annotationId;
          annotationId = annotations.myAnnotations.createOrUpdateAnnotation(updated, {save: false});
        }
      }
    };
    const getMousePosition = (event) => {
      const {
        annotations
      } = this.props;
      if (eventData && eventData.boundingRect && annotations) {
        return annotations.getImageCoordinates({
          x: event.clientX - eventData.boundingRect.left,
          y: event.clientY - eventData.boundingRect.top
        });
      }
      return undefined;
    };
    this.onMouseMove = (event) => {
      if (eventData) {
        eventData.points.push(getMousePosition(event));
        updateAnnotation();
      }
    };
    this.onMouseDown = (event) => {
      const {
        mode
      } = this.state;
      if (mode !== MODES.disabled && event.target === this.container) {
        switch (mode) {
          case MODES.text:
            eventData = {
              boundingRect: event.target.getBoundingClientRect(),
              points: []
            };
            const position = getMousePosition(event);
            eventData = undefined;
            this.setState({
              label: {
                center: position
              }
            });
            break;
          default:
            eventData = {
              boundingRect: event.target.getBoundingClientRect(),
              points: []
            };
            this.onMouseMove(event);
            break;
        }
      } else {
        eventData = undefined;
      }
    };
    this.onMouseUp = (event) => {
      this.onMouseMove(event);
      if (annotationId) {
        const {
          annotations
        } = this.props;
        if (annotations) {
          const annotation = annotations.getAnnotationByIdentifier(annotationId);
          if (annotation) {
            this.insertAction({
              annotation,
              type: ACTIONS.add
            });
          }
          (annotations.save)(false);
        }
      }
      eventData = undefined;
      annotationId = undefined;
      this.setState({mode: MODES.disabled});
    };
    window.addEventListener('mousemove', this.onMouseMove);
    window.addEventListener('mouseup', this.onMouseUp);
    this.container.addEventListener('mousedown', this.onMouseDown);
  };

  removeMouseListeners = () => {
    if (this.onMouseMove) {
      window.removeEventListener('mousemove', this.onMouseMove);
    }
    if (this.onMouseUp) {
      window.removeEventListener('mouseup', this.onMouseUp);
    }
    if (this.container && this.onMouseDown) {
      this.container.removeEventListener('mousedown', this.onMouseDown);
    }
  };

  onInitializeContainer = (ref) => {
    this.container = ref;
    this.initializeMouseListeners();
  };

  onChangeMode = (mode) => this.setState({
    mode
  });

  onToggleMode = (mode) => {
    const {mode: current} = this.state;
    if (current === mode) {
      this.onChangeMode(MODES.disabled);
    } else {
      this.onChangeMode(mode);
    }
  };

  onChangeColor = (color) => {
    this.setState({color}, () => {
      const {selectedAnnotation} = this;
      const {annotations} = this.props;
      if (annotations && selectedAnnotation && selectedAnnotation.editable) {
        const before = {
          ...selectedAnnotation
        };
        const after = {
          ...selectedAnnotation
        };
        switch (after.type) {
          case 'text':
            after.label = {
              ...(after.label || {}),
              color
            };
            break;
          default:
            after.lineColor = color;
            break;
        }
        annotations.createOrUpdateAnnotation(after, {save: true});
        this.insertAction({
          type: ACTIONS.edit,
          annotation: after,
          before
        });
      }
    });
  }

  undo = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const {
      actions = [],
      position = 0
    } = this.state;
    const {
      annotations
    } = this.props;
    if (annotations && position > 0) {
      this.setState({
        position: position - 1,
        actionsPending: true
      }, async () => {
        const action = actions[position - 1];
        if (action && action.annotation) {
          switch (action.type) {
            case ACTIONS.add:
              await annotations.removeAnnotation(action.annotation, {save: false});
              break;
            case ACTIONS.remove:
              action.annotation.identifier = await annotations
                .createOrUpdateAnnotation(action.annotation, {save: false});
              break;
            case ACTIONS.edit:
              action.annotation.identifier = await annotations
                .createOrUpdateAnnotation(action.before, {save: false});
              break;
          }
          await annotations.save(false);
          this.setState({actions, actionsPending: false});
        }
      });
    }
  };

  redo = async (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const {
      actions = [],
      position = 0
    } = this.state;
    const {
      annotations
    } = this.props;
    if (annotations && position < actions.length) {
      this.setState({
        position: position + 1,
        actionsPending: true
      }, async () => {
        const action = actions[position];
        if (action && action.annotation) {
          switch (action.type) {
            case ACTIONS.remove:
              await annotations.removeAnnotation(action.annotation, {save: false});
              break;
            case ACTIONS.add:
            case ACTIONS.edit:
              action.annotation.identifier = await annotations
                .createOrUpdateAnnotation(action.annotation, {save: false});
              break;
          }
          await annotations.save(false);
          this.setState({actions, actionsPending: false});
        }
      });
    }
  };

  removeSelected = async () => {
    const selected = this.selectedAnnotation;
    const {
      annotations
    } = this.props;
    if (
      annotations &&
      selected
    ) {
      this.setState({
        actionsPending: true
      }, async () => {
        await annotations.removeAnnotation(selected, {save: true});
        this.insertAction({
          annotation: selected,
          type: ACTIONS.remove
        });
        this.setState({actionsPending: false});
      });
    }
  };

  onChangeLabel = (event) => {
    const {
      label
    } = this.state;
    this.setState({
      label: {
        ...(label || {}),
        text: event.target.value
      }
    });
  };

  onCancelTextAnnotation = () => {
    this.setState({
      label: undefined
    });
  };

  onCreateTextAnnotation = () => {
    const {
      label,
      color
    } = this.state;
    const {
      annotations
    } = this.props;
    if (label && annotations && annotations.myAnnotations) {
      const {
        center = {},
        text
      } = label;
      const annotation = {
        type: 'text',
        center: [center.x, center.y],
        label: {
          value: text,
          fontSize: 16,
          color
        }
      };
      annotation.identifier = annotations.myAnnotations.createOrUpdateAnnotation(
        annotation,
        {save: true}
      );
      this.setState({
        label: undefined,
        mode: MODES.disabled
      }, () => this.insertAction({
        annotation,
        type: ACTIONS.add
      }));
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      mode: current,
      actions,
      position,
      actionsPending,
      label
    } = this.state;
    const toggleModeCallback = (mode) => (event) => {
      if (event) {
        event.stopPropagation();
        event.preventDefault();
      }
      this.onToggleMode(mode);
      if (event && event.target && typeof event.target.blur === 'function') {
        event.target.blur();
      }
    };
    const actionType = (mode) => (mode === current ? 'primary' : 'default');
    const stopPropagation = (event) => event.stopPropagation();
    return (
      <div
        className={
          classNames(
            className,
            styles.container,
            {
              [styles.inactive]: current === MODES.disabled,
              [styles.active]: current !== MODES.disabled
            }
          )
        }
        style={style}
        ref={this.onInitializeContainer}
      >
        <div
          className={styles.actions}
          onMouseDown={stopPropagation}
          onClick={stopPropagation}
        >
          <AnnotationsTooltip />
          <Button
            size="small"
            className={styles.action}
            onClick={toggleModeCallback(MODES.circle)}
            type={actionType(MODES.circle)}
          >
            <Icon type="plus-circle-o" />
          </Button>
          <Button
            size="small"
            className={styles.action}
            onClick={toggleModeCallback(MODES.rectangle)}
            type={actionType(MODES.rectangle)}
          >
            <Icon type="plus-square-o" />
          </Button>
          <Button
            size="small"
            className={styles.action}
            onClick={toggleModeCallback(MODES.arrow)}
            type={actionType(MODES.arrow)}
          >
            <Icon
              type="arrow-up"
              style={{
                transform: 'rotate(-45deg)'
              }}
            />
          </Button>
          <Button
            size="small"
            className={styles.action}
            onClick={toggleModeCallback(MODES.path)}
            type={actionType(MODES.path)}
          >
            <Icon
              type="edit"
            />
          </Button>
          <Button
            size="small"
            className={styles.action}
            onClick={toggleModeCallback(MODES.text)}
            type={actionType(MODES.text)}
          >
            A
          </Button>
          {
            <ColorPicker
              color={this.currentColor}
              onChange={this.onChangeColor}
              hex
              ignoreAlpha
            />
          }
          <Button
            className={styles.action}
            disabled={position === 0 || actionsPending}
            onClick={this.undo}
            size="small"
            style={{marginLeft: 10}}
          >
            Undo
          </Button>
          <Button
            className={styles.action}
            disabled={position >= actions.length || actionsPending}
            onClick={this.redo}
            size="small"
          >
            Redo
          </Button>
          {
            this.selectedAnnotation && this.selectedAnnotation.editable && (
              <Button
                size="small"
                className={styles.action}
                style={{marginLeft: 10}}
                type="danger"
                onClick={this.removeSelected}
              >
                Remove
              </Button>
            )
          }
          <Modal
            visible={!!label}
            title="Add text annotation"
            onCancel={this.onCancelTextAnnotation}
            footer={(
              <div
                className={styles.textAnnotationModalFooter}
              >
                <Button
                  onClick={this.onCancelTextAnnotation}
                >
                  CANCEL
                </Button>
                <Button
                  onClick={this.onCreateTextAnnotation}
                  disabled={!label || !label.text || label.text.trim().length === 0}
                  type="primary"
                >
                  ADD
                </Button>
              </div>
            )}
          >
            <Input
              autoFocus
              value={label ? label.text : undefined}
              onChange={this.onChangeLabel}
              style={{width: '100%'}}
            />
          </Modal>
        </div>
      </div>
    );
  }
}

OMETiffAnnotationsRenderer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default OMETiffAnnotationsRenderer;
