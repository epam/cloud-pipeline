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
  MAX_FILE_SIZE_DESCRIPTION,
  SOURCE1,
  SOURCE2
} from '../../models/omics-upload/omics-storage';
import styles from './UploadOmicsButton.css';

const Fields = {
  fileType: 'fileType',
  source1: 'source1',
  source2: 'source2',
  reference: 'reference',
  name: 'name',
  description: 'description',
  sampleId: 'sampleId',
  subjectId: 'subjectId',
  generatedFrom: 'generatedFrom'
};

const FieldLabel = {
  [Fields.fileType]: 'File type',
  [Fields.source1]: 'Source',
  [Fields.source2]: 'Source 2',
  [Fields.reference]: 'Reference',
  [Fields.name]: 'Name',
  [Fields.description]: 'Description',
  [Fields.sampleId]: 'Sample id',
  [Fields.subjectId]: 'Subject id',
  [Fields.generatedFrom]: 'Generated from'
};

const FieldValid = {
  [Fields.fileType]: 'fileTypeValid',
  [Fields.source1]: 'sourceValid',
  [Fields.source2]: 'source2Valid',
  [Fields.reference]: 'referenceValid',
  [Fields.name]: 'nameValid',
  [Fields.sampleId]: 'sampleIdValid',
  [Fields.subjectId]: 'subjectIdValid'
};

const CommonRequiredFields = [
  Fields.fileType,
  Fields.source1,
  Fields.name,
  Fields.sampleId,
  Fields.subjectId
];

const FileTypes = {
  BAM: 'BAM',
  CRAM: 'CRAM',
  UBAM: 'UBAM',
  FASTQ: 'FASTQ'
};

const StateName = {
  [Fields.source1]: 'uploadingFile1',
  [Fields.source2]: 'uploadingFile2'
};

const FILE_REMOVED_STATUS = 'removed';

const getReferenceArn = (path) => {
  const [omics, empty, parts, referenceStoreId, reference, referenceId] = path.split('/');
  const [accountId, storage, region] = parts.split('.');
  return `arn:aws:omics:${region}:${accountId}:referenceStore/${referenceStoreId}/${reference}/${referenceId}`;
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
    uploading: false,
    restrictedAccess: true,
    restrictedAccessCheckInProgress: false,
    fileType: undefined,
    fileTypeValid: false,
    sourceValid: false,
    source2Valid: false,
    referenceValid: false,
    nameValid: false,
    sampleIdValid: false,
    subjectIdValid: false,
    uploadingFile1: null,
    uploadingFile2: null,
    referenceBrowserVisible: false
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

  get isFASTQ () {
    return this.state.fileType === FileTypes.FASTQ;
  }

  get isReferenceRequired () {
    const {fileType} = this.state;
    return fileType === FileTypes.BAM || fileType === FileTypes.CRAM;
  }

  get requiredFields () {
    if (this.isReferenceRequired) {
      return [...CommonRequiredFields, Fields.reference];
    }
    return CommonRequiredFields;
  }

  get isUploadButtonDisabled () {
    if (this.state.uploading) return true;
    return this.requiredFields.some(field => {
      return !this.state[FieldValid[field]];
    });
  }

  @computed
  get uploadPercentageSource1 () {
    const p = (this.omicsStorage || {}).uploadPercentageSource1;
    if (this.omicsStorage) console.log(this.omicsStorage, this.omicsStorage.uploadPercentageSource1);
    return p;
  }
  @computed
  get uploadPercentageSource2 () {
    const p = (this.omicsStorage || {}).uploadPercentageSource2;
    console.log(p);
    return p;
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
    this.setState({uploadMenuVisible: true});
  };

  hideUploadMenu = async () => {
    if (this.state.uploading) {
      await this.omicsStorage.abortUpload();
    }
    // this.onRemoveSource(undefined, 'source1');
    // this.onRemoveSource(undefined, 'source2');
    this.setState({
      uploadMenuVisible: false,
      uploading: false,
      fileType: undefined,
      fileTypeValid: false,
      sourceValid: false,
      source2Valid: false,
      referenceValid: false,
      nameValid: false,
      sampleIdValid: false,
      subjectIdValid: false,
      uploadingFile1: null,
      uploadingFile2: null,
      referenceBrowserVisible: false
    }, async () => {
      this.props.form.resetFields();
      if (this.props.onRefresh) {
        this.props.onRefresh(true);
      }
    });
  };

  handleSubmit = (e) => {
    e && e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.setState({uploading: true}, () => this.startUpload(values));
      }
    });
  };

  startUpload = async (values) => {
    const clientCreated = await this.createOmicClient();
    if (clientCreated) {
      const uploadCreated = await this.createUpload(values);
      if (uploadCreated) {
        await this.uploadFiles();
      }
    }
    this.setState({uploading: false}, () => {
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
      sourceFileType: values.fileType,
      subjectId: values.subjectId
    };
    if (values.description) {
      params.description = values.description;
    }
    if (values.generatedFrom) {
      params.generatedFrom = values.generatedFrom;
    }
    if (values.reference) {
      params.referenceArn = getReferenceArn(values.reference);
    }
    try {
      return await this.omicsStorage.createUpload(params);
    } catch (err) {
      return false;
    }
  }

  uploadFiles = async () => {
    const files = [this.state.uploadingFile1, this.state.uploadingFile2];
    const completed = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const source = i === 0 ? SOURCE1 : SOURCE2;
      completed.push(await this.omicsStorage.uploadFile(file, source));
    }
    const success = completed.every(c => c);
    if (success) {
      console.log('success')
      try {
        await this.omicsStorage.completeUpload();
      } catch (err) {
        await this.omicsStorage.abortUpload();
      }
    } else {
      console.log('fail')
      await this.omicsStorage.abortUpload();
    }
  }

  handleChangeFileType = (type) => {
    this.setState({
      fileType: type,
      [FieldValid[Fields.source2]]: false,
      [StateName[Fields.source2]]: null
    }, () => {
      if (this.props.form.getFieldValue(Fields.source2)) {
        this.props.form.resetFields([Fields.source2]);
      }
      if (!this.props.form.getFieldValue(Fields.reference)) {
        this.props.form.resetFields([Fields.reference]);
      }
      this.props.form.validateFields([Fields.reference]);
    });
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

  handleChangeReferenceInput = (event) => {
    if (event) {
      event.preventDefault();
      if (event.target) {
        this.props.form.setFieldsValue({[Fields.reference]: event.target.value});
        this.props.form.validateFields([Fields.reference]);
      }
    }
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

  renderSourceInput = (field) => {
    const {getFieldDecorator} = this.props.form;
    const isSource1 = field === Fields.source1;
    const stateField = StateName[field];
    const label = (isSource1 && this.isFASTQ &&
      this.props.form.getFieldValue([Fields.source1]))
      ? `${FieldLabel[field]} 1` : FieldLabel[field];

    // beforeUpload is not supported in IE9
    const dummyRequest = ({file, onSuccess}) => {
      setTimeout(() => {
        onSuccess('ok');
      }, 0);
    };

    const handleChangedSource = (info, field) => {
      const {file} = info;
      const sourceValid = file && (!file.status || file.status !== FILE_REMOVED_STATUS);
      info.fileList = sourceValid ? [file] : [];
      this.setState({
        [FieldValid[field]]: sourceValid,
        [stateField]: file
      }, () => {
        this.props.form.setFieldsValue({[Fields[field]]: file.name});
      //   this.props.form.validateFields([Fields[field]]);
      });
    };

    const preventEvent = (event) => {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
    };

    const onRemoveSource = (event) => {
      if (this.state.uploading) {
        return;
      }
      preventEvent(event);
      if (isSource1) {
        this.setState({
          [FieldValid[Fields.source1]]: false,
          [FieldValid[Fields.source2]]: false,
          [StateName[Fields.source1]]: null,
          [StateName[Fields.source2]]: null
        }, () => {
          this.props.form.resetFields([Fields.source1, Fields.source2]);
        });
      } else {
        this.setState({
          [FieldValid[field]]: false,
          [stateField]: null
        }, () => {
          this.props.form.resetFields([Fields[field]]);
        });
      }
    };

    return (
      <Form.Item
        className={styles.omicsUploadFormItem}
        {...this.formItemLayout}
        label={label}>
        {getFieldDecorator(field, {
          initialValue: undefined,
          rules: [{required: isSource1}]
        })(
          <Upload
            showUploadList={false}
            openFileDialogOnClick={false}
            customRequest={dummyRequest}
            beforeUpload={(file, fileList) => false}
            disabled={this.state.uploading}
            onChange={(info) => handleChangedSource(info, field)}
          >
            <Tooltip
              title={`Maximum ${MAX_FILE_SIZE_DESCRIPTION}`}
              trigger="hover"
            >
              <Button size="small" disabled={this.state.uploading}>
                <Icon type="folder" />
                Upload source file
              </Button>
            </Tooltip>
            {
              this.state[stateField] &&
              <span className={styles.sourceInfo}>
                <span onClick={preventEvent}>
                  {this.state[stateField].name}
                </span>
                <span
                  className={styles.deleteSource}
                  onClick={onRemoveSource}>
                  <Icon type="close" />
                </span>
              </span>
            }
          </Upload>
        )}
        {this.state.uploading &&
          <Row type="flex" style={{width: '100%'}}>
            <Col span={22} offset={2}>
              <Progress
                strokeWidth={3}
                key="file-progress"
                percent={this.uploadPercentageSource1} />
            </Col>
          </Row>
        }
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
    const footer = this.state.restrictedAccessCheckInProgress
      ? false
      : (this.props.storageInfo &&
        !this.props.storageInfo.locked &&
        !this.state.restrictedAccess
        ? getUploadFooter()
        : getCancelFooter()
      );
    return footer;
  };

  render () {
    const {getFieldDecorator} = this.props.form;
    const refType = ['AWS_OMICS_REF'];

    return (
      <div style={{display: 'inline'}}>
        <Button
          size="small"
          id="upload-button"
          onClick={() => this.showUploadMenu()}>
          <Icon type="upload" className={styles.uploadBtn} />
          <span className={styles.uploadBtn}>Upload</span>
        </Button>
        <Modal
          footer={this.getFooter()}
          title="Upload file to AWS Omics store"
          visible={this.state.uploadMenuVisible}
          onCancel={this.hideUploadMenu}
        >
          <Form>
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.fileType]}>
              {getFieldDecorator(Fields.fileType, {
                initialValue: undefined,
                rules: [{
                  required: true,
                  validator: (rule, value, callback) => (
                    this.validateRequiredField(Fields.fileType, value, callback)
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
            {this.renderSourceInput(Fields.source1)}
            {
              this.isFASTQ &&
              this.props.form.getFieldValue([Fields.source1]) &&
              this.renderSourceInput(Fields.source2)
            }
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={
                <Tooltip
                  title="Reference from ref storage"
                  trigger="hover"
                >
                  {FieldLabel[Fields.reference]}
                </Tooltip>
              }>
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
          </Form>
          <BucketBrowser
            onSelect={this.selectBucketPath}
            onCancel={this.closeBucketBrowser}
            visible={this.state.referenceBrowserVisible}
            showOnlyFolder
            checkWritePermissions
            bucketTypes={refType} />
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
    this.props.form.validateFields([Fields.reference]);
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
