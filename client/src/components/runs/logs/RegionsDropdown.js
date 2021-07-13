import React from 'react';
import PropTypes from 'prop-types';

import AWSRegionTag from '../../special/AWSRegionTag';
import {
  Dropdown,
  Menu,
  Icon
} from 'antd';

export function RegionsDropdown (props) {
  const {regions, defaultRegion, title, run} = props;
  const menu = (
    <Menu style={{width: '150px'}}>
      { regions && Object.keys(regions).map((key) => (
        <Menu.Item key={key} style={{display: 'flex'}}>
          <AWSRegionTag
            style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
            regionId={run.cloudRegionId} />
          <a href={regions[key]}>{key}</a>
        </Menu.Item>))
      }
    </Menu>
  );
  return (<div
    style={{
      width: '100%',
      padding: 3,
      margin: 0,
      display: 'flex',
      justifyContent: 'flex-start',
      alignItems: 'center',
      border: '0.5px solid rgba(16,142,233,0.1)',
      boxSizing: 'border-box'
    }}
  >
    <div style={{
      width: '100%',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center'
    }}>
      <a
        href={regions && defaultRegion ? regions[defaultRegion] : ''}
        style={{
          display: 'block',
          width: '100%',
          height: '20px',
          overflowWrap: 'normal',
          lineHeight: '20px',
          textAlign: 'left'
        }}
      >
        {title}
      </a>
      <AWSRegionTag
        style={{display: 'block', verticalAlign: 'top', marginLeft: 3, fontSize: 'larger'}}
        regionId={run.cloudRegionId}
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
  </div>);
}
RegionsDropdown.propTypes = {
  regions: PropTypes.object,
  defaultRegion: PropTypes.string,
  title: PropTypes.string,
  run: PropTypes.object
};
