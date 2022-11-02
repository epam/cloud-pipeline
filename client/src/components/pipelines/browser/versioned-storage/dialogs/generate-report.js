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
import {inject, observer} from 'mobx-react';
import {
  Modal,
  Row,
  Input,
  DatePicker,
  Select,
  Button,
  Checkbox,
  Radio
} from 'antd';
import moment from 'moment-timezone';
import UserName from '../../../../special/UserName';
import Divider from '../../../../special/Divider';
import localization from '../../../../../utils/localization';
import styles from './generate-report.css';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
const ID_COMPONENT = 'report';

const SPLIT_DIFFS_BY = {
  revision: 'BY_COMMIT',
  files: 'BY_FILE'
};

@inject('usersInfo')
@localization.localizedComponent
@observer
class GenerateReportDialog extends localization.LocalizedReactComponent {
  state = {
    authors: [],
    extensions: undefined,
    dateFrom: undefined,
    dateTo: undefined,
    includeDiff: false,
    splitDiffsBy: SPLIT_DIFFS_BY.revision,
    downloadAsArchive: false
  };

  get reportSettings () {
    const {
      authors = [],
      extensions = '',
      dateFrom,
      dateTo,
      includeDiff,
      splitDiffsBy,
      downloadAsArchive
    } = this.state;
    const parsedExtensions = (extensions.trim())
      .split(',')
      .map(extension => {
        const trimmedExtension = extension.trim();
        return trimmedExtension.startsWith('.')
          ? trimmedExtension.substring(1)
          : trimmedExtension;
      })
      .filter(Boolean);
    return {
      authors,
      extensions: [...(new Set(parsedExtensions))],
      dateFrom: dateFrom
        ? moment.utc(dateFrom).format(DATE_FORMAT)
        : undefined,
      dateTo: dateTo
        ? moment.utc(dateTo).format(DATE_FORMAT)
        : undefined,
      includeDiff,
      splitDiffsBy: includeDiff ? splitDiffsBy : undefined,
      downloadAsArchive: includeDiff ? downloadAsArchive : undefined
    };
  };

  getHtmlId = (id) => {
    const {idPrefix} = this.props;
    if (id && idPrefix) {
      return `${idPrefix}_${ID_COMPONENT}_${id}`;
    }
    return null;
  }

  handleOk = () => {
    const {onOk} = this.props;
    onOk && onOk(this.reportSettings);
    this.resetSettings();
  };

  handleCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
    this.resetSettings();
  };

  resetSettings = () => {
    this.setState({
      authors: [],
      extensions: undefined,
      dateFrom: undefined,
      dateTo: undefined,
      includeDiff: false,
      splitDiffsBy: SPLIT_DIFFS_BY.revision,
      downloadAsArchive: false
    });
  };

  onUsersChange = (value) => {
    this.setState({
      authors: (value || []).slice()
    });
  };

  onDateChange = (fieldType, startOfTheDate) => (date) => {
    let dateCorrected = date;
    if (dateCorrected) {
      if (startOfTheDate) {
        dateCorrected = moment(dateCorrected).startOf('D');
      } else {
        dateCorrected = moment(dateCorrected).endOf('D');
      }
    }
    this.setState({
      [fieldType]: dateCorrected
    });
  };

  onExtensionsChange = (event) => {
    const newValue = event?.target?.value || '';
    this.setState({
      extensions: newValue
    });
  };

  onToggleDiffs = (event) => {
    const {checked} = event.target;
    this.setState({includeDiff: checked});
  };

  onSplitDiffsChange = (event) => {
    const {value} = event.target;
    if (value) {
      this.setState({splitDiffsBy: value});
    }
  };

  onDownloadAsArchiveChange = (event) => {
    const {checked} = event.target;
    this.setState({downloadAsArchive: checked});
  };

  renderUserRow = () => {
    const {usersInfo} = this.props;
    const pending = usersInfo && usersInfo.pending && !usersInfo.loaded;
    const list = usersInfo && usersInfo.loaded
      ? (usersInfo.value || []).slice()
      : [];
    const {authors = []} = this.state;
    return (
      <Row
        className={styles.reportsRow}
        type="flex"
        justify="space-between"
        id={this.getHtmlId('author-container')}
      >
        <span className={styles.label}>
          Author:
        </span>
        <Select
          disabled={pending}
          mode="multiple"
          placeholder="All"
          onChange={this.onUsersChange}
          style={{width: '70%'}}
          value={authors || []}
          filterOption={
            (input, option) => option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }
        >
          {
            list.map(user => (
              <Select.Option
                key={user.name}
                value={user.name}
              >
                <UserName userName={user.name} />
              </Select.Option>
            ))
          }
        </Select>
      </Row>
    );
  };

  renderDateRow = () => {
    const {dateFrom, dateTo} = this.state;
    return (
      <Row
        className={styles.reportsRow}
        type="flex"
        justify="space-between"
      >
        <span className={styles.label}>
          Date:
        </span>
        <div
          className={styles.dateRow}
          id={this.getHtmlId('date-container')}
        >
          <DatePicker
            className={this.getHtmlId('date-from')}
            format="YYYY-MM-DD"
            placeholder="From"
            value={dateFrom}
            onChange={this.onDateChange('dateFrom', true)}
            style={{width: '50%', marginRight: '10px'}}
          />
          <DatePicker
            className={this.getHtmlId('date-to')}
            format="YYYY-MM-DD"
            placeholder="To"
            value={dateTo}
            onChange={this.onDateChange('dateTo')}
            style={{width: '50%'}}
          />
        </div>
      </Row>
    );
  };

  renderExtensionsRow = () => {
    const {extensions} = this.state;
    return (
      <Row
        className={styles.reportsRow}
        type="flex"
        justify="space-between"
      >
        <span className={styles.label}>
          Changed file types:
        </span>
        <Input
          id={this.getHtmlId('file-types')}
          style={{width: '70%'}}
          type="text"
          placeholder="Comma-separated file extensions"
          onChange={this.onExtensionsChange}
          value={extensions}
        />
      </Row>
    );
  };

  renderDiffControls = () => {
    const {splitDiffsBy, downloadAsArchive} = this.state;
    return (
      <Row type="flex">
        <div className={styles.diffRadioGroup}>
          <Radio.Group
            onChange={this.onSplitDiffsChange}
            value={splitDiffsBy}
            id={this.getHtmlId('split-radio-group')}
          >
            <Radio
              value={SPLIT_DIFFS_BY.revision}
              className={styles.diffRadioBtn}
              id={this.getHtmlId('split-by-revision')}
            >
              Split changes by <b>revision</b>
            </Radio>
            <Radio
              value={SPLIT_DIFFS_BY.files}
              className={styles.diffRadioBtn}
              id={this.getHtmlId('split-by-files')}
            >
              Split changes by <b>files</b>
            </Radio>
          </Radio.Group>
          <Checkbox
            checked={downloadAsArchive}
            onChange={this.onDownloadAsArchiveChange}
            style={{padding: '5px 0'}}
            id={this.getHtmlId('download-archive')}
          >
            <span className={styles.userSelectNone}>
              Save changes separately (download report as archive)
            </span>
          </Checkbox>
        </div>
      </Row>
    );
  };

  render () {
    const {visible} = this.props;
    const {includeDiff} = this.state;
    const footer = (
      <Row
        type="flex"
        justify="space-between"
      >
        <Button
          onClick={this.handleCancel}
          id={this.getHtmlId('cancel-btn')}
        >
          CANCEL
        </Button>
        <Button
          type="primary"
          onClick={this.handleOk}
          id={this.getHtmlId('ok-btn')}
        >
          DOWNLOAD REPORT
        </Button>
      </Row>);
    return (
      <Modal
        visible={visible}
        onOk={this.handleOk}
        onCancel={this.handleCancel}
        bodyStyle={{padding: '20px'}}
        footer={footer}
        title="Generate report"
      >
        <span className={styles.filterGroupHeader}>
          Revision filters
        </span>
        {this.renderUserRow()}
        {this.renderDateRow()}
        {this.renderExtensionsRow()}
        <div id={this.getHtmlId('include-diffs-container')}>
          <Checkbox
            onChange={this.onToggleDiffs}
            checked={includeDiff}
            style={{padding: '5px 0', userSelect: 'none'}}
          >
            Include file diffs
          </Checkbox>
        </div>
        {includeDiff && <Divider />}
        {includeDiff && this.renderDiffControls()}
      </Modal>
    );
  };
}

GenerateReportDialog.PropTypes = {
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onOk: PropTypes.func,
  idPrefix: PropTypes.string
};

export default GenerateReportDialog;
