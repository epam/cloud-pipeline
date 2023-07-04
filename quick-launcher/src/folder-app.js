import React, { useCallback, useEffect, useMemo, useState } from 'react';
import classNames from 'classnames';
import useAdvancedUser from './components/utilities/use-advanced-user';
import {useSettings, SettingsContext} from './components/use-settings';
import useAuthenticatedUser from './components/utilities/user-authenticated-user';
import useFolderApplications from './components/utilities/use-folder-applications';
import LoadingIndicator from './components/shared/loading-indicator';
import FolderApplicationCard from './components/folder-application-card';
import EditFolderApplication from './edit-folder-application';
import PickUpFolderApplicationModal from './components/shared/pick-up-folder-application-modal';
import Modal from './components/shared/modal';
import filterAppFn from './components/utilities/filter-applications-fn';
import useFavouriteApplications, {Modes} from './components/utilities/use-favourite-applications';
import LaunchFolderApplication from './components/shared/launch-folder-application';
import Help from './components/shared/help';
import {
  FAValidationSessionsContext,
  useSessions
} from './models/validate-folder-application';
import './components/components.css';
import './app.css';
import applicationAvailable from "./models/folder-applications/application-available";

function FolderApp ({location}) {
  const settings = useSettings();
  const sessions = useSessions(settings);
  const [showDeprecated, setShowDeprecated] = useState(false);
  const onChangeShowDeprecated = useCallback((event) => {
    setShowDeprecated(event.target.checked);
  }, [setShowDeprecated]);
  const onToggleDeprecated = useCallback(() => {
    setShowDeprecated((current) => !current);
  }, [setShowDeprecated]);
  const {
    authenticatedUser,
    pending: authenticating,
    error: authenticationError
  } = useAuthenticatedUser();
  const {
    canPublishApps,
    canEditPublishedApps
  } = useAdvancedUser(settings, authenticatedUser);
  const [options, setOptions] = useState({});
  const [selectApplication, setSelectApplication] = useState(false);
  const [launchApplication, setLaunchApplication] = useState(false)
  const onApplicationLaunchCancelled = useCallback(() => setLaunchApplication(false), [setLaunchApplication]);
  const onPublishClick = useCallback(() => setSelectApplication(true), [setSelectApplication]);
  const onClosePickUpApplication = useCallback(() => setSelectApplication(false), [setSelectApplication]);
  useEffect(() => {
    if (settings) {
      setOptions(settings.parseUrl(location.href));
    }
  }, [location.href, settings, setOptions]);
  const {
    applications,
    pending,
    reload
  } = useFolderApplications(options);
  const applicationTypes = useMemo(
    () => [...new Set((applications || []).map((o) => o.appType))].filter(Boolean).sort(),
    [applications]
  );
  const [filterAppType, setFilterAppType] = useState(undefined);
  const onChangeFilterAppType = useCallback((newFilterAppType) => {
    setFilterAppType((current) => (current === newFilterAppType ? undefined : newFilterAppType));
  }, [setFilterAppType]);
  const availableApplications = (applications || [])
    .filter(app => applicationAvailable(app.info, authenticatedUser, settings));
  const [application, setApplication] = useState(undefined);
  const [filter, setFilter] = useState(undefined);
  const onChangeFilter = useCallback((e) => setFilter(e.target.value), [setFilter]);
  const goBack = useCallback(() => {
    reload();
    setApplication(undefined);
  }, [setApplication, reload]);
  const onSelectAppToPublish = useCallback((app) => {
    setSelectApplication(false);
    setApplication(app);
  }, [setApplication, setSelectApplication]);
  const applicationIsEditable = useCallback((application) => {
    return canEditPublishedApps ||
      (application?.info?.user || '').toLowerCase() === (authenticatedUser?.userName || '').toLowerCase();
  }, [canEditPublishedApps, authenticatedUser]);
  const {
    isFavourite,
    toggleFavourite,
    sorter
  } = useFavouriteApplications(
    Modes.folder,
    'path'
  );
  let applicationsContent;
  let editApplicationContent;
  const hasDeprecated = availableApplications.some((app) => app.deprecated || app.readOnly);
  if (authenticating || pending) {
    applicationsContent = (
      <div className="content loading">
        <LoadingIndicator style={{marginRight: 5, width: 15, height: 15}} />
        <span>Fetching applications list</span>
      </div>
    );
  } else if (authenticationError || !authenticatedUser) {
    applicationsContent = (
      <div className="content error">
        <div className="header">
          Authentication error
        </div>
        <div className="description">
          Please, contact {settings?.supportName || 'support team'} for details.
        </div>
      </div>
    )
  } else if (application) {
    editApplicationContent = (
      <EditFolderApplication
        application={application}
        applications={applications}
        goBack={goBack}
      />
    );
  } else if (availableApplications.length === 0) {
    applicationsContent = (
      <div className="content error">
        <div className="header">
          No applications configured
        </div>
        <div className="description">
          Please, contact {settings?.supportName || 'support team'} for details.
        </div>
      </div>
    )
  } else {
    const filtered = availableApplications
      .filter((app) => showDeprecated || (!app.deprecated && !app.readOnly) || isFavourite(app))
      .filter(filterAppFn(filter))
      .filter((app) => filterAppType === undefined || app.appType === filterAppType);
    const renderApps = (latest) => {
      return filtered
        .filter(anApp => !!anApp.latest === !!latest)
        .sort(sorter)
        .map((application) => (
          <FolderApplicationCard
            key={application.id}
            application={application}
            onEdit={
              applicationIsEditable(application) && !settings?.disablePublishingApps
                ? setApplication
                : undefined
            }
            isFavourite={isFavourite(application)}
            onFavouriteClick={toggleFavourite}
            onClick={setLaunchApplication}
          />
        ))
    };
    applicationsContent = (
      <div>
        <div
          className={
            classNames(
              'apps',
              'folder-apps',
              {
                'with-latest': availableApplications.some((a) => a.latest)
              }
            )
          }
        >
          {
            renderApps(true)
          }
        </div>
        <div
          className={
            classNames(
              'apps',
              'folder-apps',
              {
                'with-latest': availableApplications.some((a) => a.latest)
              }
            )
          }
        >
          {
            renderApps(false)
          }
        </div>
      </div>
    );
  }
  return (
    <FAValidationSessionsContext.Provider value={sessions}>
      <SettingsContext.Provider value={settings}>
        <div className="application">
          <div
            className={
              classNames(
                'static-header',
                'displayed'
              )
            }
          >
            {
              application && (
                <div
                  onClick={goBack}
                  className="link"
                >
                  BACK TO APPLICATIONS
                </div>
              )
            }
            {
              !application && (
                <div
                  className={
                    classNames(
                      'filter-applications',
                      {
                        dark: settings?.darkMode
                      }
                    )
                  }
                >
                  <input
                    className="input"
                    value={filter || ''}
                    onChange={onChangeFilter}
                    placeholder="Filter applications"
                  />
                  {
                    hasDeprecated && (
                      <div
                        className="deprecated-checkbox"
                        onClick={onToggleDeprecated}
                      >
                        <input
                          type="checkbox"
                          checked={showDeprecated}
                          onChange={onChangeShowDeprecated}
                        />
                        Show deprecated / read only
                      </div>
                    )
                  }
                </div>
              )
            }
            {
              applicationTypes.length > 1 && applicationTypes.map((appType) => (
                <span
                  className={
                    classNames(
                      'application-type-card',
                      {
                        selected: filterAppType === appType,
                      }
                    )
                  }
                  onClick={() => onChangeFilterAppType(appType)}
                >
                  {appType}
                </span>
              ))
            }
            {
              canPublishApps && !application && !settings?.disablePublishingApps && (
                <div
                  className="link"
                  onClick={onPublishClick}
                  style={
                    applicationTypes.length > 1 ? { marginLeft: 10 } : {}
                  }
                >
                  NEW APP
                </div>
              )
            }
          </div>
          {
            CP_APPLICATIONS_LOGO && (
              <div className="static-footer">
                <img className="logo" src={CP_APPLICATIONS_LOGO} alt="Logo" />
              </div>
            )
          }
          {
            editApplicationContent && (
              <div
                className="edit-application"
                style={{position: 'relative'}}
              >
                {editApplicationContent}
              </div>
            )
          }
          {
            applicationsContent && (
              <div style={{position: 'relative'}}>
                {applicationsContent}
              </div>
            )
          }
        </div>
        <PickUpFolderApplicationModal
          visible={selectApplication}
          onClose={onClosePickUpApplication}
          onSelectApplication={onSelectAppToPublish}
          allApplications
        />
        <Modal
          className="launch-folder-application-modal"
          visible={!!launchApplication}
          title={false}
          onClose={onApplicationLaunchCancelled}
          closeButton
        >
          <LaunchFolderApplication application={launchApplication} />
        </Modal>
        <Help className="help" />
      </SettingsContext.Provider>
    </FAValidationSessionsContext.Provider>
  );
}

export default FolderApp;
