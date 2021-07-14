import React from 'react';
import PropTypes from 'prop-types';

import AWSRegionTag from '../../special/AWSRegionTag';
import {
  Dropdown,
  Menu,
  Icon
} from 'antd';

export function MultizoneUrl (props) {
  const {regions, defaultRegion, title} = props;
  const fallbackRegion = Object.keys(regions)[0];
  const menu = (
    <Menu style={{width: '150px'}}>
      { regions && Object.keys(regions).map((key) => (
        <Menu.Item key={key} style={{display: 'flex'}}>
          <AWSRegionTag
            style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
            regionUID={key} />
          <a href={regions[key]}>{key}</a>
        </Menu.Item>))
      }
    </Menu>
  );
  if (regions) {
    return (<div
      style={{
        width: '100%',
        padding: 3,
        margin: 0,
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
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
          href={defaultRegion ? regions[defaultRegion] : regions[fallbackRegion]}
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
          regionUID={defaultRegion || fallbackRegion}
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
  return null;
}
MultizoneUrl.propTypes = {
  regions: PropTypes.object,
  defaultRegion: PropTypes.string,
  title: PropTypes.string
};
