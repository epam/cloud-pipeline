import React from 'react';
import PropTypes from 'prop-types';
import {
  observer} from 'mobx-react';
import {
  Checkbox,
  Popover,
  DatePicker,
  Button
} from 'antd';
import {CloseOutlined} from '@ant-design/icons';
import moment from 'moment';
import {momentToDayjs, dayjsToMoment} from '../../../../utils/antd-date-utils';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
const DATE_DISPLAY_FORMAT = 'YYYY-MM-DD';

function toLocalMomentDate (string) {
  if (!string) {
    return string;
  }
  const time = moment.utc(string);
  if (time.isValid()) {
    const localTime = moment.utc(string).toDate();
    return moment(localTime);
  }
  return undefined;
}

@observer
class RangeDatePicker extends React.Component {
  state = {
    dateFrom: undefined,
    dateTo: undefined,
    emptyValue: false,
    fromPickerVisible: false,
    toPickerVisible: false,
    rangeFilterVisible: false
  }

  get modified () {
    const {
      from,
      to,
      emptyValue
    } = this.props;
    const {
      dateFrom,
      dateTo,
      emptyValue: emptyValueState
    } = this.state;
    const stateFrom = dateFrom ? moment.utc(dateFrom).format(DATE_FORMAT) : undefined;
    const stateTo = dateTo ? moment.utc(dateTo).format(DATE_FORMAT) : undefined;
    return from !== stateFrom ||
      to !== stateTo ||
      emptyValue !== emptyValueState;
  }

  get resetDisabled () {
    return !this.props.from &&
    !this.props.to &&
    (this.props.supportEmptyValue && !this.props.emptyValue);
  }

  componentDidMount () {
    this.updateValuesFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.from !== prevProps.from ||
      this.props.to !== prevProps.to ||
      prevState.rangeFilterVisible !== this.state.rangeFilterVisible
    ) {
      this.updateValuesFromProps();
    }
  }

  updateValuesFromProps = () => {
    this.setState({
      dateFrom: toLocalMomentDate(this.props.from),
      dateTo: toLocalMomentDate(this.props.to)
    });
  };

  disabledStartDate = (startValue) => {
    const endValue = this.state.dateTo;
    if (!startValue || !endValue) {
      return false;
    }
    const endDayjs = momentToDayjs(endValue);
    return endDayjs ? startValue.isAfter(endDayjs, 'day') : false;
  }
  disabledEndDate = (endValue) => {
    const startValue = this.state.dateFrom;
    if (!startValue) {
      return endValue && endValue.isAfter(momentToDayjs(toLocalMomentDate(moment().toDate())), 'day');
    } else if (!endValue) {
      return false;
    }
    const startDayjs = momentToDayjs(startValue);
    const today = momentToDayjs(toLocalMomentDate(moment().toDate()));
    return (
      (startDayjs && endValue.isBefore(startDayjs, 'day')) ||
      (today && endValue.isAfter(today, 'day'))
    );
  }

  onStartChange = (value) => {
    const m = value ? dayjsToMoment(value) : null;
    if (m) {
      this.setState({
        dateFrom: m.clone().startOf('day')
      });
    } else {
      this.setState({
        dateFrom: undefined
      });
    }
  }

  onEndChange = (value) => {
    const m = value ? dayjsToMoment(value) : null;
    if (m) {
      this.setState({
        dateTo: m.clone().endOf('day')
      });
    } else {
      this.setState({
        dateTo: undefined
      });
    }
  }
  handleStartOpenChange = (open) => {
    this.setState({
      fromPickerVisible: open
    });
  }
  handleEndOpenChange = (endOpen) => {
    this.setState({
      toPickerVisible: endOpen
    });
  }
  handleOpenChange = (open) => {
    this.setState({rangeFilterVisible: open});
  }
  handleRangeFilterVisibility = (open) => {
    const {fromPickerVisible, toPickerVisible} = this.state;
    const {visibilityChanged} = this.props;
    if (open || (!fromPickerVisible && !toPickerVisible)) {
      this.setState({
        rangeFilterVisible: open
      }, () => {
        visibilityChanged && visibilityChanged(open);
      });
    }
  };

  handleRangeChange = () => {
    const {dateFrom, dateTo} = this.state;
    const {onChange} = this.props;
    onChange({
      from: dateFrom
        ? moment.utc(dateFrom).format(DATE_FORMAT)
        : undefined,
      to: dateTo
        ? moment.utc(dateTo).format(DATE_FORMAT)
        : undefined,
      ...(this.props.supportEmptyValue && {
        emptyValue: this.state.emptyValue
      })
    });
    this.handleRangeFilterVisibility(false);
  };

  resetRange = async () => {
    const {onChange} = this.props;
    this.setState({
      dateFrom: null,
      dateTo: null,
      emptyValue: false
    }, () => {
      onChange(null);
      this.handleRangeFilterVisibility(false);
    });
  };

  onChangeEmptyValue = (e) => {
    this.setState({
      emptyValue: e.target.checked
    });
  };

  render () {
    const content = (
      <div style={{display: 'flex', flexDirection: 'column', width: 280}}>
        {this.props.supportEmptyValue ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end'
            }}
          >
            <Checkbox
              checked={this.state.emptyValue}
              onChange={this.onChangeEmptyValue}
            >
              Empty dates
            </Checkbox>
          </div>) : null
        }
        <div style={{
          display: 'flex',
          justifyContent: 'flex-start',
          alignItems: 'center',
          marginTop: 5
        }}>
          <label
            htmlFor="from"
            style={{marginRight: 5, width: '15%', fontWeight: 800}}
          >
            From
          </label>
          <DatePicker
            style={{width: '85%'}}
            id="from"
            disabled={this.props.supportEmptyValue && this.state.emptyValue}
            disabledDate={this.disabledStartDate}
            placeholder=""
            format={DATE_DISPLAY_FORMAT}
            value={momentToDayjs(this.state.dateFrom) || null}
            onChange={this.onStartChange}
            onOpenChange={this.handleStartOpenChange}
          />
        </div>
        <div style={{
          display: 'flex',
          justifyContent: 'flex-start',
          alignItems: 'center',
          marginTop: 10,
          cursor: 'pointer'
        }}>
          <label
            htmlFor="to"
            style={{marginRight: 5, width: '15%', fontWeight: 800}}
          >
            To
          </label>
          <DatePicker
            style={{width: '85%'}}
            id="to"
            allowClear
            disabled={this.props.supportEmptyValue && this.state.emptyValue}
            disabledDate={this.disabledEndDate}
            placeholder=""
            format={DATE_DISPLAY_FORMAT}
            value={momentToDayjs(this.state.dateTo) || null}
            onChange={this.onEndChange}
            onOpenChange={this.handleEndOpenChange}
          />
        </div>
        <div
          style={{
            margin: '20px 0px 10px 0px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
          <Button
            danger
            onClick={() => this.resetRange()}
            disabled={this.resetDisabled}
          >
            Reset
          </Button>
          <Button
            type="primary"
            onClick={() => this.handleRangeChange()}
            disabled={!this.modified}
          >
            Apply
          </Button>
        </div>
      </div>
    );
    return (
      <Popover
        content={content}
        title={(
          <div
            style={{
              marginTop: 5,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer'
            }}>
            <h4>Select date range</h4>
            <CloseOutlined onClick={() => this.handleRangeFilterVisibility(false)} />
          </div>
        )}
        trigger={['click', 'mouseover']}
        open={this.state.rangeFilterVisible}
        onOpenChange={this.handleRangeFilterVisibility}
      >
        {this.props.children}
      </Popover>
    );
  }
}

RangeDatePicker.propTypes = {
  from: PropTypes.string,
  to: PropTypes.string,
  onChange: PropTypes.func,
  children: PropTypes.node,
  visibilityChanged: PropTypes.func,
  supportEmptyValue: PropTypes.bool,
  emptyValue: PropTypes.bool
};

export default RangeDatePicker;
