export function correctIdentifiers (identifiers) {
  return identifiers.filter((p) => p !== undefined && p !== null && !Number.isNaN(Number(p)));
}

function correctRunAsPermission (permission) {
  let {
    pipelinesAllowed = true,
    toolsAllowed = true,
    pipelines = [],
    tools = [],
    ...rest
  } = permission;
  if (!pipelinesAllowed) {
    pipelines = [];
  } else {
    pipelines = correctIdentifiers(pipelines);
  }
  if (!toolsAllowed) {
    tools = [];
  } else {
    tools = correctIdentifiers(tools);
  }
  return {
    ...rest,
    pipelines,
    pipelinesAllowed,
    tools,
    toolsAllowed
  };
}

export function correctRunAsPermissions (permissions = []) {
  return permissions.map(correctRunAsPermission);
}

function numberArraysAreEqual (numberArrayA, numberArrayB) {
  const a = [...new Set(numberArrayA || [])].sort((i, j) => i - j);
  const b = [...new Set(numberArrayB || [])].sort((i, j) => i - j);
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

export function runAsPermissionsEqual (permissionA, permissionB, principalProperty = 'principal') {
  const {
    name: nameA,
    [principalProperty]: principalA,
    pipelinesAllowed: pipelinesAllowedA = true,
    toolsAllowed: toolsAllowedA = true,
    pipelines: pipelinesA = [],
    tools: toolsA = []
  } = permissionA;
  const {
    name: nameB,
    [principalProperty]: principalB,
    pipelinesAllowed: pipelinesAllowedB = true,
    toolsAllowed: toolsAllowedB = true,
    pipelines: pipelinesB = [],
    tools: toolsB = []
  } = permissionB;
  return nameA === nameB &&
    principalA === principalB &&
    pipelinesAllowedA === pipelinesAllowedB &&
    toolsAllowedA === toolsAllowedB &&
    numberArraysAreEqual(pipelinesA, pipelinesB) &&
    numberArraysAreEqual(toolsA, toolsB);
}
