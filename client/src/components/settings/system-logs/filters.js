/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {inject, observer} from 'mobx-react';
import styles from './filters.css';
import {
  Button,
  Checkbox,
  DatePicker,
  Icon,
  Input,
  Row,
  Select,
  Tooltip
} from 'antd';
import moment from 'moment-timezone';
import SystemLogsFilterDictionaries from '../../../models/system-logs/filter-dictionaries';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
export {DATE_FORMAT};

function Filter ({addonBefore, label, children, display = true}) {
  if (!display) {
    return null;
  }
  return (
    <div className={styles.filter}>
      {addonBefore}
      {label && (<span className={styles.label}>{label}:</span>)}
      {children}
    </div>
  );
}

@inject('authenticatedUserInfo', 'users')
@observer
class Filters extends React.Component {
  state = {
    showAdvanced: false
  };

  @observable dictionaries = new SystemLogsFilterDictionaries();

  @computed
  get hostNames () {
    if (this.dictionaries && this.dictionaries.loaded) {
      return ((this.dictionaries.value || {}).hostnames || []).map(o => o);
    }
    return [];
  }

  @computed
  get serviceNames () {
    if (this.dictionaries && this.dictionaries.loaded) {
      return ((this.dictionaries.value || {}).serviceNames || []).map(o => o);
    }
    return [];
  }

  @computed
  get types () {
    if (this.dictionaries && this.dictionaries.loaded) {
      return ((this.dictionaries.value || {}).types || []).map(o => o);
    }
    return [];
  }

  @computed
  get myUserName () {
    if (this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.userName;
    }
    return undefined;
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
    this.dictionaries.fetchIfNeededOrWait();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filters !== this.props.filters && this.props.filters) {
      this.updateFilters(this.props.filters);
    }
  }

  updateFilters = (filters) => {
    this.setState({filters});
  };

  get users () {
    const {users} = this.props;
    if (users.loaded) {
      const mapUserAttributes = (user) => {
        if (user.attributes) {
          const getAttributesValues = () => {
            const values = [];
            for (let key in user.attributes) {
              if (user.attributes.hasOwnProperty(key)) {
                values.push(user.attributes[key]);
              }
            }
            return values;
          };
          return getAttributesValues().join(', ');
        } else {
          return user.userName;
        }
      };
      return (users.value || []).map((user) => ({
        ...user,
        attributesString: mapUserAttributes(user) || ''
      }));
    }
    return [];
  }

  toggleAdvanced = () => {
    const {showAdvanced} = this.state;
    const {onExpand} = this.props;
    this.setState({
      showAdvanced: !showAdvanced
    }, () => {
      onExpand && onExpand(!showAdvanced);
    });
  };

  getUserLabel = (user) => (
    <span>{user.userName}{user.userName === this.myUserName ? <b> (you)</b> : ''}</span>
  );

  renderUserName = (user) => {
    if (user.attributesString) {
      return (
        <Row type="flex" style={{flexDirection: 'column'}}>
          <Row>{this.getUserLabel(user)}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{user.attributesString}</span></Row>
        </Row>
      );
    } else {
      return user.userName;
    }
  };

  render () {
    const {showAdvanced, filters = {}} = this.state;
    const {onChange, filters: initialFilters = {}} = this.props;
    const {
      messageTimestampFrom,
      messageTimestampTo,
      users,
      serviceNames,
      types,
      message,
      hostnames = [],
      includeServiceAccountEvents = false
    } = filters;
    let durationWarningVisible = false;
    if (messageTimestampFrom) {
      const from = moment.utc(messageTimestampFrom, DATE_FORMAT);
      const to = messageTimestampTo
        ? moment.utc(messageTimestampTo, DATE_FORMAT)
        : moment.utc();
      durationWarningVisible = to.diff(from, 'w') >= 1;
    }
    const onFieldChanged = (
      field,
      submit = false,
      converter = (o => o),
      eventField = 'value'
    ) => (event) => {
      let value = event;
      if (event && event.target) {
        value = event.target[eventField];
      }
      const newFilters = Object.assign({}, filters, {[field]: converter(value)});
      if (!value) {
        delete newFilters[field];
      }
      this.setState({filters: newFilters}, () => {
        submit && onChange && onChange(newFilters);
      });
    };
    const submitFilters = (checkField) => () => {
      if (checkField) {
        const initial = initialFilters[checkField];
        const actual = filters[checkField];
        if (initial === actual) {
          return;
        }
      }
      onChange && onChange(filters);
    };
    const momentDateConverter = d => d
      ? moment.utc(d).format(DATE_FORMAT)
      : undefined;
    const momentDateParser = d => d
      ? moment(moment.utc(d, DATE_FORMAT).toDate())
      : undefined;
    const commonStyle = {flex: 1};
    const getDisabledDate = ({min, max}) => (date) => {
      let disabled = false;
      if (min) {
        disabled = disabled || date < min;
      }
      if (max) {
        disabled = disabled || date > max;
      }
      return disabled;
    };
    return (
      <div className={styles.filters}>
        <Filter
          label="From"
          addonBefore={
            durationWarningVisible && (
              <Tooltip
                placement="right"
                title={(
                  <div>
                    It's recommended to limit the logs retrieval
                    duration to a maximum of a week.<br />
                    Otherwise the request may timeout
                  </div>
                )}
              >
                <Icon
                  type="info-circle"
                  style={{marginRight: 5, color: 'orange'}}
                />
              </Tooltip>
            )
          }
        >
          <DatePicker
            showTime
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="From"
            style={commonStyle}
            value={momentDateParser(messageTimestampFrom)}
            onChange={onFieldChanged('messageTimestampFrom', true, momentDateConverter)}
            disabledDate={getDisabledDate({max: momentDateParser(messageTimestampTo)})}
          />
        </Filter>
        <Filter label="To">
          <DatePicker
            showTime
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="To"
            style={commonStyle}
            value={momentDateParser(messageTimestampTo)}
            onChange={onFieldChanged('messageTimestampTo', true, momentDateConverter)}
            disabledDate={getDisabledDate({min: momentDateParser(messageTimestampFrom)})}
          />
        </Filter>
        <Filter label="Service">
          <Select
            allowClear
            showSearch
            mode="multiple"
            placeholder="Service"
            style={commonStyle}
            filterOption={
              (input, option) =>
                option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
            value={serviceNames}
            onChange={onFieldChanged('serviceNames', true)}
          >
            {
              this.serviceNames.map((name) => (
                <Select.Option
                  key={name}
                  value={name}
                >
                  {name}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
        <Filter label="User">
          <Select
            allowClear
            showSearch
            dropdownMatchSelectWidth={false}
            optionLabelProp="label"
            mode="multiple"
            placeholder="User"
            style={commonStyle}
            filterOption={
              (input, option) =>
                option.props.attributesString.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
            value={users}
            onChange={onFieldChanged('users', true)}
          >
            {
              this.users.map((user) => (
                <Select.Option
                  key={user.userName}
                  value={user.userName}
                  attributesString={user.attributesString}
                  label={this.getUserLabel(user)}
                >
                  {this.renderUserName(user)}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
        <Filter label="Message">
          <Input
            style={commonStyle}
            value={message}
            onChange={onFieldChanged('message')}
            onPressEnter={submitFilters('message')}
            onBlur={submitFilters('message')}
          />
        </Filter>
        <Filter label="Hostname" display={showAdvanced}>
          <Select
            allowClear
            showSearch
            placeholder="Hostname"
            mode="multiple"
            style={commonStyle}
            filterOption={
              (input, option) =>
                option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
            value={hostnames}
            onChange={onFieldChanged('hostnames', true)}
          >
            {
              this.hostNames.map((name) => (
                <Select.Option
                  key={name}
                  value={name}
                >
                  {name}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
        <Filter label="Type" display={showAdvanced}>
          <Select
            allowClear
            showSearch
            placeholder="Type"
            mode="multiple"
            style={commonStyle}
            filterOption={
              (input, option) =>
                option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
            value={types}
            onChange={onFieldChanged('types', true)}
          >
            {
              this.types.map((name) => (
                <Select.Option
                  key={name}
                  value={name}
                >
                  {name}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
        <Filter display={showAdvanced}>
          <Checkbox
            checked={includeServiceAccountEvents}
            onChange={
              onFieldChanged(
                'includeServiceAccountEvents',
                true,
                undefined,
                'checked'
              )
            }
          >
            Include Service Account Events
          </Checkbox>
        </Filter>
        <div className={styles.filter} style={{minWidth: 'unset'}}>
          <Button
            id="show-hide-advanced"
            onClick={this.toggleAdvanced}
            size="small"
            style={{lineHeight: 1}}
          >
            {showAdvanced ? 'Hide' : 'Show'} advanced
          </Button>
        </div>
      </div>
    );
  }
}

Filters.propTypes = {
  filters: PropTypes.object,
  onChange: PropTypes.func,
  onExpand: PropTypes.func,
  onInitialized: PropTypes.func
};

export default Filters;
