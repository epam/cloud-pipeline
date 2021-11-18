import React, {
  useCallback, useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState
} from 'react';
import ReactDOM from 'react-dom';
import classNames from 'classnames';
import getContextMenuPosition, {Placement} from './get-context-menu-position';
import './context-menu.css';

const ContextMenuOverlayContext = React.createContext({});

function ContextMenuOverlay({children}) {
  const [visible, setVisible] = useState(false);
  const close = useCallback((event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    setVisible(false);
  }, [setVisible]);
  const open = useCallback((event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    setVisible(true);
  }, [setVisible]);
  const contextValue = useMemo(() => ({
    visible,
    open
  }), [open, visible]);
  return (
    <ContextMenuOverlayContext.Provider value={contextValue}>
      {
        ReactDOM.createPortal(
          (
            <div
              id="context-menu-overlay"
              className={
                classNames(
                  'context-menu-overlay',
                  {visible}
                )
              }
              onClick={close}
              role="button"
              tabIndex={0}
            >
            </div>
          ),
          document.body
        )
      }
      {children}
    </ContextMenuOverlayContext.Provider>
  );
}

function ContextMenu(
  {
    anchorElement,
    children,
    className,
    trigger,
    placement = Placement.bottomLeft,
    visible = false,
    onVisibilityChange,
    nested = false,
    visibilityControlled = false,
    margin = 5
  }
) {
  const container = document.getElementById('context-menu-overlay');
  const [updatePosition, setUpdatePosition] = useState(0);
  const {
    visible: overlayVisible = false,
    open: openOverlay = (() => {})
  } = useContext(ContextMenuOverlayContext);
  const triggerRef = useRef();
  const menuRef = useRef();
  useEffect(() => {
    let w = 0;
    let h = 0;
    let cancel;
    let modalW = 0;
    let modalH = 0;
    const handle = () => {
      if (w !== window.innerWidth || h !== window.innerHeight) {
        w = window.innerWidth;
        h = window.innerHeight;
        setUpdatePosition(o => o + 1);
      } else if (
        menuRef.current && (
          (
            modalW !== menuRef.current.clientWidth &&
            menuRef.current.clientWidth
          ) ||
          (
            modalH !== menuRef.current.clientHeight &&
            menuRef.current.clientHeight
          )
        )
      ) {
        modalW = menuRef.current.clientWidth;
        modalH = menuRef.current.clientHeight;
        setUpdatePosition(o => o + 1);
      }
      cancel = requestAnimationFrame(handle);
    };
    handle();
    return () => cancelAnimationFrame(handle);
  }, [setUpdatePosition]);
  const [position, setPosition] = useState({top: 0, left: 0});
  const [isVisible, setIsVisible] = useState(visible);
  const [menuVisible, setMenuVisible] = useState(false);
  useEffect(() => {
    setIsVisible(!!visible);
  }, [visible, setIsVisible]);
  useEffect(() => {
    if (typeof onVisibilityChange === 'function') {
      onVisibilityChange(isVisible);
    }
  }, [isVisible, onVisibilityChange]);
  useLayoutEffect(() => {
    const newPosition = getContextMenuPosition(
      triggerRef.current || anchorElement,
      menuRef.current,
      placement,
      margin
    );
    if (newPosition) {
      setPosition(newPosition);
    }
    setMenuVisible(isVisible);
  }, [
    isVisible,
    setMenuVisible,
    triggerRef,
    menuRef.current,
    anchorElement,
    placement,
    setPosition,
    margin,
    updatePosition
  ]);
  const prevent = useCallback((event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
  }, []);
  const onTriggerClick = useCallback((event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    if (openOverlay) {
      openOverlay();
    }
    setIsVisible(true);
  }, [setIsVisible, openOverlay]);
  const hide = useCallback((event) => {
    if (event) {
      event.preventDefault();
      if (!nested) {
        event.stopPropagation();
      }
    }
    setIsVisible(false);
  }, [setIsVisible, nested]);
  useEffect(() => {
    if (!overlayVisible && hide) {
      hide();
    }
  }, [overlayVisible, hide]);
  const triggerElement = visibilityControlled || !trigger
    ? trigger
    : React.cloneElement(
      trigger,
      {
        onClick: onTriggerClick
      }
    );
  const {
    top,
    left
  } = position;
  if (!container) {
    return null;
  }
  const menu = ReactDOM.createPortal(
    (
      <div
        ref={menuRef}
        className={
          classNames(
            'context-menu',
            {
              visible: menuVisible
            },
            className
          )
        }
        style={{
          top,
          left
        }}
        onClick={prevent}
        tabIndex={0}
        role="button"
      >
        {children}
      </div>
    ),
    container
  );
  return (
    <>
      {
        triggerElement && (
          <div
            style={{display: 'inline'}}
            ref={triggerRef}
          >
            {triggerElement}
          </div>
        )
      }
      {menu}
    </>
  );
}

export {Placement, ContextMenuOverlay};
export default ContextMenu;
