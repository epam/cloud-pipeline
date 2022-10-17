import React, {useCallback, useEffect, useState} from 'react';
import classNames from 'classnames';
import {useApplications, ApplicationsContext, UserContext} from './components/use-applications';
import {useSettings, SettingsContext} from './components/use-settings';
import useExtendedSettings, {ExtendedSettingsContext} from './components/utilities/use-extended-settings';
import useDefaultLaunchExtendedSettings from './components/utilities/use-default-launch-extended-options';
import Application from './components/application';
import LoadingIndicator from './components/shared/loading-indicator';
import ApplicationCard from './components/application-card';
import generateParametersFromDependencies from './components/utilities/generate-parameters-from-dependencies';
import { escapeRegExp } from './models/utilities/escape-reg-exp';
import './components/components.css';
import './app.css';

function findApplication (settings, location, applications = []) {
  if (!settings) {
    return undefined;
  }
  const parsed = settings.parseUrl(location.href);
  console.log(`Searching best application for "${location.href}". Url parsed:`, parsed, 'available applications:', applications);
  let pathname = location.pathname || '';
  if (pathname.startsWith('/')) {
    pathname = pathname.slice(1);
  }
  if (pathname.endsWith('/')) {
    pathname = pathname.slice(0, -1);
  }
  pathname = escapeRegExp(pathname);
  const pathNameRegExp = new RegExp(`^\/?${pathname}\/?$`, 'i');
  const appByUrl = applications.find(o => o.rawUrl && pathNameRegExp.test(o.rawUrl));
  if (appByUrl) {
    console.log(`Application with the same url found: ${appByUrl.name}, url: "${appByUrl.rawUrl}"`, appByUrl);
    return appByUrl;
  }
  console.log(`Application with the same url ("${location.pathname || ''}") not found`);
  console.log('Searching application by url parameters:', parsed);
  const {
    image,
    app
  } = parsed;
  if (app) {
    const appToUse = applications.find(a => (new RegExp(`^${app}$`, 'i')).test(a.name));
    console.log(`Searching application by [app] "${app}": `, appToUse || 'not found');
    if (appToUse) {
      return appToUse;
    }
  }
  if (image) {
    const appToUse = applications.find(a => (new RegExp(`^${image}$`, 'i')).test(a.name));
    console.log(`Searching application by [image] "${image}": `, appToUse || 'not found');
    if (appToUse) {
      return appToUse;
    }
  }
  if (applications.length > 0) {
    console.warn(`Application not found. ${applications.length} applications available. The first application will be used`);
    return applications[0];
  }
  console.log('Application not found');
  return undefined;
}

function App({launch, location}) {
  const settings = useSettings();
  const {appExtendedSettings, dependencies} = useExtendedSettings();
  const [launchExtendedOptions, setLaunchExtendedOptions] = useDefaultLaunchExtendedSettings(
    appExtendedSettings
  );
  const [application, setApplication] = useState(undefined);
  const {applications, error, pending, user} = useApplications();
  const back = useCallback(() => {
    setApplication(undefined);
  }, [setApplication]);
  const onSelectApplication = useCallback((application, extended) => {
    setLaunchExtendedOptions(extended);
    setApplication(application);
  }, [setApplication, setLaunchExtendedOptions]);
  useEffect(() => {
    if (settings) {
      const map = settings.parseUrl(location.href, true);
      console.log('Mapper:', map);
    }
  }, [location.href, settings]);
  useEffect(() => {
    if (launch && settings && !application && applications.length > 0 && user) {
      const appToUse = findApplication(settings, location, applications);
      console.log('Application:', appToUse);
      setApplication(appToUse.id);
    }
  }, [application, applications, user, launch, setApplication, settings]);
  let app, launchUser;
  let options = {};
  if (settings) {
    options = settings.parseUrl(location.href);
    app = options?.app;
    launchUser = options?.user;
  }
  const appName = launch && launchUser && app ? `${launchUser}/${app}` : undefined;
  let applicationsContent;
  if (pending) {
    applicationsContent = (
      <div className="content loading">
        <LoadingIndicator style={{marginRight: 5, width: 15, height: 15}} />
        <span>Fetching applications list</span>
      </div>
    );
  } else if (error) {
    applicationsContent = (
      <div className="content error">
        <div className="header">
          Error fetching applications list
        </div>
        <div className="description">
          Please, contact {settings?.supportName || 'support team'} for details. <br/>
          <span className="raw">{error}</span>
        </div>
      </div>
    )
  } else if (applications.length === 0) {
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
  } else if (!user || !user.userName) {
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
  } else if (!launch) {
    applicationsContent = (
      <div
        className="apps"
      >
        {
          applications.map((anApp) => (
            <ApplicationCard
              key={anApp.id}
              application={anApp}
              onClick={(extended) => onSelectApplication(anApp.id, extended)}
              options={options}
            />
          ))
        }
      </div>
    )
  } else if (!application) { // e.g. if (launch && !application)
    applicationsContent = (
      <div className="content loading">
        <LoadingIndicator style={{marginRight: 5, width: 15, height: 15}} />
        <span>Fetching application</span>
      </div>
    );
  }

  return (
    <SettingsContext.Provider value={settings}>
      <ApplicationsContext.Provider value={applications}>
        <UserContext.Provider value={user}>
          <ExtendedSettingsContext.Provider value={appExtendedSettings}>
            <div className="application">
              <div
                className={
                  classNames(
                    'static-header',
                    {
                      displayed: !!application && !launch
                    }
                  )
                }
              >
                <div
                  onClick={back}
                  className="link"
                >
                  BACK TO APPLICATIONS
                </div>
              </div>
              {
                CP_APPLICATIONS_LOGO && (
                  <div className="static-footer">
                    <img className="logo" src={CP_APPLICATIONS_LOGO} alt="Logo" />
                  </div>
                )
              }
              {
                !application && (<div style={{position: 'relative'}}>{applicationsContent}</div>)
              }
              {
                !!application && (
                  <Application
                    id={application}
                    name={appName}
                    launchOptions={
                      Object.assign(
                        {__launch__: launch},
                        options,
                        launchExtendedOptions,
                        {
                          __parameters__: generateParametersFromDependencies(
                            appExtendedSettings,
                            launchExtendedOptions,
                            dependencies
                          )
                        }
                      )
                    }
                    goBack={back}
                  />
                )
              }
            </div>
          </ExtendedSettingsContext.Provider>
        </UserContext.Provider>
      </ApplicationsContext.Provider>
    </SettingsContext.Provider>
  );
}

export default App;
