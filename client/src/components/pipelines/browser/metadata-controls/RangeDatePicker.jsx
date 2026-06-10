import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Checkbox, Popover, DatePicker, Button} from 'antd';
import {CloseOutlined} from '@ant-design/icons';
import dayjs, {
  calendarDayBoundary,
  localDateToUtcDayjs,
  utcStringToLocalDayjs,
} from '../../../../utils/dayjs';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
const DATE_DISPLAY_FORMAT = 'YYYY-MM-DD';

@observer
class RangeDatePicker extends React.Component {
  state = {
    dateFrom: undefined,
    dateTo: undefined,
    emptyValue: false,
    fromPickerVisible: false,
    toPickerVisible: false,
    rangeFilterVisible: false,
  };

  get modified() {
    const {from, to, emptyValue} = this.props;
    const {dateFrom, dateTo, emptyValue: emptyValueState} = this.state;
    const stateFrom = dateFrom ? localDateToUtcDayjs(dateFrom)?.format(DATE_FORMAT) : undefined;
    const stateTo = dateTo ? localDateToUtcDayjs(dateTo)?.format(DATE_FORMAT) : undefined;
    return from !== stateFrom || to !== stateTo || emptyValue !== emptyValueState;
  }

  get resetDisabled() {
    return (
      !this.props.from && !this.props.to && this.props.supportEmptyValue && !this.props.emptyValue
    );
  }

  componentDidMount() {
    this.updateValuesFromProps();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
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
      dateFrom: utcStringToLocalDayjs(this.props.from),
      dateTo: utcStringToLocalDayjs(this.props.to),
    });
  };

  disabledStartDate = (startValue) => {
    const endValue = this.state.dateTo;
    if (!startValue || !endValue) {
      return false;
    }
    return startValue.isAfter(endValue, 'day');
  };

  disabledEndDate = (endValue) => {
    const startValue = this.state.dateFrom;
    const today = dayjs().startOf('day');
    if (!startValue) {
      return endValue && endValue.isAfter(today, 'day');
    }
    if (!endValue) {
      return false;
    }
    return endValue.isBefore(startValue, 'day') || endValue.isAfter(today, 'day');
  };

  onStartChange = (value) => {
    this.setState({
      dateFrom: value ? calendarDayBoundary(value, false) : undefined,
    });
  };

  onEndChange = (value) => {
    this.setState({
      dateTo: value ? calendarDayBoundary(value, true) : undefined,
    });
  };

  handleStartOpenChange = (open) => {
    this.setState({
      fromPickerVisible: open,
    });
  };

  handleEndOpenChange = (endOpen) => {
    this.setState({
      toPickerVisible: endOpen,
    });
  };

  handleOpenChange = (open) => {
    this.setState({rangeFilterVisible: open});
  };

  handleRangeFilterVisibility = (open) => {
    const {fromPickerVisible, toPickerVisible} = this.state;
    const {visibilityChanged} = this.props;
    if (open || (!fromPickerVisible && !toPickerVisible)) {
      this.setState(
        {
          rangeFilterVisible: open,
        },
        () => {
          if (visibilityChanged) {
            visibilityChanged(open);
          }
        },
      );
    }
  };

  handleRangeChange = () => {
    const {dateFrom, dateTo} = this.state;
    const {onChange} = this.props;
    onChange({
      from: dateFrom ? localDateToUtcDayjs(dateFrom)?.format(DATE_FORMAT) : undefined,
      to: dateTo ? localDateToUtcDayjs(dateTo)?.format(DATE_FORMAT) : undefined,
      ...(this.props.supportEmptyValue && {
        emptyValue: this.state.emptyValue,
      }),
    });
    this.handleRangeFilterVisibility(false);
  };

  resetRange = async () => {
    const {onChange} = this.props;
    this.setState(
      {
        dateFrom: null,
        dateTo: null,
        emptyValue: false,
      },
      () => {
        onChange(null);
        this.handleRangeFilterVisibility(false);
      },
    );
  };

  onChangeEmptyValue = (e) => {
    this.setState({
      emptyValue: e.target.checked,
    });
  };

  render() {
    const content = (
      <div
        style={{display: 'flex', flexDirection: 'column', width: 280}}
        onClick={(event) => event.stopPropagation()}
      >
        {this.props.supportEmptyValue ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
            }}
          >
            <Checkbox checked={this.state.emptyValue} onChange={this.onChangeEmptyValue}>
              Empty dates
            </Checkbox>
          </div>
        ) : null}
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-start',
            alignItems: 'center',
            marginTop: 5,
          }}
        >
          <label htmlFor="from" style={{marginRight: 5, width: '15%', fontWeight: 800}}>
            From
          </label>
          <DatePicker
            style={{width: '85%'}}
            id="from"
            disabled={this.props.supportEmptyValue && this.state.emptyValue}
            disabledDate={this.disabledStartDate}
            placeholder=""
            format={DATE_DISPLAY_FORMAT}
            value={this.state.dateFrom || null}
            onChange={this.onStartChange}
            onOpenChange={this.handleStartOpenChange}
          />
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-start',
            alignItems: 'center',
            marginTop: 10,
            cursor: 'pointer',
          }}
        >
          <label htmlFor="to" style={{marginRight: 5, width: '15%', fontWeight: 800}}>
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
            value={this.state.dateTo || null}
            onChange={this.onEndChange}
            onOpenChange={this.handleEndOpenChange}
          />
        </div>
        <div
          style={{
            margin: '20px 0px 10px 0px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Button
            danger
            onClick={(event) => {
              event.stopPropagation();
              this.resetRange();
            }}
            disabled={this.resetDisabled}
          >
            Reset
          </Button>
          <Button
            type="primary"
            onClick={(event) => {
              event.stopPropagation();
              this.handleRangeChange();
            }}
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
        title={
          <div
            style={{
              marginTop: 5,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer',
            }}
            onClick={(event) => event.stopPropagation()}
          >
            <h4>Select date range</h4>
            <CloseOutlined onClick={() => this.handleRangeFilterVisibility(false)} />
          </div>
        }
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
  emptyValue: PropTypes.bool,
};

export default RangeDatePicker;
