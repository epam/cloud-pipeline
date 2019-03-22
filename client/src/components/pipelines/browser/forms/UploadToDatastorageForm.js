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
import {Button, Checkbox, Input, InputNumber, Modal, Row, Select} from 'antd';
import BucketBrowser from '../../launch/dialogs/BucketBrowser';

export default class UploadToDatastorageForm extends React.Component {

  static propTypes = {
    visible: PropTypes.bool,
    onClose: PropTypes.func,
    onTransfer: PropTypes.func,
    fields: PropTypes.array,
    pathFields: PropTypes.array
  };

  state = {
    destination: null,
    nameField: null,
    pathFields: [],
    createFolders: false,
    bucketBrowserVisible: false,
    updatePathValues: true,
    threadsCount: null,
    threadsCountValid: true
  };

  openBucketBrowser = () => {
    this.setState({
      bucketBrowserVisible: true
    });
  };

  closeBucketBrowser = () => {
    this.setState({
      bucketBrowserVisible: false
    });
  };

  selectButcketPath = (path) => {
    this.setState({
      destination: path,
      bucketBrowserVisible: false
    });
  };

  onSelectNameField = (value) => {
    this.setState({
      nameField: value
    });
  };

  onSelectPathFields = (values) => {
    this.setState({
      pathFields: values
    });
  };

  onCreateFolderChanged = (e) => {
    this.setState({
      createFolders: e.target.checked
    });
  };

  onUpdatePathValuesChanged = (e) => {
    this.setState({
      updatePathValues: e.target.checked
    });
  };

  onTransfer = async () => {
    this.props.onTransfer &&
    await this.props.onTransfer({
      destination: this.state.destination,
      pathFields: this.state.pathFields,
      nameField: this.state.nameField,
      createFolders: this.state.createFolders,
      updatePathValues: this.state.updatePathValues,
      threadsCount: this.state.threadsCount
        ? this.state.threadsCount
        : undefined
    });
  };

  onChangeDestinationValue = (e) => {
    this.setState({
      destination: e.target.value
    });
  };

  onMaxThreadsCountChange = (value) => {
    const validateThreadsCount = (value) => {
      const threadsCount = value;
      if (!threadsCount) {
        return true;
      } else if (isNaN(threadsCount) || +threadsCount <= 0) {
        return false;
      }
      return true;
    };
    this.setState({
      threadsCount: value,
      threadsCountValid: validateThreadsCount(value)
    });
  };

  render () {
    return (
      <Modal
        title="Transfer to the Cloud"
        footer={
          <Row type="flex" align="middle" justify="end">
            <Button
              onClick={this.props.onClose}
              style={{marginRight: 5}}>
              Cancel
            </Button>
            <Button
              onClick={this.onTransfer}
              type="primary"
              disabled={!this.state.destination || this.state.pathFields.length === 0 || !this.state.threadsCountValid}
              style={{marginRight: 5}}>
              Start download
            </Button>
          </Row>
        }
        onCancel={this.props.onClose}
        visible={this.props.visible}>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <span style={{fontWeight: 'bold', marginRight: 5}}>
            Destination
            <span style={{color: 'red'}}>*</span>
            :
          </span>
          <div style={{flex: 1}}>
            <Input
              value={this.state.destination}
              onChange={this.onChangeDestinationValue}
              style={{width: '100%'}}
              addonAfter={
                <div style={{cursor: 'pointer'}} onClick={this.openBucketBrowser}>
                  Browse
                </div>
              } />
          </div>
        </Row>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <span style={{fontWeight: 'bold', marginRight: 5}}>
            Select path fields
            <span style={{color: 'red'}}>*</span>
            :
          </span>
          <div style={{flex: 1}}>
            <Select
              mode="multiple"
              disabled={this.props.pathFields.length === 1}
              style={{width: '100%'}}
              value={this.state.pathFields}
              onChange={this.onSelectPathFields}
              placeholder="Select fields">
              {
                this.props.pathFields.map(f => {
                  return (
                    <Select.Option key={f}>{f}</Select.Option>
                  );
                })
              }
            </Select>
          </div>
        </Row>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <span style={{fontWeight: 'bold', marginRight: 10}}>
            Select name field:
          </span>
          <div style={{flex: 1}}>
            <Select
              allowClear
              style={{width: '100%'}}
              onChange={this.onSelectNameField}
              placeholder="Select field">
              {
                this.props.fields.map(f => {
                  return (
                    <Select.Option key={f}>{f}</Select.Option>
                  );
                })
              }
            </Select>
          </div>
        </Row>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <span style={{fontWeight: 'bold', marginRight: 5}}>
            Max threads count:
          </span>
          <div style={{flex: 1}}>
            <InputNumber
              value={this.state.threadsCount}
              onChange={this.onMaxThreadsCountChange}
              style={
                this.state.threadsCountValid
                  ? {width: '100%'}
                  : {width: '100%', border: '1px solid red', color: 'red'}
              } />
          </div>
        </Row>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <Checkbox
            checked={this.state.createFolders}
            onChange={this.onCreateFolderChanged}>
            Create folders for each <i>path</i> field
          </Checkbox>
        </Row>
        <Row type="flex" align="middle" style={{marginTop: 5}}>
          <Checkbox
            checked={this.state.updatePathValues}
            onChange={this.onUpdatePathValuesChanged}>
            Update path values
          </Checkbox>
        </Row>
        <BucketBrowser
          onSelect={this.selectButcketPath}
          onCancel={this.closeBucketBrowser}
          visible={this.state.bucketBrowserVisible}
          path={this.state.destination}
          showOnlyFolder
          checkWritePermissions
          bucketTypes={['S3']} />
      </Modal>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (this.props.visible !== nextProps.visible && nextProps.visible) {
      const state = {
        pathFields: [],
        destination: null,
        updatePathValues: true,
        threadsCount: null,
        threadsCountValid: true
      };
      if (nextProps.pathFields.length === 1) {
        state.pathFields = nextProps.pathFields;
      }
      this.setState(state);
    }
  }
}
