import {CommonProps} from '../../../@types/common.ts';
import './library-actions.css';
import classNames from 'classnames';
import {Button, Dropdown} from 'antd';
import {AppstoreFilled, CheckCircleFilled} from '@ant-design/icons';
import React, {useCallback, useLayoutEffect, useMemo, useRef} from 'react';
import {doAction, isAction, isToggle, LibraryActionsStore} from './library-actions-store.ts';
import {preventDefaultAndStopPropagation} from '../../../utilities/callbacks.ts';
import {ItemType} from 'antd/es/menu/interface';

function LibraryActions(
  props: CommonProps & {
    actionsStore?: LibraryActionsStore;
  },
) {
  const {className, style, actionsStore} = props;
  const container = useRef<HTMLDivElement>(null);
  const containerAfterMenu = useRef<HTMLDivElement>(null);
  const {attachContainer, actions} = actionsStore ?? {};
  useLayoutEffect(() => {
    attachContainer?.(container.current, containerAfterMenu.current);
  }, [attachContainer, container, containerAfterMenu]);
  const displayOptionsMenuItems = useMemo(() => {
    const pre = (actions ?? [])
      .map((action) => {
        if (action === 'divider') {
          return {type: 'divider'};
        }
        switch (action.type) {
          case 'action':
            return {
              id: action.key,
              key: action.key,
              label: action.title,
              disabled: action.disabled,
            };
          case 'toggle':
            return {
              id: action.key,
              key: action.key,
              label: (
                <div className="flex items-center">
                  <span>{action.title}</span>
                  {action.checked && <CheckCircleFilled className="ml-auto" />}
                </div>
              ),
              disabled: action.disabled,
            };
          default:
            return undefined;
        }
      })
      .filter(Boolean) as ItemType[];
    let result: ItemType[] = [];
    let prevDivider = true;
    for (const item of pre) {
      const isDivider =
        !!item && typeof item === 'object' && 'type' in item && item.type === 'divider';
      if (!isDivider || !prevDivider) {
        result.push(item);
      }
      prevDivider = isDivider;
    }
    if (prevDivider) {
      result = result.slice(0, -1);
    }
    return result;
  }, [actions]);
  const onSelectDisplayOption = useCallback(
    ({key}) => {
      const action = (actions ?? [])
        .filter((action) => isAction(action) || isToggle(action))
        .find((o) => o.key === key);
      if (action) {
        doAction(action);
      }
    },
    [actions],
  );
  return (
    <div
      className={classNames(className, 'library-actions', 'library-actions-flex')}
      style={style}
      onClick={preventDefaultAndStopPropagation}
    >
      <div ref={container} className="library-actions-flex" />
      {displayOptionsMenuItems.length > 0 && (
        <Dropdown
          key="display attributes"
          trigger={['click']}
          menu={{
            items: displayOptionsMenuItems,
            onClick: onSelectDisplayOption,
            style: {width: 125},
          }}
        >
          <Button id="display-attributes" style={{lineHeight: 1}} size="small">
            <AppstoreFilled />
          </Button>
        </Dropdown>
      )}
      <div ref={containerAfterMenu} className="library-actions-flex" />
    </div>
  );
}

export {LibraryActions};
