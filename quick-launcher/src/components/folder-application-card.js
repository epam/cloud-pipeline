import React, {useCallback} from 'react';
import classNames from 'classnames';
import './components.css';
import Gear from './shared/gear';
import Star from './shared/star';
import useApplicationIcon from './utilities/use-application-icon';
import UserAttributes from './shared/user-attributes';

export default function FolderApplicationCard(
  {
    application,
    className,
    onEdit,
    onClick,
    isFavourite,
    onFavouriteClick,
    displayFavourite = true,
    disabled
  }
) {
  if (!application || !application.name) {
    return null;
  }
  const {
    iconFile,
    name,
    description,
    version,
    published,
    url,
    storage,
    info = {}
  } = application;
  const {icon} = useApplicationIcon(storage, iconFile ? iconFile.path : undefined);
  const onClickCallback = useCallback((e) => {
    if (onClick) {
      e.stopPropagation();
      e.preventDefault();
      onClick(application);
    }
  }, [application, onClick]);
  const onEditCallback = useCallback((e) => {
    if (onEdit) {
      e.stopPropagation();
      e.preventDefault();
      onEdit(application);
    }
  }, [application, onEdit]);
  const onFavouriteClickCallback = useCallback((e) => {
    if (onFavouriteClick) {
      e.stopPropagation();
      e.preventDefault();
      onFavouriteClick(application);
    }
  }, [application, onFavouriteClick]);
  const owner = info.user;
  const ownerInfo = info.ownerInfo;
  const Wrapper = ({children}) => {
    if (!disabled && !onClick && published && url) {
      return (
        <a
          className="no-link"
          href={url}
          onClick={onClickCallback}
        >
          {children}
        </a>
      );
    }
    return <>{children}</>;
  };
  return (
    <Wrapper>
      <div
        className={
          classNames(
            'app',
            'app-folder',
            {
              dark: DARK_MODE,
              published,
              disabled
            },
            className
          )
        }
        onClick={disabled ? undefined : onClickCallback}
      >
        <div className="layout">
          {
            icon && (
              <img
                src={icon}
                alt={name}
                className="icon"
              />
            )
          }
          <div className="layout-column">
            <div className="header" title={name}>
              <span className="name">
                {name}
              </span>
              {
                version && (
                  <span className="version">
                    {version}
                  </span>
                    )
              }
            </div>
            <div className="app-description" title={description}>
              {description}
            </div>
            {
              ownerInfo && (
                <div className="app-owner" title={owner}>
                  <UserAttributes
                    user={{attributes: ownerInfo}}
                    className="attributes"
                    attributeClassName="user-attribute"
                    skip={['email', 'e-mail']}
                  />
                </div>
              )
            }
            {
              !ownerInfo && owner && (
                <div className="app-owner" title={owner}>
                  {owner}
                </div>
              )
            }
          </div>
        </div>
        <div className="folder-app-actions-container">
          {
            onEdit && (
              <Gear
                className={classNames('folder-app-action', 'folder-app-gear')}
                onClick={onEditCallback}
              />
            )
          }
          {
            displayFavourite && (
              <Star
                className={
                  classNames(
                    'folder-app-action',
                    'folder-app-star',
                    {
                      'favourite': isFavourite
                    }
                  )
                }
                onClick={onFavouriteClickCallback}
              />
            )
          }
        </div>
      </div>
    </Wrapper>
  );
}
