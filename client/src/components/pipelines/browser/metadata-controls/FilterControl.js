import React from 'react';
import PropTypes, {object} from 'prop-types';
import {observer} from 'mobx-react';
import {Popover, Button, Icon, Select} from 'antd';

const Option = Select.Option;

@observer
class FilterControl extends React.PureComponent {

  async componentDidMount () {
    const tags = [];
    await setTimeout(() => {
      const {columnName, list} = this.props;
      for (let i = 0; i < list.length; i++) {
        if (list[i][columnName] && list[i][columnName].value) {
          const currentValue = list[i][columnName].value;
          if (!this.state.tags.includes(currentValue)) {
            tags.push(currentValue);
          }
        }
      }
    }, 1000);
    this.setState({tags});
  }
  state = {
    tags: [],
    selectedTags: [],
    popoverVisible: false
  }
  static propTypes = {
    list: PropTypes.arrayOf(object),
    columnName: PropTypes.string,
    onSearch: PropTypes.func
  }
  clearAll = () => {
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
    this.props.onSearch(value);
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
          mode="multiple"
          style={{width: '100%'}}
          placeholder="Please select"
          onChange={this.handleInputConfirm}
        >
          {tags.length > 0 && (
            tags.map((tag, index) => (
              <Option
                key={tag + index}
                value={tag}
              >{tag}</Option>
            ))
          )}
        </Select>
        {selectedTags.length >= 3 && (<div style={{marginTop: 10}}><Button onClick={this.clearAll}>Clear all</Button></div>)}
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
