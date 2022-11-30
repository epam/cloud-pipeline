import React, {useCallback} from 'react';
import classNames from 'classnames';
import useAppExtendedSettings from './utilities/use-app-extended-settings';
import ApplicationSettings from './shared/application-settings';
import './components.css';
import {launchOptionsContainSensitiveMounts} from "../models/pre-run-checks/check-sensitive-storages";
import {useSettings} from './use-settings';

export default function ApplicationCard(
  {
    application,
    onClick,
    options
  }
) {
  const settings = useSettings();
  const {
    appendDefault,
    getSettingValue,
    getSettingDependencyValues,
    onChange,
    onDependencyChange,
    options: extendedOptions,
  } = useAppExtendedSettings(application);
  const sensitive = launchOptionsContainSensitiveMounts(extendedOptions);
  const onLaunch = useCallback(() => {
    onClick && onClick(appendDefault(extendedOptions));
  }, [extendedOptions, appendDefault]);
  return (
    <div
      className={classNames('app', {dark: settings?.darkMode, sensitive})}
      onClick={onLaunch}
    >
      {
        application.background && (
          <div
            className="background"
            style={
              Object.assign(
                {
                  backgroundImage: `url("${application.background}")`
                },
                application.backgroundStyle || {}
              )
            }
          >
            {'\u00A0'}
          </div>
        )
      }
      <div className="header">
        {
          application.icon && (
            <img
              src={application.icon}
              className="icon"
            />
          )
        }
        {
          !application.icon && application.iconData && (
            <img
              src={application.iconData}
              className="icon"
            />
          )
        }
        <span className="name">
            {application.name}
          </span>
        {
          application.version && (
            <span className="version">
                {application.version}
              </span>
          )
        }
      </div>
      <div className="app-description">
        {application.description}
      </div>
      <ApplicationSettings
        className="app-settings"
        application={application}
        options={options}
        appendDefault={appendDefault}
        getSettingValue={getSettingValue}
        getSettingDependencyValues={getSettingDependencyValues}
        onChange={onChange}
        onDependencyChange={onDependencyChange}
        extendedOptions={extendedOptions}
      />
    </div>
  );
}
