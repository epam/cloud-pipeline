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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Slider, Select, Checkbox, Popover, Button, Icon} from 'antd';
import displaySize from '../../../../utils/displaySize';
import styles from './hcs-3d-button.css';

@inject('hcsViewerState')
@observer
export default class HCS3DButton extends React.Component {
  state = {
    modalVisible: false
  };

  @computed
  get use3dMode () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.use3D;
  }

  @computed
  get downsamplingMode () {
    const {hcsViewerState} = this.props;
    return '' + hcsViewerState?.downsamplingMode;
  }

  @computed
  get renderingMode () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.renderingMode;
  }

  @computed
  get xSlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.xSlice || [];
  }

  @computed
  get xSliceEnabled () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.xSliceEnabled;
  }

  @computed
  get ySlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.ySlice || [];
  }

  @computed
  get ySliceEnabled () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.ySliceEnabled;
  }

  @computed
  get zSlice () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.zSlice || [];
  }

  @computed
  get zSliceEnabled () {
    const {hcsViewerState} = this.props;
    return hcsViewerState?.zSliceEnabled;
  }

  onChangeDownsampleMode = (key) => {
    const {hcsViewerState} = this.props;
    if (hcsViewerState?.changeDownsamplingMode) {
      hcsViewerState.changeDownsamplingMode(key);
    }
  };

  onChangeRenderingMode = (key) => {
    const {hcsViewerState} = this.props;
    if (hcsViewerState?.changeDownsamplingMode) {
      hcsViewerState.changeRenderingMode(key);
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
    const downsamplingModesMock = [
      {id: 1, name: 'mode 1', size_bytes: 1},
      {id: 2, name: 'mode 2', size_bytes: 100},
      {id: 3, name: 'mode 3', size_bytes: 1101010},
      {id: 4, name: 'mode 4', size_bytes: 98883838}
    ];
    const renderingModesMock = [
      {id: 1, name: 'Maximum intensity projection'},
      {id: 2, name: 'Minimum intensity projection'},
      {id: 3, name: 'Normal intensity projection'}
    ];
    const sliceControls = [{
      min: 0,
      max: 100,
      title: 'X slice',
      onChange: hcsViewerState?.changeXSlice,
      onChangeEnabled: hcsViewerState?.changeXSliceEnabled,
      enabled: this.xSliceEnabled,
      value: [...this.xSlice]
    }, {
      min: 0,
      max: 100,
      title: 'Y slice',
      onChange: hcsViewerState?.changeYSlice,
      onChangeEnabled: hcsViewerState?.changeYSliceEnabled,
      enabled: this.ySliceEnabled,
      value: [...this.ySlice]
    }, {
      min: 0,
      max: 100,
      title: 'Z slice',
      onChange: hcsViewerState?.changeZSlice,
      onChangeEnabled: hcsViewerState?.changeZSliceEnabled,
      enabled: this.zSliceEnabled,
      value: [...this.zSlice]
    }];
    const TitleWrapper = ({title, children}) => (
      <div className={styles.selectorWrapper}>
        <span className={styles.title} style={{minWidth: 130}}>{title}</span>
        {children}
      </div>
    );
    return (
      <div className={styles.overlayContainer}>
        <b>3D settings:</b>
        <Checkbox
          checked={this.use3dMode}
          onChange={this.toggle3DMode}
          className={styles.title}
        >
          Use 3D view
        </Checkbox>
        <TitleWrapper title="Downsampling mode:">
          <Select
            value={`${this.downsamplingMode}`}
            style={{flex: 1}}
            onChange={this.onChangeDownsampleMode}
            getPopupContainer={triggerNode => triggerNode.parentNode}
          >
            {downsamplingModesMock.map(mode => (
              <Select.Option key={mode.id} value={`${mode.id}`}>
                {`${mode.name} (${displaySize(mode.size_bytes)})`}
              </Select.Option>
            ))}
          </Select>
        </TitleWrapper>
        <TitleWrapper title="Rendering mode:">
          <Select
            value={`${this.renderingMode}`}
            onChange={this.onChangeRenderingMode}
            style={{flex: 1}}
            getPopupContainer={triggerNode => triggerNode.parentNode}
          >
            {renderingModesMock.map(mode => (
              <Select.Option key={mode.id} value={`${mode.id}`}>
                {mode.name}
              </Select.Option>
            ))}
          </Select>
        </TitleWrapper>
        {sliceControls.map(({min, max, title, value, onChange, enabled, onChangeEnabled}) => (
          <div key={title} className={styles.sliceWrapper}>
            <Checkbox
              className={styles.title}
              checked={enabled}
              onChange={e => onChangeEnabled(e.target.checked)}
            >
              {title}
            </Checkbox>
            <Slider
              style={{margin: '2px 6px'}}
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
      <Button.Group className={className}>
        <Button
          size={size}
          onClick={this.toggle3DMode}
          type={this.use3dMode ? 'primary' : 'default'}
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
            borderTopRightRadius: '4px'
          }}>
            <Icon type="down" />
          </Button>
        </Popover>
      </Button.Group>
    );
  }
}

HCS3DButton.PropTypes = {
  size: PropTypes.string,
  viewer: PropTypes.object
};
