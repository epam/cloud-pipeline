import {CommonProps} from '../../../@types/common.ts';
import {useQuery} from '@tanstack/react-query';
import {usersInfoQueryOptions} from '../../../queries';
import {useMemo} from 'react';
import classNames from 'classnames';
import {UserOutlined} from '@ant-design/icons';
import {Tooltip} from 'antd';
import {getUserDisplayName} from './utilities.ts';
import {TooltipPlacement} from 'antd/es/tooltip';

function UserName(
  props: CommonProps & {
    userName?: string;
    showIcon?: boolean;
    tooltipPlacement?: TooltipPlacement;
  },
) {
  const {className, style, userName, showIcon, tooltipPlacement} = props;
  const {data: users = []} = useQuery(usersInfoQueryOptions());
  const user = useMemo(
    () =>
      userName ? users.find((o) => o.userName.toLowerCase() === userName.toLowerCase()) : undefined,
    [userName, users],
  );
  const attributes = useMemo(() => Object.values(user?.attributes ?? {}).join(', '), [user]);
  if (!userName) return null;
  const details = (
    <div>
      <div>{userName.toLowerCase()}</div>
      <div>{attributes}</div>
    </div>
  );
  const displayName = user ? getUserDisplayName(user) : userName;
  return (
    <Tooltip placement={tooltipPlacement} title={details} trigger="hover">
      <div className={classNames(className, 'inline-block', 'cursor-default')} style={style}>
        {showIcon && <UserOutlined />}
        <span>{displayName}</span>
      </div>
    </Tooltip>
  );
}

export {UserName};
