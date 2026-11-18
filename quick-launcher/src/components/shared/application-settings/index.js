import React, {useCallback, useContext, useState} from 'react';
import classNames from 'classnames';
import SettingValue from './setting-value';
import StringInput from './string-input';
import {useSettings} from '../../use-settings';
import {ExtendedSettingsContext} from '../../utilities/use-extended-settings';
import LoadingIndicator from '../loading-indicator';
import clearSessionInfo from '../../../models/clear-session-info';
import ContextMenu, {Placement} from '../context-menu';
import Gear from '../gear';
import './application-settings.css';
import Arrow from "../arrow";
import Check from "../check";

export default function ApplicationSettings (
  {
    application,
    className,
    options,
    appendDefault,
    getSettingValue,
    getSettingDependencyValues,
    extendedOptions,
    onChange,
    onDependencyChange,
    onShowHideCallback,
    isHidden
  }
) {
  const appSettings = useSettings();
  const [contextMenuVisible, setContextMenuVisible] = useState(false);
  const [subMenu, setSubMenu] = useState(undefined);
  const [multiSelectionSubMenu, setMultiSelectionSubMenu] = useState(undefined);
  const [filter, setFilter] = useState(undefined);
  const onContextMenuVisibilityChanged = useCallback((visible) => {
    setContextMenuVisible(visible)
    if (!visible) {
      setSubMenu(undefined);
      setMultiSelectionSubMenu(undefined);
      setFilter(undefined);
    }
  }, [setSubMenu, setMultiSelectionSubMenu, setFilter, setContextMenuVisible]);
  const onChangeDependencyOptions = (setting, dependency, value) => {
    onDependencyChange(setting, dependency, value, true);
  }
  const onChangeOptions = useCallback((setting, value, event) => {
    onChange(setting, value, true);
    if (setting && value && setting.valueHasSubOptions(value)) {
      setSubMenu({
        setting,
        subMenuItems: setting.itemSubOptions(value),
        target: event ? event.currentTarget : undefined
      });
    } else {
      setSubMenu(undefined);
    }
    setMultiSelectionSubMenu(undefined);
    setFilter(undefined);
  }, [onChange, setSubMenu, setMultiSelectionSubMenu, setFilter]);

  const onOpenMultiSelectionSubMenu = useCallback((setting, event) => {
    if (setting) {
      setMultiSelectionSubMenu({
        setting,
        target: event ? event.currentTarget : undefined
      });
    } else {
      setMultiSelectionSubMenu(undefined);
    }
    setSubMenu(undefined);
    setFilter(undefined);
  }, [setSubMenu, setMultiSelectionSubMenu, setFilter]);

  const onChangeMultiSelectionSubMenuFilter = useCallback((event) => {
    setFilter(event.target.value);
  }, [setFilter]);

  const onChangeMultiSelectionOption = useCallback((setting, value) => {
    onChange(setting, value, true);
  }, [onChange]);

  const appExtendedSettings = useContext(ExtendedSettingsContext);
  const appExtendedSettingsFiltered = (appExtendedSettings || [])
    .filter(setting => !setting.availableForApp || setting.availableForApp(application));
  const hasExtendedSettings = appExtendedSettingsFiltered
    .filter(o => !/^divider$/i.test(o.type)).length > 0;
  const hasSessionInfoSettings = appSettings?.sessionInfoStorage &&
    appSettings?.sessionInfoPath;
  const [clearingSessionInfo, setClearingSessionInfo] = useState(false);
  const clearSessionInfoCallback = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setClearingSessionInfo(true);
    clearSessionInfo(
      application,
      appSettings,
      {
        ...options,
        ...appendDefault(extendedOptions)
      }
    )
      .then(() => {
        setClearingSessionInfo(false);
      })
      .catch(e => {
        console.error(e);
        setClearingSessionInfo(false);
      })
  }, [
    setClearingSessionInfo,
    clearSessionInfo,
    appSettings,
    appExtendedSettingsFiltered,
    extendedOptions,
    appendDefault,
    options
  ]);
  const toggleShowHide = useCallback(() => {
    onContextMenuVisibilityChanged(false);
    if (typeof onShowHideCallback === 'function') {
      onShowHideCallback();
    }
  }, [onContextMenuVisibilityChanged, onShowHideCallback]);
  const extendedOptionsPresentation = [];
  for (let setting of appExtendedSettingsFiltered) {
    const value = getSettingValue(setting);
    if (/^(divider|boolean|string)$/i.test(setting.type)) {
      continue;
    }
    if (/^multi-selection$/i.test(setting.type) && (value || []).length === 0) {
      continue;
    }
    const dependencyValue = getSettingDependencyValues(setting);
    const title = setting.title;
    const valueStr = setting.valuePresentation(value || setting.default) || 'Default';
    let dependencyValuePresentations = Object
      .entries(dependencyValue || {})
      .filter(([key]) => !/^__.+__$/i.test(key))
      .map(([key, value]) => `${key} ${value}`)
      .join(', ');
    if (dependencyValuePresentations.length) {
      dependencyValuePresentations = `(${dependencyValuePresentations})`;
    }
    extendedOptionsPresentation.push((
      <span
        key={setting.key}
        className={
          classNames(
            'extended-setting-presentation',
            setting.className
          )
        }
      >
        {title}: {valueStr} {dependencyValuePresentations}
      </span>
    ));
  }
  const {
    setting: subMenuSetting,
    subMenuItems = [],
    target: subMenuTarget
  } = subMenu || {};
  const subMenuVisible = !!subMenu && !!subMenuSetting;
  const getSubMenuItemValue = useCallback((key) => {
    if (subMenuSetting && getSettingDependencyValues) {
      const depValue = getSettingDependencyValues(subMenuSetting) || {};
      return depValue[key];
    }
    return undefined;
  }, [getSettingDependencyValues, subMenuSetting]);

  const {
    setting: multiSelectionSubMenuSetting,
    target: multiSelectionSubMenuTarget
  } = multiSelectionSubMenu || {};
  const multiSelectionSubMenuVisible = !!multiSelectionSubMenu &&
    !!multiSelectionSubMenuSetting;

  if (
    !hasExtendedSettings &&
    !hasSessionInfoSettings
  ) {
    return null;
  }
  return (
    <div className={classNames('application-settings', className)}>
      <div className="gear-container">
        <ContextMenu
          className="settings-context-menu"
          placement={Placement.bottomRight}
          trigger={(
            <Gear>
              {extendedOptionsPresentation}
            </Gear>
          )}
          visible={contextMenuVisible}
          onVisibilityChange={onContextMenuVisibilityChanged}
        >
          {
            contextMenuVisible ? (appExtendedSettingsFiltered.map((setting) => [
                /^radio$/i.test(setting.type) && (
                  <div
                    key={setting.key}
                    className="application-setting"
                  >
                    <div className="application-setting-title">
                      {setting.title}:
                    </div>
                    <div className="application-setting-value">
                      <SettingValue
                        config={setting}
                        onChange={(value, event) => onChangeOptions(setting, value, event)}
                        value={getSettingValue(setting)}
                        dependency={getSettingDependencyValues(setting)}
                      />
                    </div>
                  </div>
                ),
                /^boolean$/i.test(setting.type) && (
                  <div
                    key={setting.key}
                    className="application-setting boolean-setting"
                    onClick={() => onChange(setting, !getSettingValue(setting, true), true)}
                  >
                    <div className="application-setting-title">
                      {setting.title}
                    </div>
                    {
                      getSettingValue(setting, true)
                        ? (<Check className="boolean-setting-value" />)
                        : (<span>{'\u00A0'}</span>)
                    }
                  </div>
                ),
                /^multi-selection$/i.test(setting.type) && (
                  <div
                    key={setting.key}
                    className="application-setting multi-selection-setting"
                    onClick={e => onOpenMultiSelectionSubMenu(setting, e)}
                  >
                    <div className="application-setting-title">
                      {setting.title}
                    </div>
                    <div className="multi-selection-value">
                      {
                        (getSettingValue(setting) || []).length > 0 && (
                          <span>
                          {(getSettingValue(setting) || []).length}
                        </span>
                        )
                      }
                      <Arrow direction="right" />
                    </div>
                  </div>
                ),
                /^divider$/i.test(setting.type) && (
                  <div
                    key={setting.key}
                    className="application-setting-divider"
                  >
                    {'\u00A0'}
                  </div>
                ),
                /^string$/i.test(setting.type) && (
                  <StringInput
                    key={setting.key}
                    title={setting.title}
                    linkRenderFn={setting.linkRenderFn}
                    linkText={setting.linkText}
                    value={getSettingValue(setting, true)}
                    onChange={(value) => onChange(
                      setting,
                      value,
                      true
                    )}
                    hiddenUnderLink={setting.hiddenUnderLink}
                    validateFn={setting.validateFn}
                    formatterFn={setting.formatterFn}
                    correctFn={setting.correctFn}
                  />
                ),
              ])
                .reduce((r, c) => ([...r, ...c]), [])
                .filter(Boolean)
            ) : null
          }
          {
            hasSessionInfoSettings && (
              <div
                className="application-session-info"
                onClick={
                  clearingSessionInfo
                    ? undefined
                    : clearSessionInfoCallback
                }
              >
                {
                  clearingSessionInfo && (
                    <>
                      <span>
                        Clearing session info...
                      </span>
                      <LoadingIndicator
                        style={{
                          fill: '#aaa',
                          width: 8,
                          height: 8,
                          marginLeft: 5
                        }}
                      />
                    </>
                  )
                }
                {
                  !clearingSessionInfo && (
                    <span className="clear-session-info-link">
                      Clear session info
                    </span>
                  )
                }
              </div>
            )
          }
          {
            contextMenuVisible && (
              <div
                className="application-hide-action"
                onClick={toggleShowHide}
              >
                <span className="show-hide-link">
                  {
                    isHidden ? 'Show application' : 'Hide application'
                  }
                </span>
              </div>
            )
          }
        </ContextMenu>
      </div>
      {
        subMenuVisible && (
          <ContextMenu
            className="application-setting-sub-options-menu"
            placement={Placement.right}
            anchorElement={subMenuTarget}
            visible={subMenuVisible}
            visibilityControlled
            margin={10}
          >
            {
              (subMenuItems || []).map(item => (
                [
                  <div
                    key={item.key}
                    className="application-setting"
                  >
                    <div className="application-setting-title">
                      {item.title}:
                    </div>
                    <div className="application-setting-value">
                      <SettingValue
                        config={item}
                        onChange={(value) => onChangeDependencyOptions(subMenuSetting, item.key, value)}
                        value={getSubMenuItemValue(item.key)}
                      />
                    </div>
                  </div>,
                  (
                    <div
                      key={`${item.key}-divider`}
                      className="application-setting-divider"
                    >
                      {'\u00A0'}
                    </div>
                  )
                ])).reduce((r, c) => ([...r, ...c]), [])
            }
          </ContextMenu>
        )
      }
      {
        multiSelectionSubMenuVisible && (
          <ContextMenu
            className="application-setting-sub-options-menu multi-selection-menu"
            placement={Placement.right}
            anchorElement={multiSelectionSubMenuTarget}
            visible={multiSelectionSubMenuVisible}
            visibilityControlled
            margin={10}
          >
            <div className="sub-menu-header">
              <input
                className="sub-menu-filter"
                value={filter}
                onChange={onChangeMultiSelectionSubMenuFilter}
              />
            </div>
            <div
              className="application-setting"
            >
              <div className="application-setting-value multi-selection">
                <SettingValue
                  config={multiSelectionSubMenuSetting}
                  onChange={(value) => onChangeMultiSelectionOption(multiSelectionSubMenuSetting, value)}
                  value={getSettingValue(multiSelectionSubMenuSetting)}
                  filter={filter}
                />
              </div>
            </div>
            <div
              className="sub-menu-footer"
            >
              <span
                className="sub-menu-action"
                onClick={() => onChangeMultiSelectionOption(multiSelectionSubMenuSetting, [])}
              >
                Clear selection
              </span>
            </div>
          </ContextMenu>
        )
      }
    </div>
  );
}
