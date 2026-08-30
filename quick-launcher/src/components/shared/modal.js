import React, {useEffect, useRef, useState} from 'react';
import ReactDOM from 'react-dom';
import classNames from 'classnames';
import Close from './close';
import './modal.css';

const hidden_timeout_ms = 100;

export default function Modal(
  {
    className,
    children,
    visible,
    onClose,
    closable = true,
    title,
    closeButton = false
  }
) {
  const animationRef = useRef(undefined);
  const [hidden, setHidden] = useState(true);
  useEffect(() => {
    if (animationRef.current) {
      clearTimeout(animationRef.current);
      animationRef.current = undefined;
    }
    if (visible) {
      setHidden(false);
    } else {
      animationRef.current = setTimeout(() => setHidden(true), hidden_timeout_ms);
    }
    return () => {
      animationRef.current && clearTimeout(animationRef.current);
      animationRef.current = undefined;
    }
  }, [visible, setHidden, animationRef]);
  return ReactDOM.createPortal(
    (
      <div
        className={
          classNames(
            'modal',
            {visible, hidden}
          )
        }
      >
        <div
          className={classNames('overlay')}
          onClick={closable ? onClose : undefined}
        >
          {'\u00A0'}
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row'
          }}
        >
          <div
            className={
              classNames(
                className,
                'window'
              )
            }
            onClick={e => e.stopPropagation()}
          >
            {
              title && (
                <div
                  className="title"
                >
                  {title}
                </div>
              )
            }
            {children}
          </div>
          {
            closeButton && (
              <div className="modal-close-button-container">
                <Close
                  className="modal-close-button"
                  onClick={onClose}
                />
              </div>
            )
          }
        </div>
      </div>
    ),
    document.getElementById('modals')
  )
}
