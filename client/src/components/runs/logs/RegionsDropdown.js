import React from 'react';
import PropTypes from 'prop-types';

import AWSRegionTag from '../../special/AWSRegionTag';
import {
  Dropdown,
  Menu,
  Icon
} from 'antd';

const REGION_ID = 1;

export class RegionsDropdown extends React.PureComponent {
  state = {
    selectedRegion: this.props.defaultRegion,
    title: this.props.title
  }
  static propTypes = {
    regions: PropTypes.object,
    defaultRegion: PropTypes.object,
    title: PropTypes.string,
    onSelect: PropTypes.func
  }

  componentDidMount () {
    console.log(this.props);
    this.updateStateFromProps();
  }
  updateStateFromProps = () => {
    const {regions = [], defaultRegion, title} = this.props;
    this.setState({
      regions,
      selectedRegion: defaultRegion,
      title
    });
  }

  handleClickItem = ({key}) => {
    const selectedLink = this.state.regions.get(key);
    this.setState({
      selectedRegion: {[key]: selectedLink}
    });
    this.props.onSelect({[key]: selectedLink});
  }
  render () {
    const {regions} = this.state;
    const menu = (
      <Menu onClick={this.handleClickItem} style={{width: '150px'}}>
        { regions && regions.entries().map((region) => (
          <Menu.Item key={region[0]}>
            <AWSRegionTag
              style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
              regionId={REGION_ID} />
            {region[0]}
          </Menu.Item>))
        }
      </Menu>
    );
    return (
      <div
        style={{
          width: '100px',
          marginLeft: 5,
          padding: 0,
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center'}}
      >
        <div style={{display: 'flex', justifyContent: 'flex-ends', alignItems: 'center'}}>
          <a
            href={Object.values(this.state.selectedRegion)[0]}
            style={{display: 'inline-block', width: '90%', textAlign: 'right'}}
          >
            {this.props.title}
          </a>
          <AWSRegionTag
            style={{verticalAlign: 'top', marginLeft: 3, fontSize: 'larger'}}
            regionId={1}
          />
        </div>
        <Dropdown
          trigger={['click']}
          overlay={menu}
          placement="bottomRight"
          style={{width: '150px', display: 'flex', alignItems: 'center'}}
        >
          <Icon type="down" style={{color: '#108ee9', marginLeft: 5}} />
        </Dropdown>
      </div>
    );
  }
}
