/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Form, Row, Upload, Button, Modal, Select, Input, Tooltip, message} from 'antd';
import {CloseOutlined, FolderOutlined, LoadingOutlined, UploadOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import dataStorageRestrictedAccessCheck from '../../utils/data-storage-restricted-access';
import BucketBrowser from '../pipelines/launch/dialogs/BucketBrowser';
import OmicsStorage, {MAX_FILE_SIZE_DESCRIPTION} from '../../models/omics-upload/omics-storage';
import styles from './UploadOmicsButton.module.css';

const Fields = {
  fileType: 'fileType',
  source1: 'source1',
  source2: 'source2',
  reference: 'reference',
  name: 'name',
  description: 'description',
  sampleId: 'sampleId',
  subjectId: 'subjectId',
  generatedFrom: 'generatedFrom',
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
  [Fields.generatedFrom]: 'Generated from',
};

const FieldValid = {
  [Fields.fileType]: 'fileTypeValid',
  [Fields.source1]: 'sourceValid',
  [Fields.source2]: 'source2Valid',
  [Fields.reference]: 'referenceValid',
  [Fields.name]: 'nameValid',
  [Fields.sampleId]: 'sampleIdValid',
  [Fields.subjectId]: 'subjectIdValid',
};

const CommonRequiredFields = [
  Fields.fileType,
  Fields.source1,
  Fields.name,
  Fields.sampleId,
  Fields.subjectId,
];

const FileTypes = {
  BAM: 'BAM',
  CRAM: 'CRAM',
  UBAM: 'UBAM',
  FASTQ: 'FASTQ',
};

const ExtensionPattern = {
  [FileTypes.BAM]: /\.bam$/,
  [FileTypes.CRAM]: /\.cram$/,
  [FileTypes.UBAM]: /\.bam$/,
  [FileTypes.FASTQ]: /(\.fastq\.gz|\.fq\.gz)$/,
};

const FileExtension = {
  [FileTypes.BAM]: 'bam',
  [FileTypes.CRAM]: 'cram',
  [FileTypes.UBAM]: 'bam',
  [FileTypes.FASTQ]: 'fastq.gz, fq.gz',
};

const FILE_REMOVED_STATUS = 'removed';

const SOURCE1 = 'SOURCE1';
const SOURCE2 = 'SOURCE2';

const getReferenceArn = (path) => {
  const [omics, empty, parts, referenceStoreId, reference, referenceId] = path.split('/');
  const [accountId, storage, region] = parts.split('.');
  return `arn:aws:omics:${region}:${accountId}:referenceStore/${referenceStoreId}/${reference}/${referenceId}`;
};

export class UploadOmicsButton extends React.Component {
  formRef = React.createRef();

  static propTypes = {
    storageInfo: PropTypes.object,
    region: PropTypes.string,
    onRefresh: PropTypes.func,
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
    uploadingFiles: {
      source1: null,
      source2: null,
    },
    referenceBrowserVisible: false,
    validationError: {
      source1: null,
      source2: null,
    },
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6},
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18},
    },
  };

  get fileTypes() {
    return Object.values(FileTypes || {}) || [];
  }

  get isFASTQ() {
    return this.state.fileType === FileTypes.FASTQ;
  }

  get isReferenceRequired() {
    const {fileType} = this.state;
    return fileType === FileTypes.BAM || fileType === FileTypes.CRAM;
  }

  get requiredFields() {
    if (this.isReferenceRequired) {
      return [...CommonRequiredFields, Fields.reference];
    }
    if (this.isFASTQ) {
      return [...CommonRequiredFields, Fields.source2];
    }
    return CommonRequiredFields;
  }

  get isUploadButtonDisabled() {
    if (this.state.uploading) return true;
    return this.requiredFields.some((field) => {
      return !this.state[FieldValid[field]];
    });
  }

  componentDidMount() {
    this.checkRestrictedAccess();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const storageChanged = (a, b) => {
      const {id: aID} = a || {};
      const {id: bID} = b || {};
      return aID !== bID;
    };
    if (storageChanged(this.props.storageInfo, prevProps.storageInfo)) {
      this.checkRestrictedAccess();
    }
    const {uploadError} = this.omicsStorage || {};
    if (uploadError) {
      message.error(uploadError, 5);
      this.omicsStorage.uploadError = null;
    }
  }

  showUploadMenu = (file) => {
    this.setState({uploadMenuVisible: true});
  };

  hideUploadMenu = async () => {
    if (this.state.uploading) {
      await this.abortUpload();
    }
    this.setState(
      {
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
        uploadingFiles: {
          source1: null,
          source2: null,
        },
        referenceBrowserVisible: false,
        validationError: {
          source1: null,
          source2: null,
        },
      },
      async () => {
        if (this.formRef.current) {
          this.formRef.current.resetFields();
        }
        if (this.props.onRefresh) {
          this.props.onRefresh(true);
        }
      },
    );
  };

  handleSubmit = (e) => {
    if (e) {
      e.preventDefault();
    }
    this.formRef.current
      .validateFields()
      .then((values) => {
        this.setState({uploading: true}, () => this.startUpload(values));
      })
      .catch(() => {});
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
  };

  createOmicClient = async () => {
    const config = {
      region: this.props.region,
      storage: this.props.storageInfo,
    };
    try {
      this.omicsStorage = new OmicsStorage(config);
      return await this.omicsStorage.createClient();
    } catch (err) {
      return false;
    }
  };

  createUpload = async (values) => {
    const params = {
      name: values.name,
      sampleId: values.sampleId,
      sequenceStoreId: this.props.storageInfo.cloudStorageId,
      sourceFileType: values.fileType,
      subjectId: values.subjectId,
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
  };

  uploadFiles = async () => {
    const files = [this.state.uploadingFiles.source1, this.state.uploadingFiles.source2].filter(
      (f) => f,
    );
    const completed = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const source = i === 0 ? SOURCE1 : SOURCE2;
      const uploadedFile = await this.omicsStorage.uploadFile(file, source);
      completed.push(uploadedFile);
    }
    const success = completed.every((c) => c);
    if (success) {
      await this.omicsStorage.completeUpload();
    } else {
      await this.abortUpload();
    }
  };

  abortUpload = async () => {
    await this.omicsStorage.abortUpload();
  };

  handleChangeFileType = (type) => {
    const {uploadingFiles} = this.state;
    uploadingFiles[Fields.source2] = null;
    this.setState(
      {
        fileType: type,
        [FieldValid[Fields.source2]]: false,
        uploadingFiles,
      },
      () => {
        const form = this.formRef.current;
        if (form) {
          if (form.getFieldValue(Fields.source2)) {
            form.resetFields([Fields.source2]);
          }
          if (!form.getFieldValue(Fields.reference)) {
            form.resetFields([Fields.reference]);
          }
          this.validateFiles();
          form.validateFields([Fields.reference]).catch(() => {});
        }
      },
    );
  };

  validateRequiredField = (rule, value, field) => {
    if (!value) {
      this.setState({[FieldValid[field]]: false});
      return Promise.reject(new Error(`${FieldLabel[field]} is required`));
    }
    this.setState({[FieldValid[field]]: true});
    return Promise.resolve();
  };

  handleChangeReferenceInput = (event) => {
    if (event && event.target && this.formRef.current) {
      event.preventDefault();
      this.formRef.current.setFieldsValue({[Fields.reference]: event.target.value});
      this.formRef.current.validateFields([Fields.reference]).catch(() => {});
    }
  };

  renderRequiredStringField = (field) => {
    return (
      <Form.Item
        className={styles.omicsUploadFormItem}
        {...this.formItemLayout}
        label={FieldLabel[field]}
        name={field}
        rules={[
          {
            required: true,
            validator: (rule, value) => this.validateRequiredField(rule, value, field),
          },
        ]}
      >
        <Input style={{width: '100%'}} disabled={this.state.uploading} />
      </Form.Item>
    );
  };

  validateFiles = () => {
    const sources = ['source1', 'source2'];
    const files = [this.state.uploadingFiles[sources[0]], this.state.uploadingFiles[sources[1]]];
    if (files && files.length) {
      for (let i = 0; i < files.length; i++) {
        this.validateSourceExtension(sources[i], {file: files[i]}, () => {});
      }
    }
  };

  validateSourceExtension = (field, value = '', callback = () => {}) => {
    const isSource1 = field === Fields.source1;
    const isString = typeof value === 'string';
    if ((!value && isString) || (value && !isString && !value.file)) {
      if (isSource1) {
        const error = `${FieldLabel[field]} is required`;
        const {validationError} = this.state;
        validationError[field] = error;
        this.setState({
          [FieldValid[field]]: false,
          validationError,
        });
        callback(error);
      } else {
        const {validationError} = this.state;
        validationError[field] = null;
        this.setState({
          [FieldValid[field]]: true,
          validationError,
        });
        callback();
      }
    } else {
      if (this.state.fileType) {
        const fileName = value.file ? value.file.name : isString ? value : '';
        const valid = ExtensionPattern[this.state.fileType].test(fileName);
        if (!valid) {
          const error = `ReadSet with Type ${this.state.fileType}
            suppots file extentions [${FileExtension[this.state.fileType]}] only`;
          const {validationError} = this.state;
          validationError[field] = error;
          this.setState({
            [FieldValid[field]]: false,
            validationError,
          });
          callback(error);
        } else {
          const {validationError} = this.state;
          validationError[field] = null;
          this.setState({
            [FieldValid[field]]: true,
            validationError,
          });
          callback();
        }
      }
    }
  };

  renderSourceInput = (field) => {
    const form = this.formRef.current;
    const isSource1 = field === Fields.source1;
    const label =
      isSource1 && this.isFASTQ && form && form.getFieldValue(Fields.source1)
        ? `${FieldLabel[field]} 1`
        : FieldLabel[field];

    // beforeUpload is not supported in IE9
    const dummyRequest = ({file, onSuccess}) => {
      setTimeout(() => {
        onSuccess('ok');
      }, 0);
    };

    const handleChangedSource = (info, fieldName) => {
      const {file} = info;
      const notRemoved = file && (!file.status || file.status !== FILE_REMOVED_STATUS);
      info.fileList = notRemoved ? [file] : [];
      const {uploadingFiles} = this.state;
      uploadingFiles[fieldName] = file;
      this.setState(
        {
          uploadingFiles,
        },
        () => {
          if (this.formRef.current) {
            this.formRef.current.setFieldsValue({[fieldName]: file.name});
          }
        },
      );
    };

    const preventEvent = (event) => {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
    };

    const onRemoveSource = async (event) => {
      preventEvent(event);
      if (this.state.uploading) {
        await this.abortUpload().then((res) => {
          if (res) {
            this.setState({
              uploading: false,
            });
          }
        });
        return;
      }
      if (isSource1) {
        const {uploadingFiles} = this.state;
        uploadingFiles[Fields.source1] = null;
        uploadingFiles[Fields.source2] = null;
        this.setState(
          {
            [FieldValid[Fields.source1]]: false,
            [FieldValid[Fields.source2]]: false,
            uploadingFiles,
          },
          () => {
            this.validateSourceExtension(field);
            if (this.formRef.current) {
              this.formRef.current.resetFields([Fields.source1, Fields.source2]);
            }
          },
        );
      } else {
        const {uploadingFiles} = this.state;
        uploadingFiles[field] = null;
        this.setState(
          {
            [FieldValid[field]]: false,
            uploadingFiles,
          },
          () => {
            this.validateSourceExtension(field);
            if (this.formRef.current) {
              this.formRef.current.resetFields([Fields[field]]);
            }
          },
        );
      }
    };

    return (
      <Form.Item
        className={styles.omicsUploadFormItem}
        {...this.formItemLayout}
        label={label}
        name={field}
        rules={[
          {
            required: isSource1,
            validator: (rule, value) => {
              return new Promise((resolve, reject) => {
                this.validateSourceExtension(field, value, (err) => {
                  if (err) reject(err);
                  else resolve();
                });
              });
            },
          },
        ]}
      >
        <Upload
          showUploadList={false}
          openFileDialogOnClick={false}
          customRequest={dummyRequest}
          beforeUpload={(file, fileList) => false}
          disabled={this.state.uploading}
          onChange={(info) => handleChangedSource(info, field)}
        >
          <Tooltip title={`Maximum ${MAX_FILE_SIZE_DESCRIPTION}`} trigger="hover">
            <Button size="small" disabled={this.state.uploading}>
              <FolderOutlined />
              Upload source file
            </Button>
          </Tooltip>
          {this.state.uploadingFiles[field] && (
            <span className={styles.sourceInfo}>
              <span onClick={preventEvent}>{this.state.uploadingFiles[field].name}</span>
              <Tooltip
                title={this.state.uploading ? 'Cancel upload' : 'Clear source file'}
                trigger="hover"
              >
                <span
                  className={classNames({
                    [styles.clearSource]: !this.state.uploading,
                    [styles.cancelSource]: this.state.uploading,
                  })}
                  onClick={onRemoveSource}
                >
                  <CloseOutlined />
                </span>
              </Tooltip>
            </span>
          )}
          {!this.state[FieldValid[field]] && this.state.validationError[field] && (
            <div className={styles.sourceValidationError}>{this.state.validationError[field]}</div>
          )}
        </Upload>
      </Form.Item>
    );
  };

  getFooter = () => {
    const getCancelFooter = () => {
      return (
        <Row>
          <Button id="omics-upload-form-cancel-button" onClick={this.hideUploadMenu}>
            Cancel
          </Button>
        </Row>
      );
    };
    const getUploadFooter = () => {
      return (
        <Row>
          <Button id="omics-upload-form-cancel-button" onClick={this.hideUploadMenu}>
            Cancel
          </Button>
          <Button
            id="omics-upload-form-upload-button"
            type="primary"
            htmlType="submit"
            disabled={this.isUploadButtonDisabled}
            onClick={this.handleSubmit}
          >
            {this.state.uploading ? <LoadingOutlined /> : 'Upload'}
          </Button>
        </Row>
      );
    };
    const footer = this.state.restrictedAccessCheckInProgress
      ? false
      : this.props.storageInfo && !this.props.storageInfo.locked && !this.state.restrictedAccess
        ? getUploadFooter()
        : getCancelFooter();
    return footer;
  };

  render() {
    const refType = ['AWS_OMICS_REF'];

    return (
      <div style={{display: 'inline'}}>
        <Button size="small" id="upload-button" onClick={() => this.showUploadMenu()}>
          <UploadOutlined className={styles.uploadBtn} />
          <span className={styles.uploadBtn}>Upload</span>
        </Button>
        <Modal
          footer={this.getFooter()}
          title="Upload file to AWS HealthOmics Store"
          open={this.state.uploadMenuVisible}
          onCancel={this.hideUploadMenu}
          closable={false}
        >
          <Form ref={this.formRef}>
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.fileType]}
              name={Fields.fileType}
              rules={[
                {
                  required: true,
                  validator: (rule, value) =>
                    this.validateRequiredField(rule, value, Fields.fileType),
                },
              ]}
            >
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
            </Form.Item>
            {this.renderSourceInput(Fields.source1)}
            {this.isFASTQ &&
              this.formRef.current &&
              this.formRef.current.getFieldValue(Fields.source1) &&
              this.renderSourceInput(Fields.source2)}
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={
                <Tooltip title="Reference from ref storage" trigger="hover">
                  {FieldLabel[Fields.reference]}
                </Tooltip>
              }
              name={Fields.reference}
              rules={
                this.isReferenceRequired
                  ? [
                      {
                        required: this.isReferenceRequired,
                        validator: (rule, value) =>
                          this.validateRequiredField(rule, value, Fields.reference),
                      },
                    ]
                  : undefined
              }
            >
              <Input
                style={{width: '100%'}}
                disabled={this.state.uploading}
                placeholder="Reference"
                addonBefore={
                  <div className={styles.pathType} onClick={this.openBucketBrowser}>
                    <FolderOutlined />
                  </div>
                }
                onChange={this.handleChangeReferenceInput}
              />
            </Form.Item>
            {this.renderRequiredStringField(Fields.name)}
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.description]}
              name={Fields.description}
            >
              <Input type="textarea" disabled={this.state.uploading} />
            </Form.Item>
            {this.renderRequiredStringField(Fields.sampleId)}
            {this.renderRequiredStringField(Fields.subjectId)}
            <Form.Item
              className={styles.omicsUploadFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.generatedFrom]}
              name={Fields.generatedFrom}
            >
              <Input style={{width: '100%'}} disabled={this.state.uploading} />
            </Form.Item>
          </Form>
          <BucketBrowser
            onSelect={this.selectBucketPath}
            onCancel={this.closeBucketBrowser}
            visible={this.state.referenceBrowserVisible}
            showOnlyFolder
            checkWritePermissions
            bucketTypes={refType}
          />
        </Modal>
      </div>
    );
  }

  openBucketBrowser = () => {
    if (this.state.uploading) return;
    this.setState({
      referenceBrowserVisible: true,
    });
  };

  closeBucketBrowser = () => {
    this.setState({
      referenceBrowserVisible: false,
    });
  };

  selectBucketPath = (path) => {
    if (this.formRef.current) {
      this.formRef.current.setFieldsValue({[Fields.reference]: path});
      this.formRef.current.validateFields([Fields.reference]).catch(() => {});
    }
    this.closeBucketBrowser();
  };

  componentWillUnmount() {
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
    this.setState(
      {
        restrictedAccessCheckInProgress: true,
        restrictedAccess: true,
      },
      async () => {
        const state = {
          restrictedAccessCheckInProgress: false,
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
      },
    );
  };
}
