import React, {useCallback, useEffect, useState} from 'react';
import classNames from 'classnames';
import Modal from './modal';
import {filterUserFn, filterRoleFn} from '../utilities/helpers';
import LoadingIndicator from './loading-indicator';
import {useUsersRoles} from '../utilities/users-roles-context';
import UserCard from './user-card';
import RoleCard from './role-card';
import Close from './close';
import './pick-up-users-modal.css';

const MAX_ITEMS_TO_SHOW = 25;

function PrincipalList (
  {
    list = [],
    placeholder,
    principalClassName,
    principalRenderer = (o) => o.name,
    isSelected = ((o) => false),
    onSelect = (o) => {},
    removeButton = false,
    showFirst
  }
) {
  if (list.length === 0) {
    return null;
  }
  return (
    <div className="selection">
      {
        placeholder && (
          <span className="placeholder">
            {placeholder}:
          </span>
        )
      }
      {
        list
          .slice(showFirst ? 0 : undefined, showFirst ? showFirst : undefined)
          .map(principal => (
            <div
              tabIndex={0}
              role="button"
              onClick={() => !removeButton || !isSelected(principal)
                ? onSelect(principal, !isSelected(principal))
                : {}
              }
              key={principal.name}
              className={
                classNames(
                  'selection-item',
                  principalClassName,
                  {
                    selected: isSelected(principal)
                  }
                )
              }
            >
              {principalRenderer(principal)}
              {
                removeButton && isSelected(principal) && (
                  <Close
                    className="user-clear-button"
                    onClick={() => onSelect(principal, false)}
                    bordered={false}
                  />
                )
              }
            </div>
          ))
      }
      {
        showFirst && list.length > showFirst && (
          <span
            className="show-more"
          >
            and {list.length - showFirst} more
          </span>
        )
      }
    </div>
  );
}

export default function PickUpUsersModal (
  {
    visible,
    onClose,
    onSelectUsers,
    selectedUsers: initialSelection = [],
    notSelectedText = ''
  }
) {
  const [selection, setSelection] = useState([]);
  const [filter, setFilter] = useState(undefined);
  useEffect(() => {
    setSelection((initialSelection || []).slice());
  }, [initialSelection, setSelection, visible]);
  const {
    users,
    roles,
    pending,
  } = useUsersRoles();
  const onFilterChange = useCallback((e) => {
    setFilter(e.target.value);
  }, [setFilter]);
  const selectedUsers = selection
    .filter(o => o.principal)
    .map(o => users.find(u => o.name === u.name))
    .filter(Boolean);
  const selectedRoles = selection
    .filter(o => !o.principal)
    .map(o => roles.find(u => o.name === u.name))
    .filter(Boolean);
  const selectedUserNames = new Set(selectedUsers.map(o => o.name));
  const selectedRoleNames = new Set(selectedRoles.map(o => o.name));
  const onSelect = useCallback((o, isSelected, principal) => {
    setSelection(s => s
      .filter(i => !(i.principal === principal && i.name === o.name))
      .concat(isSelected ? [{name: o.name, principal}] : [])
    );
  }, [setSelection, onSelectUsers]);
  const onSelectUser = useCallback((user, isSelected) => {
    onSelect(user, isSelected, true);
  }, [onSelect]);
  const onSelectRole = useCallback((role, isSelected) => {
    onSelect(role, isSelected, false);
  }, [onSelect]);
  const onClear = useCallback((e) => {
    if (e) {
      e.stopPropagation();
    }
    setSelection([]);
  }, [setSelection]);
  const onCloseModal = useCallback(() => {
    if (onClose) {
      onClose();
    }
    setFilter(undefined);
  }, [onClose, setFilter]);
  const onSave = useCallback((e) => {
    if (e) {
      e.stopPropagation();
    }
    if (onSelectUsers) {
      onSelectUsers(selection);
    }
    onCloseModal();
  }, [selection, onSelectUsers, onCloseModal]);
  if (pending) {
    return (
      <Modal
        visible={visible}
        onClose={onCloseModal}
        title="Select users or roles"
        className="pick-up-users-modal"
        closeButton
      >
        <LoadingIndicator />
      </Modal>
    );
  }
  return (
    <Modal
      visible={visible}
      onClose={onCloseModal}
      title="Select users or roles"
      className="pick-up-users-modal"
      closeButton
    >
      <div className="users-filter">
        <input
          className="users-filter-input"
          value={filter || ''}
          onChange={onFilterChange}
          placeholder="Search users or roles"
        />
      </div>
      <div className="pick-up-users-modal-content">
        {
          !(filter && filter.length) && selection.length === 0 && notSelectedText && (
            <div className="empty-selection">
              {notSelectedText}
            </div>
          )
        }
        {
          filter &&
          filter.length &&
          users.filter(filterUserFn(filter)).length === 0 &&
          roles.filter(filterRoleFn(filter)).length === 0 && (
            <div className="empty-selection">
              Nothing found
            </div>
          )
        }
        <PrincipalList
          list={
            (filter && filter.length)
              ? users.filter(filterUserFn(filter))
              : selectedUsers
          }
          principalClassName="user"
          placeholder="Users"
          isSelected={o => selectedUserNames.has(o.name)}
          onSelect={onSelectUser}
          principalRenderer={(user) => (
            <UserCard userName={user.name} />
          )}
          removeButton={!filter || !filter.length}
          showFirst={filter && filter.length ? MAX_ITEMS_TO_SHOW : undefined}
        />
        <PrincipalList
          list={
            (filter && filter.length)
              ? roles.filter(filterRoleFn(filter))
              : selectedRoles
          }
          principalClassName="role"
          placeholder="Roles"
          isSelected={o => selectedRoleNames.has(o.name)}
          onSelect={onSelectRole}
          principalRenderer={role => (<RoleCard roleName={role.name} /> )}
          removeButton={!filter || !filter.length}
          showFirst={filter && filter.length ? MAX_ITEMS_TO_SHOW : undefined}
        />
      </div>
      <div className="pick-up-users-modal-actions">
        <div
          className="pick-up-users-modal-action"
          onClick={onCloseModal}
        >
          CANCEL
        </div>
        <div style={{display: 'inline-flex'}}>
          {
            selection.length > 0 && (
              <div
                className={
                  classNames(
                    'pick-up-users-modal-action',
                    'primary'
                  )
                }
                onClick={onClear}
                style={{
                  marginRight: 5
                }}
              >
                CLEAR
              </div>
            )
          }
          <div
            className={
              classNames(
                'pick-up-users-modal-action',
                'primary'
              )
            }
            onClick={onSave}
          >
            SAVE
          </div>
        </div>
      </div>
    </Modal>
  );
}
