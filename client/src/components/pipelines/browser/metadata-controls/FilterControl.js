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
      <div style={{maxWidth: 300, padding: 16}}>
        <div style={{width: '100%', display: 'flex', alignItems: 'center'}}>
          <Select
            value={selectedTags}
            mode="tags"
            style={{width: '100%'}}
            placeholder="Type or select tags"
            onChange={this.handleInputConfirm}
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
            type="primary"
            onClick={this.handleApplyFilter}
            disabled={!selectedTags.length}
          >Apply</Button>
          <Button
            type="danger"
            onClick={this.resetFilter}
            disabled={!selectedTags.length}
          >Reset</Button>
        </div>
      </div>);
    return (
      <Popover
        placement="bottom"
        title={(
          <div
            style={{
              width: 300,
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
        trigger={['focus', 'click']}
        visible={popoverVisible}
        onVisibleChange={this.handlePopoverVisibleChange}
      >
        <Icon
          type="filter"
          style={{
            pointerEvents: 'auto',
            color: selectedTags.length ? '#108ee9' : 'grey',
            zIndex: 1000
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
