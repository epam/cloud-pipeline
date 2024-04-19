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
import {computed} from 'mobx';
import {
  Row,
  Upload,
  Button,
  Icon,
  Modal,
  Form,
  Select,
  Input,
  Progress,
  Col,
  Tooltip,
  message
} from 'antd';
import dataStorageRestrictedAccessCheck from '../../utils/data-storage-restricted-access';
import BucketBrowser from '../pipelines/launch/dialogs/BucketBrowser';
import OmicsStorage, {
  MIN_PART_SIZE,
  MAX_FILE_SIZE,
  MAX_FILE_SIZE_DESCRIPTION,
  MIN_FILE_SIZE_DESCRIPTION
} from '../../models/omics-upload/omics-storage';
import styles from './UploadOmicsButton.css';

const Fields = {
  file: 'file',
  reference: 'reference',
  name: 'name',
  description: 'description',
  sampleId: 'sampleId',
  subjectId: 'subjectId',
  generatedFrom: 'generatedFrom'
};

const FieldLabel = {
  [Fields.file]: 'File',
  [Fields.reference]: 'Reference from ref storage',
  [Fields.name]: 'Name',
  [Fields.description]: 'Description',
  [Fields.sampleId]: 'Sample id',
  [Fields.subjectId]: 'Subject id',
  [Fields.generatedFrom]: 'Generated from'
};

const FieldValid = {
  [Fields.file]: 'fileValid',
  [Fields.subjectId]: 'subjectIdValid',
  [Fields.sampleId]: 'sampleIdValid',
  [Fields.name]: 'nameValid',
  [Fields.reference]: 'referenceValid'
};

const CommonRequiredFields = [
  Fields.file,
  Fields.subjectId,
  Fields.sampleId,
  Fields.name
];

const FileTypes = {
  BAM: 'BAM',
  CRAM: 'CRAM',
  UBAM: 'UBAM',
  FASTQ: 'FASTQ'
};

@Form.create()
export class UploadOmicsButton extends React.Component {

  static propTypes = {
    storageInfo: PropTypes.object,
    region: PropTypes.string,
    onRefresh: PropTypes.func
  };

  state = {
    uploadMenuVisible: false,
    uploadMenuClosable: true,
    uploadingFile: null,
    restrictedAccess: true,
    restrictedAccessCheckInProgress: false,
    fileValid: false,
    subjectIdValid: false,
    sampleIdValid: false,
    nameValid: false,
    referenceValid: false,
    uploading: false
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  get fileTypes () {
    return Object.values(FileTypes || {}) || [];
  }

  get isReferenceRequired () {
    return this.state.file === FileTypes.BAM || this.state.file === FileTypes.CRAM;
  }

  get requiredFields () {
    if (this.isReferenceRequired) {
      return [...CommonRequiredFields, Fields.reference];
    }
    return CommonRequiredFields;
  }

  get isUploadButtonDisabled () {
    return this.requiredFields.some(field => {
      return !this.state[FieldValid[field]];
    });
  }

  @computed
  get clientError () {
    return (this.omicsStorage || {}).uploadError;
  }

  @computed
  get uploadPercentage () {
    return (this.omicsStorage || {}).uploadPercentage;
  }

  componentDidMount () {
    this.checkRestrictedAccess();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const storageChanged = (a, b) => {
      const {
        id: aID
      } = a || {};
      const {
        id: bID
      } = b || {};
      return aID !== bID;
    };
    if (storageChanged(this.props.storageInfo, prevProps.storageInfo)) {
      this.checkRestrictedAccess();
    }
  }

  showUploadMenu = (file) => {
    this.setState({
      uploadMenuVisible: true,
      uploadingFile: file
    });
  };

  hideUploadMenu = () => {
    if (!this.state.uploadMenuClosable) {
      return;
    }
    this.props.form.resetFields();
    this.setState({
      uploadMenuVisible: false,
      uploadMenuClosable: true,
      uploadingFiles: [],
      fileValid: false,
      subjectIdValid: false,
      sampleIdValid: false,
      nameValid: false,
      referenceValid: false,
      uploading: false
    }, async () => {
      if (this.props.onRefresh) {
        this.props.onRefresh(true);
      }
    });
  };

  onUploadStatusChanged = (info) => {
    const {file} = info;
    if (file) {
      this.showUploadMenu(file);
    }
  };

  handleSubmit = (e) => {
    e && e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.setState({
          uploading: true,
          uploadMenuClosable: false
        }, () => this.startUpload(values));
      }
    });
  };

  checkFile = () => {
    const {size: fileSize} = this.state.uploadingFile;
    if (fileSize < MIN_PART_SIZE) {
      message.error(`Minimum file size is ${MIN_FILE_SIZE_DESCRIPTION}`, 5);
      return false;
    }
    if (fileSize > MAX_FILE_SIZE) {
      message.error(`Maximum file size is ${MAX_FILE_SIZE_DESCRIPTION}`, 5);
      return false;
    }
    return true;
  }

  startUpload = async (values) => {
    const fileValid = this.checkFile();
    if (!fileValid) {
      this.setState({
        uploading: false,
        uploadMenuClosable: true
      });
      return;
    }
    const clientCreated = await this.createOmicClient();
    if (clientCreated) {
      const uploadCreated = await this.createUpload(values);
      if (uploadCreated) {
        await this.uploadFile();
      }
    }
    this.setState({
      uploading: false,
      uploadMenuClosable: true
    }, () => {
      this.hideUploadMenu();
    });
  }

  createOmicClient = async () => {
    const config = {
      region: this.props.region,
      storage: this.props.storageInfo
    };
    try {
      this.omicsStorage = new OmicsStorage(config);
      return await this.omicsStorage.createClient();
    } catch (err) {
      return false;
    }
  }

  createUpload = async (values) => {
    const params = {
      name: values.name,
      sampleId: values.sampleId,
      sequenceStoreId: this.props.storageInfo.cloudStorageId,
      sourceFileType: values.file,
      subjectId: values.subjectId
    };
    if (values.description) {
      params.description = values.description;
    }
    if (values.generatedFrom) {
      params.generatedFrom = values.generatedFrom;
    }
    if (values.reference) {
      params.referenceArn = values.reference;
    }
    try {
      return await this.omicsStorage.createUpload(params);
    } catch (err) {
      return false;
    }
  }

  uploadFile = async () => {
    const file = this.state.uploadingFile;
    await this.omicsStorage.uploadFile(file);
  }

  handleChangeFileType = (type) => {
    this.setState({file: type}, () => {
      if (!this.props.form.getFieldValue(Fields.reference)) {
        this.props.form.resetFields([Fields.reference]);
      }
      this.props.form.validateFieldsAndScroll();
    });
  }

  handleChangeReferenceInput = (e) => {
    e && e.preventDefault();
    if (e && e.target) {
      this.props.form.setFieldsValue({[Fields.reference]: e.target.value});
      this.props.form.validateFieldsAndScroll();
    }
  }

  validateRequiredField = (field, value, callback) => {
    if (!value) {
      this.setState({[FieldValid[field]]: false});
      // eslint-disable-next-line standard/no-callback-literal
      callback(`${FieldLabel[field]} is required`);
    } else {
      this.setState({[FieldValid[field]]: true});
    }
    callback();
  }

  renderRequiredStringField = (field) => {
    const {getFieldDecorator} = this.props.form;
    return (
      <Form.Item
        className={styles.omicsUploadFormItem}
        {...this.formItemLayout}
        label={FieldLabel[field]}>
        {getFieldDecorator(field, {
          initialValue: undefined,
          rules: [{
            required: true,
            validator: (rule, value, callback) => this.validateRequiredField(
              field,
              value,
              callback
            )
          }]
        })(
          <Input
            style={{width: '100%'}}
            disabled={this.state.uploading} />
        )}
      </Form.Item>
    );
  }

  getFooter = () => {
    const getCancelFooter = () => {
      return (
        <Row>
          <Button
            id="omics-upload-form-cancel-button"
            onClick={this.hideUploadMenu}
            disabled={!this.state.uploadMenuClosable}
          >
            Cancel
          </Button>
        </Row>
      );
    };
    const getUploadFooter = () => {
      return (
        <Row>
          <Button
            id="omics-upload-form-cancel-button"
            onClick={this.hideUploadMenu}
            disabled={!this.state.uploadMenuClosable}
          >
            Cancel
          </Button>
          <Button
            id="omics-upload-form-upload-button"
            type="primary"
            htmlType="submit"
            disabled={this.isUploadButtonDisabled}
            onClick={this.handleSubmit}
          >
            Upload
          </Button>
        </Row>
      );
    };
    const footer = (this.state.uploading || this.state.restrictedAccessCheckInProgress)
      ? false
      : (this.props.storageInfo &&
        !this.props.storageInfo.locked &&
        !this.state.restrictedAccess
        ? getUploadFooter()
        : getCancelFooter()
      );
    return footer;
  };

  // beforeUpload is not supported in IE9
  dummyRequest = ({file, onSuccess}) => {
    setTimeout(() => {
      onSuccess('ok');
    }, 0);
  };

  render () {
    const {getFieldDecorator} = this.props.form;
    const refType = ['AWS_OMICS_REF'];

    return (
      <div style={{display: 'inline'}}>
        <Upload
          showUploadList={false}
          customRequest={this.dummyRequest}
          beforeUpload={(file, fileList) => {
            return false;
          }}
          onChange={this.onUploadStatusChanged}
        >
          <Tooltip
            title={`Maximum ${MAX_FILE_SIZE_DESCRIPTION}`}
            trigger="hover"
          >
            <Button size="small" id="upload-button">
              <Icon type="upload" className={styles.uploadBtn} />
              <span className={styles.uploadBtn}>Upload</span>
            </Button>
          </Tooltip>
        </Upload>
        <Modal
          footer={this.getFooter()}
          title="Upload file to AWS Omics store"
          closable={this.state.uploadMenuClosable}
          onCancel={this.hideUploadMenu}
          visible={this.state.uploadMenuVisible}
        >
          <Form id="omics-upload-form">
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.file]}>
              {getFieldDecorator(Fields.file, {
                initialValue: undefined,
                rules: [{
                  required: true,
                  validator: (rule, value, callback) => (
                    this.validateRequiredField(Fields.file, value, callback)
                  )
                }]
              })(
                <Select
                  style={{width: '100%'}}
                  onChange={this.handleChangeFileType}
                  disabled={this.state.uploading}
                >
                  {this.fileTypes.map((type) => {
                    return (
                      <Select.Option key={type} title={type}>
                        {type}
                      </Select.Option>
                    );
                  })}
                </Select>
              )}
            </Form.Item>
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.reference]}>
              {getFieldDecorator(Fields.reference, {
                initialValue: undefined,
                rules: this.isReferenceRequired
                  ? [{
                    required: this.isReferenceRequired,
                    validator: (rule, value, callback) => {
                      return this.validateRequiredField(Fields.reference, value, callback);
                    }
                  }]
                  : undefined
              })(
                <Input
                  style={{width: '100%'}}
                  disabled={this.state.uploading}
                  placeholder="Reference"
                  addonBefore={
                    <div
                      className={styles.pathType}
                      onClick={this.openBucketBrowser}>
                      <Icon type="folder" />
                    </div>
                  }
                  onChange={this.handleChangeReferenceInput}
                />
              )}
            </Form.Item>
            {this.renderRequiredStringField(Fields.name)}
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.description]}>
              {getFieldDecorator(Fields.description, {
                initialValue: undefined
              })(
                <Input
                  type="textarea"
                  disabled={this.state.uploading} />
              )}
            </Form.Item>
            {this.renderRequiredStringField(Fields.sampleId)}
            {this.renderRequiredStringField(Fields.subjectId)}
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.generatedFrom]}>
              {getFieldDecorator(Fields.generatedFrom, {
                initialValue: undefined
              })(
                <Input
                  style={{width: '100%'}}
                  disabled={this.state.uploading} />
              )}
            </Form.Item>
            <BucketBrowser
              onSelect={this.selectBucketPath}
              onCancel={this.closeBucketBrowser}
              visible={this.state.referenceBrowserVisible}
              showOnlyFolder
              checkWritePermissions
              bucketTypes={refType} />
          </Form>
          {this.state.uploading &&
            <Row type="flex" style={{width: '100%'}}>
              <Col span={22} offset={2}>
                <Progress
                  strokeWidth={3}
                  key="file-progress"
                  percent={this.uploadPercentage} />
              </Col>
            </Row>
          }
        </Modal>
      </div>
    );
  }

  openBucketBrowser = () => {
    if (this.state.uploading) return;
    this.setState({
      referenceBrowserVisible: true
    });
  };

  closeBucketBrowser = () => {
    this.setState({
      referenceBrowserVisible: false
    });
  };

  selectBucketPath = (path) => {
    this.props.form.setFieldsValue({[Fields.reference]: path});
    this.props.form.validateFieldsAndScroll();
    this.closeBucketBrowser();
  };

  componentWillUnmount () {
    this.increaseCheckRestrictedAccessToken();
  }

  increaseCheckRestrictedAccessToken = () => {
    this.checkRestrictedAccessToken = (this.checkRestrictedAccessToken || 0) + 1;
    return this.checkRestrictedAccessToken;
  };

  checkRestrictedAccess = () => {
    const {storageInfo} = this.props;
    const {id} = storageInfo || {};
    const token = this.increaseCheckRestrictedAccessToken();
    this.setState({
      restrictedAccessCheckInProgress: true,
      restrictedAccess: true
    }, async () => {
      const state = {
        restrictedAccessCheckInProgress: false
      };
      try {
        state.restrictedAccess = await dataStorageRestrictedAccessCheck(id);
      } catch (_) {
        state.restrictedAccess = true;
      } finally {
        if (token === this.checkRestrictedAccessToken) {
          if (state.restrictedAccess) {
            console.log(`Storage #${id} is in the restricted access mode for current user`);
          }
          this.setState(state);
        }
      }
    });
  };
}
