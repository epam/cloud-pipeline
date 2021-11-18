import React from 'react';
import classNames from 'classnames';
import {useUsersRoles} from '../utilities/users-roles-context';
import UserAttributes from './user-attributes';
import './user-card.css';

export default function UserCard(
  {
    userName,
    className,
    attributeClassName,
    small = false,
    style = {}
  }
) {
  const {
    users
  } = useUsersRoles();
  const user = (users || []).find(u => u.name === userName);
  if (!user) {
    return (
      <div
        className={
          classNames(
            'user-card',
            className
          )
        }
        style={small ? {fontSize: 'smaller', ...style} : style}
      >
        <span
          className={
            classNames(
              'user-card-attribute',
              attributeClassName
            )
          }
        >
          {userName}
        </span>
      </div>
    );
  }
  return (
    <UserAttributes
      user={user}
      className={
        classNames(
          'user-card',
          className
        )
      }
      attributeClassName={
        classNames(
          'user-card-attribute',
          attributeClassName
        )
      }
      skip={['email', 'e-mail']}
      style={small ? {fontSize: 'smaller', ...style} : style}
    />
  )
}
