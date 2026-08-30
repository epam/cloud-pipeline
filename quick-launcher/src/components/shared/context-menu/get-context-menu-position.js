const Placement = {
  top: 'top',
  bottom: 'bottom',
  left: 'left',
  right: 'right',
  topLeft: 'topLeft',
  topRight: 'topRight',
  bottomLeft: 'bottomLeft',
  bottomRight: 'bottomRight'
};

const topAnchor = {
  y: 0,
  dy: -1,
  vMarginDirection: -1
};

const centerVerticalAnchor = {
  y: 0.5,
  dy: -0.5,
  vMarginDirection: 0
}

const bottomAnchor = {
  y: 1,
  dy: 0,
  vMarginDirection: 1
};

const leftAnchor = {
  x: 0,
  dx: -1,
  hMarginDirection: -1
};

const centerHorizontalAnchor = {
  x: 0.5,
  dx: -0.5,
  hMarginDirection: 0
}

const rightAnchor = {
  x: 1,
  dx: 0,
  hMarginDirection: 1
};

const anchors = {
  [Placement.top]: {...topAnchor, ...centerHorizontalAnchor},
  [Placement.bottom]: {...bottomAnchor, ...centerHorizontalAnchor},
  [Placement.left]: {...centerVerticalAnchor, ...leftAnchor},
  [Placement.right]: {...centerVerticalAnchor, ...rightAnchor},
  [Placement.topLeft]: {...topAnchor, x: 0, dx: 0},
  [Placement.topRight]: {...topAnchor, x: 1, dx: -1},
  [Placement.bottomLeft]: {...bottomAnchor, x: 0, dx: 0},
  [Placement.bottomRight]: {...bottomAnchor, x: 1, dx: -1}
};

const oppositeAnchors = {
  [Placement.top]: [Placement.bottom],
  [Placement.bottom]: [Placement.top],
  [Placement.left]: [Placement.right],
  [Placement.right]: [Placement.left],
  [Placement.topLeft]: [Placement.bottomLeft],
  [Placement.topRight]: [Placement.bottomRight],
  [Placement.bottomLeft]: [Placement.topLeft],
  [Placement.bottomRight]: [Placement.topRight]
};

function interpolatePosition (start, end, ratio) {
  return start + (end - start) * ratio;
}

function getPositionForAnchor(triggerBounds, menuBounds, anchor, margin = 5) {
  if (!anchor || !triggerBounds || !menuBounds) {
    return undefined;
  }
  const {
    x,
    y,
    dx,
    dy,
    vMarginDirection = 0,
    hMarginDirection = 0
  } = anchor;
  const {
    top = 0,
    left = 0,
    width = 0,
    height = 0
  } = triggerBounds;
  const {
    width: menuWidth = 0,
    height: menuHeight = 0
  } = menuBounds;
  const topMargin = typeof margin === 'object' ? (margin.top || 0) : margin;
  const leftMargin = typeof margin === 'object' ? (margin.left || 0) : margin;
  const vOffset = typeof margin === 'object' ? (margin.vertical || 0) : 0;
  const hOffset = typeof margin === 'object' ? (margin.horizontal || 0) : 0;
  return {
    top: interpolatePosition(top, top + height, y) + dy * menuHeight + vMarginDirection * topMargin + vOffset,
    left: interpolatePosition(left, left + width, x) + dx * menuWidth + hMarginDirection * leftMargin + hOffset,
    width: menuWidth,
    height: menuHeight
  }
}

function positionOutOfTheWindow(position) {
  if (!position) {
    return true;
  }
  const windowWidth = window.innerWidth || 0;
  const windowHeight = window.innerHeight || 0;
  const {
    top,
    left,
    width,
    height
  } = position;
  return (top < 0 || top + height > windowHeight) || (left < 0 || left + width > windowWidth);
}

function correctPosition(position) {
  if (!position) {
    return true;
  }
  const windowWidth = window.innerWidth || 0;
  const windowHeight = window.innerHeight || 0;
  const {
    top,
    left,
    width = 0,
    height = 0
  } = position;
  const topCorrected = Math.max(0, Math.min(windowHeight - height, top));
  const leftCorrected = Math.max(0, Math.min(windowWidth - width, left));
  return {
    top: Number.isNaN(topCorrected) ? top : topCorrected,
    left: Number.isNaN(leftCorrected) ? left : leftCorrected,
    width,
    height
  };
}

export {Placement};
export default function getContextMenuPosition(
  trigger,
  menu,
  placement = Placement.bottomLeft,
  margin = 0
) {
  if (trigger && menu) {
    const triggerBounds = trigger.getBoundingClientRect();
    const menuBounds = menu.getBoundingClientRect();
    const testAnchors = [
      placement,
      ...oppositeAnchors[placement],
      placement
    ]
      .filter(Boolean);
    let position = {
      top: 0,
      left: 0
    };
    for (const anchor of testAnchors) {
      position = getPositionForAnchor(triggerBounds, menuBounds, anchors[anchor], margin);
      if (!positionOutOfTheWindow(position)) {
        break;
      }
    }
    return correctPosition(position);
  }
  return undefined;
}
