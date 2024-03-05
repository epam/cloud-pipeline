import React, {useCallback, useState} from 'react';
import classNames from 'classnames';
import {useSettings} from '../use-settings';
import './help.css';
import Markdown from "./markdown";

export default function Help (
  {
    className
  }
) {
  const [visible, setVisible] = useState(false);
  const show = useCallback(() => setVisible(true), [setVisible]);
  const hide = useCallback(() => setVisible(false), [setVisible]);
  const settings = useSettings();
  if (settings?.help) {
    return (
      <div
        className={
          classNames(
            'help-button-container',
            className,
            {visible}
          )
        }
      >
        <div
          className={'help-button'}
          onClick={show}
        >
          ?
        </div>
        <div
          className="overlay"
          onClick={hide}
        >
          {'\u00A0'}
        </div>
        <Markdown
          className="content"
          src={settings?.help}
        />
      </div>
    );
  }
  return null;
}
