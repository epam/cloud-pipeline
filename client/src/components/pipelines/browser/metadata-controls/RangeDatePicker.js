import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Popover, Icon, DatePicker, Button} from 'antd';
import moment from 'moment';

const FULL_DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';
const currentDate = toLocalMomentDate(moment().toDate());

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
  componentDidMount () {
    setTimeout(() => {
      this.setState({
        dateFrom: toLocalMomentDate(this.props.from),
        dateTo: toLocalMomentDate(this.props.to)
      });
    }, 500);
  }
    static propTypes = {
      from: PropTypes.string,
      to: PropTypes.string,
      onChange: PropTypes.func
    }
    state = {
      dateFrom: toLocalMomentDate(this.props.from),
      dateTo: toLocalMomentDate(this.props.to),
      visible: false,
      fromPickerVisible: false,
      toPickerVisible: false,
      rangeFilterVisible: false
    }

    disabledStartDate = (startValue) => {
      const endValue = this.state.dateTo;
      if (!startValue || !endValue) {
        return false;
      }
      return (
        startValue > endValue
      );
    }
    disabledEndDate = (endValue) => {
      const startValue = this.state.dateFrom;
      if (!startValue) {
        return endValue > toLocalMomentDate(moment().toDate());
      } else if (!endValue) {
        return false;
      }
      return (
        endValue < startValue ||
        endValue > currentDate
      );
    }
    onChange = (field, value) => {
      this.setState({
        [field]: value
      });
    }
    onStartChange = (value) => {
      this.onChange('dateFrom', value);
    }
    onEndChange = (value) => {
      this.onChange('dateTo', value);
    }
    handleStartOpenChange= (open) => {
      this.setState({
        fromPickerVisible: open
      });
    }
    handleEndOpenChange = (endOpen) => {
      this.setState({
        toPickerVisible: endOpen
      });
    }
    handleVisibleChange = (visible) => {
      this.setState({visible});
    }
    handleRangeFilterVisibility = (visible) => {
      const {fromPickerVisible, toPickerVisible} = this.state;
      if (visible || (!fromPickerVisible && !toPickerVisible)) {
        this.setState({
          rangeFilterVisible: visible
        });
      }
    };

    handleRangeChange = () => {
      const {dateFrom, dateTo} = this.state;
      const {onChange} = this.props;
      onChange({
        from: dateFrom
          ? moment.utc(dateFrom).format(FULL_DATE_FORMAT)
          : undefined,
        to: dateTo
          ? moment.utc(dateTo).format(FULL_DATE_FORMAT)
          : undefined
      });
      this.handleRangeFilterVisibility(false);
    };
    resetRange = async () => {
      const {onChange} = this.props;
      await this.setState({
        dateFrom: null,
        dateTo: null
      });
      onChange(null);
      this.handleRangeFilterVisibility(false);
    }
    render () {
      if (this.props.from !== undefined && this.props.to !== undefined) {
        const content = (
          <div style={{display: 'flex', flexDirection: 'column'}}>
            <div style={{display: 'flex', flexDirection: 'column', marginTop: 5}}>
              <label htmlFor="from" style={{marginRight: 5, fontWeight: 800}}>From</label>
              <DatePicker
                id="from"
                disabledDate={this.disabledStartDate}
                format={DATE_FORMAT}
                showTime
                value={this.state.dateFrom || null}
                placeholder="from"
                onChange={this.onStartChange}
                onOpenChange={this.handleStartOpenChange}
              />
            </div>
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              marginTop: 5,
              cursor: 'pointer'
            }}>
              <label htmlFor="to" style={{marginRight: 5, fontWeight: 800}}>To</label>
              <DatePicker
                id="to"
                allowClear
                disabledDate={this.disabledEndDate}
                showTime
                format={DATE_FORMAT}
                value={this.state.dateTo || null}
                placeholder="to"
                onChange={this.onEndChange}
                onOpenChange={this.handleEndOpenChange}
              />
            </div>
            <div
              style={{
                marginTop: 10,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}>
              <Button
                type="danger"
                onClick={() => this.resetRange()}>
                Reset
              </Button>
              <Button
                type="primary"
                onClick={() => this.handleRangeChange()}
                disabled={!this.state.dateFrom && !this.state.dateTo}
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
                <Icon type="close" onClick={() => this.handleRangeFilterVisibility(false)} />
              </div>
            )}
            trigger={['click', 'mouseover']}
            visible={this.state.rangeFilterVisible}
            onVisibleChange={this.handleVisibleChange}
          >
            <Icon
              style={{
                pointerEvents: 'auto'
              }}
              type="filter"
              onClick={() => {
                this.setState({
                  rangeFilterVisible: true
                });
              }}
            />
          </Popover>);
      } else {
        return null;
      }
    }
}
export default RangeDatePicker;
