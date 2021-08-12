import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Popover, Button, Icon, Select} from 'antd';

function tagsAreEqual (tagsA, tagsB) {
  if (!tagsA && !tagsB) {
    return true;
  }
  if (!tagsA || !tagsB) {
    return false;
  }
  if (tagsA.length !== tagsB.length) {
    return false;
  }
  for (let i = 0; i < tagsA.length; i++) {
    if (tagsA[i] !== tagsB[i]) {
      return false;
    }
  }
  return true;
}

@observer
class FilterControl extends React.PureComponent {
  state = {
    selectedTags: [],
    popoverVisible: false
  }
  static propTypes = {
    columnName: PropTypes.string,
    onSearch: PropTypes.func,
    children: PropTypes.node,
    value: PropTypes.arrayOf(PropTypes.string),
    visibilityChanged: PropTypes.func
  }

  get modified () {
    return !tagsAreEqual(this.props.value || [], this.state.selectedTags || []);
  }

  componentDidMount () {
    this.updateStateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!tagsAreEqual(prevProps.value || [], this.props.value || [])) {
      this.updateStateFromProps();
    }
  }
  updateStateFromProps = () => {
    const {value = []} = this.props;
    this.setState({selectedTags: value});
  };

  resetFilter = () => {
    this.setState({
      selectedTags: []
    });
    this.props.onSearch(null);
    this.handlePopoverVisibleChange(false);
  }
  onChange = async (value) => {
    await this.setState({
      selectedTags: value
    });
  }
  handleApplyFilter = () => {
    const {
      selectedTags = []
    } = this.state;
    const {
      onSearch
    } = this.props;
    if (selectedTags.length === 0) {
      onSearch && onSearch(null);
    } else {
      onSearch && onSearch(selectedTags);
    }
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
    const {value = []} = this.props;
    const {selectedTags, popoverVisible} = this.state;
    const content = (
      <div style={{width: 280, padding: '8px 0px'}}>
        <div style={{width: 280, display: 'flex', alignItems: 'center'}}>
          <Select
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
            disabled={value.length === 0}
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
