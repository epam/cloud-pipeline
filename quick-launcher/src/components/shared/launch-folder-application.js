import React, {useLayoutEffect, useState} from 'react';
import classNames from 'classnames';
import useApplicationIcon from '../utilities/use-application-icon';
import UserAttributes from './user-attributes';
import {useSettings} from '../use-settings';
import Markdown from './markdown';
import './launch-folder-application.css';

export default function LaunchFolderApplication (
  {
    application
  }
) {
  const {
    iconFile,
    name,
    description,
    fullDescription,
    url,
    storage,
    info = {},
    deprecated
  } = application || {};
  const settings = useSettings();
  const {icon} = useApplicationIcon(storage, iconFile ? iconFile.path : undefined);
  const pathProperties = Object
    .values(settings?.folderApplicationPathAttributes || {})
    .filter(propertyName => !/^(name|description|fullDescription)$/i.test(propertyName))
    .filter(propertyName => !!info[propertyName]);
  const owner = info?.user;
  const ownerInfo = info?.ownerInfo;
  const externalUrl = info?.externalUrl;
  if (!application) {
    return null;
  }
  return (
    <div
      className="launch-folder-application"
    >
      <div className="launch-folder-application-info">
        {
          icon && (
            <img
              src={icon}
              alt={name}
              className="launch-folder-application-icon"
            />
          )
        }
        {
          (url || externalUrl) && (
            <a
              className="launch-folder-application-launch-a"
              href={externalUrl || url}
              target={externalUrl ? '_blank' : undefined}
            >
              <div
                className="launch-folder-application-launch"
              >
                {externalUrl ? 'OPEN' : 'LAUNCH'}
              </div>
            </a>
          )
        }
        <div
          className={classNames('launch-folder-application-title', 'launch-folder-application-row')}
        >
          {name}
          {
            deprecated && (
              <span
                className="version-deprecated"
              >
                DEPRECATED
              </span>
            )
          }
        </div>
        {
          description && (
            <div
              className={
                classNames(
                  'launch-folder-application-row',
                  'launch-folder-application-short-description'
                )
              }
            >
              {description}
            </div>
          )
        }
        {
          ownerInfo && (
            <div
              className={
                classNames(
                  'launch-folder-application-author',
                  'launch-folder-application-row',
                  'launch-folder-application-property'
                )
              }
            >
              <span className="key">Owner:</span>
              <UserAttributes
                user={{attributes: ownerInfo}}
                className={
                  classNames(
                    'launch-folder-application-attributes',
                    'value'
                  )
                }
                attributeClassName="launch-folder-application-user-attribute"
                skip={['email', 'e-mail']}
              />
            </div>
          )
        }
        {
          !ownerInfo && owner && (
            <div
              className={
                classNames(
                  'launch-folder-application-author',
                  'launch-folder-application-row',
                  'launch-folder-application-property'
                )
              }
            >
              <span className="key">Owner:</span>
              <span className="value">
              {owner}
            </span>
            </div>
          )
        }
        {
          pathProperties.map(property => (
            <div
              key={property}
              className={
                classNames(
                  'launch-folder-application-row',
                  'launch-folder-application-property'
                )
              }
            >
            <span className="key">
              {property}:
            </span>
              <span className="value">
              {info[property]}
            </span>
            </div>
          ))
        }
        {
          info?.tags && info.tags.length > 0 && (
            <div
              className={
                classNames(
                  'launch-folder-application-tags',
                  'launch-folder-application-row',
                  'launch-folder-application-property'
                )
              }
            >
              {(info?.tags || []).map((tag, idx) => (
                <div key={`tag-${idx}`} className="launch-folder-application-tag">
                  {tag}
                </div>
              ))}
            </div>
          )
        }
        {
          fullDescription && (
            <Markdown
              className={
                classNames(
                  'launch-folder-application-row',
                  'launch-folder-application-description'
                )
              }
              style={{width: '100%'}}
            >
              {fullDescription}
            </Markdown>
          )
        }
      </div>
    </div>
  );
}
