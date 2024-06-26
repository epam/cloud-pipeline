import React, {useCallback, useEffect, useState} from 'react';
import classNames from 'classnames';
import Modal from './modal';
import useAuthenticatedUser from '../utilities/user-authenticated-user';
import { useApplicationTypeSettings } from '../use-settings';
import getUsersInfo from '../../models/cloud-pipeline-api/get-users-info';
import useFolderApplications from '../utilities/use-folder-applications';
import FolderApplicationCard from '../folder-application-card';
import useAdvancedUser from '../utilities/use-advanced-user';
import filterAppFn from '../utilities/filter-applications-fn';
import UserAttributes from './user-attributes';
import {filterUserFn} from '../utilities/helpers';
import './pick-up-folder-application-modal.css';

export default function PickUpFolderApplicationModal (
  {
    appType,
    allApplications,
    visible,
    onClose,
    onSelectApplication,
    ignoredApplications = []
  }
) {
  const {
    authenticatedUser,
    pending: authenticating,
    error: authenticationError
  } = useAuthenticatedUser();
  const settings = useApplicationTypeSettings(allApplications ? undefined : appType);
  const [user, setUser] = useState(undefined);
  const [users, setUsers] = useState([]);
  const [filter, setFilter] = useState(undefined);
  const [appsFilter, setAppsFilter] = useState(undefined);
  const [pending, setPending] = useState(true);
  const onFilterChange = useCallback((e) => setFilter(e.target.value), [setFilter]);
  const onAppsFilterChange = useCallback((e) => setAppsFilter(e.target.value), [setAppsFilter]);
  const {
    canEditPublishedApps: userSelectionAvailable
  } = useAdvancedUser(settings, authenticatedUser);
  useEffect(() => {
    if (authenticatedUser && visible) {
      setPending(true);
      if (userSelectionAvailable) {
        getUsersInfo()
          .then((users) => {
            setUsers(users);
          })
          .catch(e => {
            console.error(e.message);
            setUser(authenticatedUser);
          })
          .then(() => {
            setPending(false);
          })
      } else {
        setUser(authenticatedUser);
        setPending(false);
      }
    }
  }, [
    authenticatedUser,
    userSelectionAvailable,
    setUser,
    settings,
    setPending,
    visible,
    setUsers
  ]);
  const selectUser = useCallback((selectedUser) => {
    setUser(selectedUser ? {...selectedUser, userName: selectedUser.name} : undefined);
    setFilter(undefined);
    setAppsFilter(undefined);
  }, [setUser, setFilter, setAppsFilter]);
  useEffect(() => {
    if (!visible && userSelectionAvailable) {
      selectUser(undefined);
    }
  }, [visible, userSelectionAvailable, selectUser]);
  const [options, setOptions] = useState({});
  useEffect(() => {
    if (settings) {
      setOptions(settings.parseUrl(location.href));
    }
  }, [location.href, settings, setOptions]);
  const {
    applications: allApps,
    pending: applicationsPending
  } = useFolderApplications(options, user);
  const applications = allApplications
    ? allApps
    : allApps.filter(o => o.appType === appType);
  return (
    <Modal
      visible={visible}
      onClose={onClose}
      title={
        pending
          ? false
          : (user ? `Select ${user.userName}'s application` : 'Select user')
      }
      className="pick-up-application-modal"
      closeButton
    >
      {
        authenticationError && (
          <div className="authentication-error">
            {authenticationError}
          </div>
        )
      }
      {
        (authenticating || pending) && (
          <div className="pick-up-loading">
            Loading...
          </div>
        )
      }
      {
        !authenticationError && !authenticating && !pending && !user && (
          <div>
            <div className="filter">
              <input
                className="filter-input"
                value={filter || ''}
                onChange={onFilterChange}
              />
            </div>
            <div className="users">
              {
                (users || [])
                  .filter(filterUserFn(filter))
                  .map(filteredUser => (
                    <div
                      key={filteredUser.name}
                      className={
                        classNames(
                          'user',
                          {
                            current: filteredUser.name === authenticatedUser?.userName
                          }
                        )
                      }
                      onClick={() => selectUser(filteredUser)}
                    >
                      <div>{filteredUser.name}</div>
                      <UserAttributes
                        user={filteredUser}
                        className="attributes"
                        attributeClassName="user-attribute"
                      />
                    </div>
                  ))
              }
            </div>
          </div>
        )
      }
      {
        !authenticationError && !authenticating && !pending && !!user && applicationsPending && (
          <div className="pick-up-loading">
            Loading...
          </div>
        )
      }
      {
        !authenticationError && !authenticating && !pending && !!user && !applicationsPending && (
          <div>
            <div className="filter">
              {
                userSelectionAvailable && (
                  <div
                    className="back-to-users"
                    onClick={() => selectUser(undefined)}
                  >
                    {'< BACK'}
                  </div>
                )
              }
              <input
                className="filter-input"
                value={appsFilter || ''}
                onChange={onAppsFilterChange}
              />
            </div>
            {
              applications.length === 0 && (
                <div className="pick-up-loading">
                  Applications not found
                </div>
              )
            }
            <div className="pick-up-applications">
              {
                applications
                  .filter(filterAppFn(appsFilter))
                  .map(application => (
                    <FolderApplicationCard
                      className={
                        classNames(
                          'pick-up-application',
                          {ignored: ignoredApplications.includes(application.id)}
                        )
                      }
                      disabled={ignoredApplications.includes(application.id)}
                      key={application.id}
                      application={application}
                      onClick={
                        ignoredApplications.includes(application.id)
                          ? undefined
                          : onSelectApplication
                      }
                      displayFavourite={false}
                    />
                  ))
              }
            </div>
          </div>
        )
      }
    </Modal>
  );
}
