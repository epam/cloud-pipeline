import {ComponentType, CSSProperties, ReactNode, useCallback, useMemo, useState} from 'react';
import {Card, Input, Popover} from 'antd';
import {LoadingOutlined, StarFilled, StarOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import MultizoneUrl from '../../../../special/multizone-url';
import {getFavouritesForPanel, setFavouritesForPanel} from '../../utils/favourites';
import renderSeparator from './renderSeparator';
import RunSSHButton from './run-ssh-button';
import styles from './CardsPanel.module.css';
import type {CardsPanelAction, CardsPanelItem, CardsPanelProps} from './cards-panel.types';

const ACTION_MIN_HEIGHT = 18;

function normalizeChildren<TItem>(children: TItem | TItem[] | undefined): TItem[] {
  if (children === undefined || children === null) {
    return [];
  }
  return Array.isArray(children) ? children : [children];
}

function CardsPanel<TItem extends CardsPanelItem = CardsPanelItem>({
  children,
  search,
  panelKey,
  style,
  onClick,
  childRenderer,
  cardClassName,
  cardStyle,
  actions,
  emptyMessage,
  favouriteEnabled = false,
  displayOnlyFavourites = false,
  itemId = (item) => item.id as string | number | null,
  getFavourites: getFavouritesProp,
  setFavourites: setFavouritesProp,
  hovered,
  pageSize,
}: CardsPanelProps<TItem>) {
  const [actionInProgress, setActionInProgress] = useState(false);
  const [inProgressActionsTitle, setInProgressActionsTitle] = useState<string | null>(null);
  const [openPopovers, setOpenPopovers] = useState<number[]>([]);
  const [searchValue, setSearchValue] = useState<string | null>(null);
  const [showMax, setShowMax] = useState<number | undefined>(undefined);
  const [favouritesRevision, setFavouritesRevision] = useState(0);

  const getFavourites = useCallback(() => {
    if (getFavouritesProp) {
      return getFavouritesProp();
    }
    if (panelKey) {
      return getFavouritesForPanel(panelKey);
    }
    return [];
  }, [getFavouritesProp, panelKey, favouritesRevision]);

  const setFavourites = useCallback(
    (id: string | number | null, isFavourite: boolean) => {
      if (setFavouritesProp) {
        setFavouritesProp(id, isFavourite);
      } else if (panelKey && id !== null) {
        setFavouritesForPanel(panelKey, id, isFavourite);
      }
      setFavouritesRevision((revision) => revision + 1);
    },
    [panelKey, setFavouritesProp],
  );

  const getItemIdentifier = useCallback(
    (child: TItem): string | number | null => {
      if (typeof itemId === 'function') {
        return itemId(child);
      }
      if (itemId) {
        const value = child[itemId];
        if (typeof value === 'string' || typeof value === 'number') {
          return value;
        }
      }
      return null;
    },
    [itemId],
  );

  const childIsFavourite = useCallback(
    (child: TItem) => {
      const childIdentifier = getItemIdentifier(child);
      if (childIdentifier === null) {
        return false;
      }
      return getFavourites().indexOf(childIdentifier) >= 0;
    },
    [getFavourites, getItemIdentifier],
  );

  const openPopover = useCallback((index: number) => {
    setOpenPopovers((current) => (current.includes(index) ? current : [...current, index]));
  }, []);

  const closePopover = useCallback((index: number) => {
    setOpenPopovers((current) => current.filter((itemIndex) => itemIndex !== index));
  }, []);

  const onActionClicked = useCallback(
    async (event: React.MouseEvent, action: CardsPanelAction<TItem>, source: TItem) => {
      event.stopPropagation();
      if (actionInProgress || action.disabled) {
        return;
      }
      setActionInProgress(true);
      setInProgressActionsTitle(action.title ?? null);
      try {
        if (action.action) {
          await action.action(source);
        }
      } finally {
        setActionInProgress(false);
        setInProgressActionsTitle(null);
      }
    },
    [actionInProgress],
  );

  const renderFavouriteSelector = (child: TItem, isFavourite: boolean) => {
    const onFavouriteClick = (event: React.MouseEvent) => {
      event.stopPropagation();
      const id = getItemIdentifier(child);
      setFavourites(id, !isFavourite);
    };

    return (
      <div
        onClick={onFavouriteClick}
        className={styles.cardFavouriteContainer}
        role="button"
        tabIndex={-1}
      >
        <StarOutlined className={styles.notFavouriteSelector} style={{fontSize: 'large'}} />
        <StarFilled className={styles.favouriteSelector} style={{fontSize: 'large'}} />
      </div>
    );
  };

  const renderChildActions = (
    child: TItem,
    index: number,
    itemActions: CardsPanelAction<TItem>[],
  ) => {
    if (!itemActions.length) {
      return null;
    }

    const onVisibleChange = (visible: boolean) => {
      if (visible) {
        openPopover(index);
      } else {
        closePopover(index);
      }
    };

    const getIconType = (
      action: CardsPanelAction<TItem>,
    ): ComponentType<{
      style?: CSSProperties;
      className?: string;
    }> | null => {
      if (actionInProgress && inProgressActionsTitle === action.title) {
        return LoadingOutlined;
      }
      return action.icon ?? null;
    };

    const isHovered = openPopovers.includes(index) || hovered === child;

    return (
      <div
        className={classNames(styles.actionsContainer, 'cp-panel-card-actions', {
          [styles.hovered]: isHovered,
          hovered: isHovered,
        })}
      >
        <div
          className={classNames(
            styles.actionsContainerBackground,
            'cp-panel-card-actions-background',
          )}
        />
        {itemActions.map((action, actionIndex, array) => {
          const {
            disabled = false,
            title,
            icon,
            style: actionStyle,
            className,
            overlay,
            target,
            multiZoneUrl,
            runSSH,
            runId,
          } = action;
          const IconComponent = getIconType(action);
          const containerStyle: CSSProperties = {
            flex: 1 / array.length,
            minHeight: ACTION_MIN_HEIGHT,
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'flex-start',
          };

          if (runSSH) {
            return (
              <RunSSHButton
                key={actionIndex}
                runId={runId}
                visibilityChanged={onVisibleChange}
                className={classNames(
                  styles.actionButton,
                  {
                    [styles.disabled]: disabled,
                    'cp-disabled': disabled,
                  },
                  'cp-card-action-button',
                  className,
                )}
                style={containerStyle}
                icon={icon}
              />
            );
          }

          if (multiZoneUrl) {
            return (
              <MultizoneUrl
                key={actionIndex}
                className={classNames(
                  styles.actionButton,
                  {
                    [styles.disabled]: disabled,
                    'cp-disabled': disabled,
                  },
                  'cp-card-action-button',
                  className,
                )}
                visibilityChanged={onVisibleChange}
                style={containerStyle}
                target={target}
                configuration={multiZoneUrl}
              >
                <div
                  style={{
                    fontWeight: 'bold',
                    display: 'inline',
                  }}
                >
                  {IconComponent ? (
                    <IconComponent style={actionStyle} className={className} />
                  ) : null}
                  <span>{title}</span>
                </div>
              </MultizoneUrl>
            );
          }

          return (
            <div
              key={actionIndex}
              className={classNames(
                styles.actionButton,
                {
                  [styles.disabled]: disabled,
                  'cp-disabled': disabled,
                },
                'cp-card-action-button',
                className,
              )}
              onClick={(event) => onActionClicked(event, action, child)}
              style={{
                flex: 1 / array.length,
                minHeight: ACTION_MIN_HEIGHT,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <div style={{display: 'flex', alignItems: 'center'}}>
                {IconComponent ? <IconComponent style={actionStyle} className={className} /> : null}
                {overlay ? (
                  <Popover onOpenChange={onVisibleChange} content={overlay}>
                    <span style={actionStyle}>{title}</span>
                  </Popover>
                ) : (
                  <span style={actionStyle}>{title}</span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  const renderCard = (child: TItem, index: number) => {
    const itemActions = typeof actions === 'function' ? actions(child) : (actions ?? []);
    const resolvedCardClassName =
      typeof cardClassName === 'function' ? cardClassName(child) : cardClassName;
    const resolvedCardStyle =
      typeof cardStyle === 'function' ? cardStyle(child) : (cardStyle ?? {});
    const isFavouriteEnabled =
      typeof favouriteEnabled === 'function' ? favouriteEnabled(child) : favouriteEnabled;
    const isFavourite = childIsFavourite(child);

    return (
      <Card
        key={child.id ?? index}
        className={classNames('cp-panel-card', resolvedCardClassName, styles.card, {
          [styles.favouriteEnabled]: isFavouriteEnabled,
          [styles.favouriteItem]: isFavourite,
          [styles.notFavouriteItem]: !isFavourite,
        })}
        styles={{body: {padding: 10, height: '100%'}}}
        style={{
          width: 'initial',
          margin: 2,
          minHeight:
            itemActions.length > 0 ? ACTION_MIN_HEIGHT * itemActions.length + 10 : undefined,
          ...resolvedCardStyle,
        }}
        onClick={() => onClick?.(child)}
      >
        {isFavouriteEnabled ? renderFavouriteSelector(child, isFavourite) : null}
        <div
          style={isFavouriteEnabled ? {paddingRight: 30} : undefined}
          className={styles.cardContent}
        >
          {childRenderer(child, searchValue)}
        </div>
        {renderChildActions(child, index, itemActions)}
      </Card>
    );
  };

  const onSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchValue(event.target.value);
    setShowMax(undefined);
  };

  const onShowMore = () => {
    if (pageSize) {
      setShowMax((current) => (current ?? pageSize) + pageSize);
    }
  };

  const allItems = useMemo(() => normalizeChildren(children), [children]);

  const filteredItems = useMemo(() => {
    if (search?.searchFn) {
      return allItems.filter((item) => search.searchFn!(item, searchValue));
    }
    return allItems;
  }, [allItems, search, searchValue]);

  const visibleMax = showMax ?? pageSize;
  const slicedItems =
    visibleMax && visibleMax > 0 ? filteredItems.slice(0, visibleMax) : filteredItems;
  const hasMore = Boolean(visibleMax && visibleMax < filteredItems.length);

  const personalItemsFiltered = slicedItems.filter((item) => !item.isGlobal);
  const globalItemsFiltered = slicedItems.filter((item) => item.isGlobal);

  let personalItems = [
    ...personalItemsFiltered,
    ...globalItemsFiltered.filter((item) => childIsFavourite(item)),
  ];
  let globalItems = searchValue
    ? globalItemsFiltered.filter((item) => !childIsFavourite(item))
    : [];

  if (!searchValue && displayOnlyFavourites) {
    personalItems = personalItems.filter((item) => childIsFavourite(item));
    globalItems = [];
  }

  const favourites = personalItems.filter((item) => childIsFavourite(item));
  const other = personalItems.filter((item) => !childIsFavourite(item));

  let resolvedEmptyMessage = emptyMessage;
  if (typeof resolvedEmptyMessage === 'function') {
    resolvedEmptyMessage = resolvedEmptyMessage(searchValue);
  }

  return (
    <div className={styles.cardsPanelContainer} style={style}>
      {search ? (
        <div style={{display: 'flex', alignItems: 'center'}}>
          <Input.Search
            value={searchValue ?? undefined}
            size="small"
            onChange={onSearchChange}
            placeholder={search.placeholder}
            style={{margin: 2}}
          />
        </div>
      ) : null}
      <div style={{overflow: 'auto', flex: 1}}>
        {personalItems.length === 0 && resolvedEmptyMessage ? (
          <div style={{display: 'flex', alignItems: 'center', flex: 1, margin: 5}}>
            <span>{resolvedEmptyMessage}</span>
          </div>
        ) : null}
        <div style={{display: 'flex', justifyContent: 'center', flexWrap: 'wrap'}}>
          {favourites.map((child, index) => renderCard(child, index))}
        </div>
        <div style={{display: 'flex', justifyContent: 'center', flexWrap: 'wrap'}}>
          {other.map((child, index) => renderCard(child, index + favourites.length))}
        </div>
        {globalItems.length > 0 ? renderSeparator('Global search', 0) : null}
        <div style={{display: 'flex', justifyContent: 'center', flexWrap: 'wrap'}}>
          {globalItems.map((child, index) =>
            renderCard(child, index + favourites.length + other.length),
          )}
        </div>
        {hasMore ? (
          <div style={{display: 'flex', justifyContent: 'center', margin: 10}}>
            <a onClick={onShowMore}>Show more</a>
          </div>
        ) : null}
      </div>
    </div>
  );
}

export {CardsPanel};
export type {CardsPanelProps, CardsPanelAction, CardsPanelItem} from './cards-panel.types';
