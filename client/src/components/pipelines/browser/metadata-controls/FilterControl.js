import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Popover, Button, Icon, Select} from 'antd';

const Option = Select.Option;

@observer
class FilterControl extends React.PureComponent {
  state = {
    tags: [],
    selectedTags: [],
    popoverVisible: false
  }
  static propTypes = {
    columnName: PropTypes.string,
    onSearch: PropTypes.func,
    children: PropTypes.node,
    value: PropTypes.arrayOf(PropTypes.string)
  }
  componentDidMount () {
    this.updateStateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.updateStateFromProps();
    }
  }
  updateStateFromProps = () => {
    const {value = []} = this.props;
    this.setState({selectedTags: value});
  };

  getContainer = (triggernode) => {
    return triggernode.parentNode;
  };
  resetFilter = () => {
    this.setState({
      selectedTags: []
    });
    this.props.onSearch(null);
    this.handlePopoverVisibleChange(false);
  }
  handleInputConfirm = async (value) => {
    await this.setState({
      selectedTags: value,
      popoverVisible: true
    });
  }
  handleApplyFilter = () => {
    this.props.onSearch(this.state.selectedTags);
    this.handlePopoverVisibleChange(false);
  }
  handlePopoverVisibleChange = (visible) => {
    this.setState({
      popoverVisible: visible
    });
  }
  render () {
    const {tags, selectedTags, popoverVisible} = this.state;
    const content = (
      <div style={{width: 280, padding: '8px 0px'}}>
        <div style={{width: 280, display: 'flex', alignItems: 'center'}}>
          <Select
            value={selectedTags}
            mode="tags"
            style={{width: 280}}
            placeholder="Type or select tags"
            notFoundContent="Specify tags to filter"
            onChange={this.handleInputConfirm}
            getPopupContainer={this.getContainer}
          >
            {tags.map((tag, index) => (
              <Option
                key={tag + index}
                value={tag}
              >{tag}</Option>
            ))}
          </Select>
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
            disabled={!selectedTags.length}
          >Reset</Button>
          <Button
            type="primary"
            onClick={this.handleApplyFilter}
            disabled={!selectedTags.length}
          >Apply</Button>
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
            <h4>Add new tag for search</h4>
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
