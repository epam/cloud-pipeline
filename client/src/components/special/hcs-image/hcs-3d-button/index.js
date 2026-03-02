/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  inject,
  observer} from 'mobx-react';
import {computed, isObservableArray, makeObservable} from 'mobx';
import {Slider,
  Select,
  Checkbox,
  Popover,
  Button
} from 'antd';
import {DownOutlined} from '@ant-design/icons';
import displaySize from '../../../../utils/displaySize';
import styles from './hcs-3d-button.css';
import classNames from 'classnames';

function getSliceRangeSafe (range) {
  if (!range || typeof range !== 'object' || !(Array.isArray(range) || isObservableArray(range))) {
    return [0, 100];
  }
  const [min = 0, max = 100] = range;
  if (min >= max) {
    return [0, 100];
  }
  return [min, max];
}

function getSliceEnabled (range) {
  if (!range || typeof range !== 'object' || !(Array.isArray(range) || isObservableArray(range)) || range.length !== 2) {
    return false;
  }
  const [min = 0, max = 100] = range;
  return min < max;
}

@inject('hcsViewerState')
@observer
export default class HCS3DButton extends React.Component {
  state = {
    modalVisible: false
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      use3dMode: computed,
      downsamplingMode: computed,
      renderingMode: computed,
      xSlice: computed,
      xSliceRange: computed,
      xSliceEnabled: computed,
      ySlice: computed,
      ySliceRange: computed,
      ySliceEnabled: computed,
      zSlice: computed,
      zSliceRange: computed,
      zSliceEnabled: computed
    });
  }

  get use3dMode () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.use3D;
  }

  get downsamplingMode () {
    const {hcsViewerState} = this.props;
    const {downsamplingMode} = hcsViewerState ?? {};
    return downsamplingMode === undefined ? undefined : `${downsamplingMode}`;
  }

  get renderingMode () {
    const {hcsViewerState} = this.props;
    const {renderingMode} = hcsViewerState ?? {};
    return renderingMode === undefined ? undefined : `${renderingMode}`;
  }

  get xSlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.xSlice || [];
  }

  get xSliceRange () {
    const {hcsViewerState} = this.props;
    return getSliceRangeSafe(hcsViewerState?.xSliceRange);
  }

  get xSliceEnabled () {
    const {hcsViewerState} = this.props;
    return getSliceEnabled(hcsViewerState?.xSlice);
  }

  get ySlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.ySlice || [];
  }

  get ySliceRange () {
    const {hcsViewerState} = this.props;
    return getSliceRangeSafe(hcsViewerState?.ySliceRange);
  }

  get ySliceEnabled () {
    const {hcsViewerState} = this.props;
    return getSliceEnabled(hcsViewerState?.ySlice);
  }

  get zSlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.zSlice || [];
  }

  get zSliceRange () {
    const {hcsViewerState} = this.props;
    return getSliceRangeSafe(hcsViewerState?.zSliceRange);
  }

  get zSliceEnabled () {
    const {hcsViewerState} = this.props;
    return getSliceEnabled(hcsViewerState?.zSlice);
  }

  onChangeDownsampleMode = (key) => {
    const {hcsViewerState} = this.props;
    if (hcsViewerState?.changeDownsamplingMode) {
      hcsViewerState.changeDownsamplingMode(Number.isNaN(Number(key)) ? undefined : Number(key));
    }
  };

  onChangeRenderingMode = (key) => {
    const {hcsViewerState} = this.props;
    if (hcsViewerState?.changeRenderingMode) {
      hcsViewerState.changeRenderingMode(Number.isNaN(Number(key)) ? undefined : Number(key));
    }
  };

  toggle3DMode = () => {
    const {hcsViewerState} = this.props;
    if (hcsViewerState?.change3dMode) {
      hcsViewerState.change3dMode(!this.use3dMode);
    }
  };

  visibilityChanged = visible => visible ? this.openModal() : this.closeModal();
  openModal = () => this.setState({modalVisible: true});
  closeModal = () => this.setState({modalVisible: false});

  renderDropdownContent = () => {
    const {hcsViewerState} = this.props;
    const downsamplingModes = hcsViewerState?.downsamplingModes || [];
    const renderingModes = hcsViewerState?.renderingModes || [];
    const sliceControls = [{
      min: this.xSliceRange[0],
      max: this.xSliceRange[1],
      title: 'X slice',
      onChange: hcsViewerState?.changeXSlice,
      enabled: this.xSliceEnabled && this.use3dMode,
      value: [...this.xSlice]
    }, {
      min: this.ySliceRange[0],
      max: this.ySliceRange[1],
      title: 'Y slice',
      onChange: hcsViewerState?.changeYSlice,
      enabled: this.ySliceEnabled && this.use3dMode,
      value: [...this.ySlice]
    }, {
      min: this.zSliceRange[0],
      max: this.zSliceRange[1],
      title: 'Z slice',
      onChange: hcsViewerState?.changeZSlice,
      enabled: this.zSliceEnabled && this.use3dMode,
      value: [...this.zSlice]
    }];
    const TitleWrapper = ({title, children}) => (
      <div className={styles.selectorWrapper}>
        <span className={styles.title} style={{minWidth: 130}}>{title}</span>
        <div className={styles.content}>{children}</div>
      </div>
    );
    return (
      <div className={styles.overlayContainer}>
        <b>Volume rendering settings:</b>
        <Checkbox
          checked={this.use3dMode}
          onChange={this.toggle3DMode}
          className={styles.title}
        >
          Use volume renderer
        </Checkbox>
        <TitleWrapper title="Downsampling mode:">
          <Select
            style={{width: '100%'}}
            value={this.downsamplingMode}
            onChange={this.onChangeDownsampleMode}
            getPopupContainer={triggerNode => triggerNode.parentNode}
          >
            {downsamplingModes.map(mode => (
              <Select.Option key={mode.id} value={`${mode.id}`}>
                {`${mode.name} (${displaySize(mode.bytes)} per channel)`}
              </Select.Option>
            ))}
          </Select>
        </TitleWrapper>
        <TitleWrapper title="Rendering mode:">
          <Select
            value={this.renderingMode}
            onChange={this.onChangeRenderingMode}
            style={{width: '100%'}}
            getPopupContainer={triggerNode => triggerNode.parentNode}
          >
            {renderingModes.map(mode => (
              <Select.Option key={mode.id} value={`${mode.id}`}>
                {mode.name}
              </Select.Option>
            ))}
          </Select>
        </TitleWrapper>
        {sliceControls.map(({min, max, title, value, onChange, enabled}) => (
          <div key={title} className={styles.sliceWrapper}>
            <span>{title}</span>
            <Slider
              style={{margin: '2px 6px', flex: 1}}
              disabled={!enabled}
              value={value}
              min={min}
              max={max}
              onChange={onChange}
              range
            />
          </div>
        ))}
      </div>
    );
  };

  render () {
    const {size, className} = this.props;
    const {modalVisible} = this.state;
    return (
      <div className={classNames(className, styles.volumetricButton)}>
        <Button
          size={size}
          onClick={this.toggle3DMode}
          type={this.use3dMode ? 'primary' : 'default'}
          style={{
            borderBottomRightRadius: '0px',
            borderTopRightRadius: '0px',
            borderBottomLeftRadius: '4px',
            borderTopLeftRadius: '4px'
          }}
        >
          3D
        </Button>
        <Popover
          getPopupContainer={triggerNode => triggerNode.parentNode}
          onVisibleChange={this.visibilityChanged}
          visible={modalVisible}
          trigger="click"
          title={false}
          content={this.renderDropdownContent()}
          placement="bottom"
          overlayStyle={{
            width: '35vw',
            minWidth: 350
          }}
          overlayClassName={styles.modalOverlay}
          maskClosable={false}
        >
          <Button size={size} style={{
            borderBottomRightRadius: '4px',
            borderTopRightRadius: '4px',
            borderBottomLeftRadius: '0px',
            borderTopLeftRadius: '0px'
          }}>
            <DownOutlined />
          </Button>
        </Popover>
      </div>
    );
  }
}

HCS3DButton.propTypes = {
  size: PropTypes.string,
  viewer: PropTypes.object
};
