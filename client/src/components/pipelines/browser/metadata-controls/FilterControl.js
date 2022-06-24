import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Popover, Button, Icon, Select, Checkbox} from 'antd';

function tagsAreEqual (tagsA, tagsB) {
  if (!tagsA && !tagsB) {
    return true;
  }
  if (!tagsA || !tagsB) {
    return false;
  }
  const a = [...new Set(tagsA)].sort();
  const b = [...new Set(tagsB)].sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

@observer
class FilterControl extends React.PureComponent {
  state = {
    selectedTags: [],
    emptyValue: false,
    popoverVisible: false
  }
  static propTypes = {
    columnName: PropTypes.string,
    onSearch: PropTypes.func,
    children: PropTypes.node,
    value: PropTypes.arrayOf(PropTypes.string),
    visibilityChanged: PropTypes.func,
    supportEmptyValue: PropTypes.bool
  }
  static defaultProps = {
    supportEmptyValue: true
  };

  get selectedValues () {
    if (this.state.emptyValue) {
      return [];
    }
    const result = this.state.selectedTags || [];
    return result.length === 0 ? null : result;
  }

  get modified () {
    return !tagsAreEqual(this.props.value, this.selectedValues);
  }

  componentDidMount () {
    this.updateStateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!tagsAreEqual(prevProps.value, this.props.value)) {
      this.updateStateFromProps();
    }
  }
  updateStateFromProps = () => {
    const {value} = this.props;
    const empty = value && value.length === 0;
    this.setState({
      selectedTags: empty ? [] : (value || []).filter(o => o.length),
      emptyValue: empty
    });
  };

  resetFilter = () => {
    this.setState({
      selectedTags: [],
      emptyValue: false
    });
    this.props.onSearch(null);
    this.handlePopoverVisibleChange(false);
  }
  onChange = (value) => {
    this.setState({
      selectedTags: value
    });
  };
  onChangeEmptyValue = (e) => {
    this.setState({
      emptyValue: e.target.checked
    });
  };
  handleApplyFilter = () => {
    const {
      onSearch
    } = this.props;
    const result = this.selectedValues;
    onSearch && onSearch(result);
    this.handlePopoverVisibleChange(false);
  }
  handlePopoverVisibleChange = (visible) => {
    const {visibilityChanged} = this.props;
    this.setState({
      popoverVisible: visible
    }, () => {
      visibilityChanged && visibilityChanged(visible);
    });
  }
  render () {
    const {value, supportEmptyValue} = this.props;
    const {
      selectedTags = [],
      popoverVisible,
      emptyValue
    } = this.state;
    const content = (
      <div style={{width: 280, padding: '8px 0px'}}>
        {
          supportEmptyValue && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                marginBottom: 8
              }}
            >
              <Checkbox
                checked={emptyValue}
                onChange={this.onChangeEmptyValue}
              >
                Empty
              </Checkbox>
            </div>
          )
        }
        <div style={{width: 280, display: 'flex', alignItems: 'center'}}>
          <Select
            disabled={emptyValue}
            value={selectedTags}
            mode="tags"
            style={{width: 280}}
            placeholder="Type filter and press enter"
            dropdownStyle={{display: 'none'}}
            onChange={this.onChange}
            getPopupContainer={triggerNode => triggerNode.parentNode}
          />
        </div>
        <div style={{
          width: '100%',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: 10
        }}>
          <Button
            type="danger"
            onClick={this.resetFilter}
            disabled={!value}
          >
            Reset
          </Button>
          <Button
            type="primary"
            onClick={this.handleApplyFilter}
            disabled={!this.modified}
          >
            Apply
          </Button>
        </div>
      </div>);
    return (
      <Popover
        placement="bottom"
        title={(
          <div
            style={{
              width: 280,
              marginTop: 5,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer'
            }}>
            <h4>Specify filter for <span style={{fontWeight: 600}}>{this.props.columnName}</span>
            </h4>
            <Icon type="close" onClick={() => this.handlePopoverVisibleChange(false)} />
          </div>
        )}
        content={content}
        trigger={['click']}
        visible={popoverVisible}
        onVisibleChange={this.handlePopoverVisibleChange}
      >
        {this.props.children}
      </Popover>
    );
  }
}
export default FilterControl;
