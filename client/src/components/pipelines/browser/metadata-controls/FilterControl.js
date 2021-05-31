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
    onSearch: PropTypes.func
  }
  resetFilter = () => {
    this.setState({
      selectedTags: []
    });
    this.props.onSearch([]);
  }
  handleInputConfirm = (value) => {
    this.setState({
      popoverVisible: true,
      selectedTags: value
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
      <div style={{maxWidth: 250}}>
        <Select
          value={selectedTags}
          mode="tags"
          style={{width: '100%'}}
          placeholder="Please select"
          onChange={this.handleInputConfirm}
        >
          {tags.map((tag, index) => (
            <Option
              key={tag + index}
              value={tag}
            >{tag}</Option>
          ))}
        </Select>
        <div style={{marginTop: 10, display: 'flex', justifyContent: 'space-between'}}>
          <Button
            type="danger"
            onClick={this.resetFilter}
            style={{
              visibility: !selectedTags.length ? 'hidden' : 'visible',
              marginRight: 5}}
          >Reset</Button>
          <Button
            type="primary"
            onClick={this.handleApplyFilter}
            disabled={!selectedTags.length}
          >Apply filter</Button>
        </div>
      </div>);
    return (
      <Popover
        placement="bottom"
        title={(
          <div
            style={{
              maxWidth: 300,
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
        trigger={'click'}
        visible={popoverVisible}
      >
        <Icon
          type="filter"
          style={{
            pointerEvents: 'auto',
            color: selectedTags.length ? '#108ee9' : 'grey'
          }}
          onClick={() => {
            this.setState({
              popoverVisible: true
            });
          }}
        />
      </Popover>
    );
  }
}
export default FilterControl;
