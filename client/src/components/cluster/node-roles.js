export const nodeRoles = {
  master: 1,
  run: 1 << 1,
  cloudPipelineRole: 1 << 2,
  pipelineInfo: 1 << 3,
  nodePoolRole: 1 << 4
};

export default nodeRoles;

export const PIPELINE_INFO_LABEL = 'pipeline-info';
const pipelineInfoLabelRegExp = new RegExp(`^${PIPELINE_INFO_LABEL}$`, 'i');

// Checking if `test` contains `role`
export function testRole (test, role) {
  return (test & role) === role;
}

// Checking if `test` has any role
export function roleIsDefined (test) {
  return test !== 0;
}

export function parseLabel (label, value, config = {}) {
  let role = 0;
  let displayName = label;
  let displayValue = value;
  if ((displayValue || '').toLowerCase() === 'true') {
    displayValue = label;
  }
  const {pools = []} = config;
  if (/^RUNID$/i.test(label)) {
    role = nodeRoles.run;
    displayValue = `RUN ID ${value}`;
  } else if (/^NODE-ROLE\.KUBERNETES\.IO\/MASTER$/i.test(label)) {
    role = nodeRoles.master;
    displayValue = 'MASTER';
  } else if (pipelineInfoLabelRegExp.test(label)) {
    role = nodeRoles.pipelineInfo;
  } else if (
    /^KUBEADM\.ALPHA\.KUBERNETES\.IO\/ROLE$/i.test(label) &&
    (value || '').toLowerCase() === 'master'
  ) {
    role = nodeRoles.master;
  } else if (
    /^CLOUD-PIPELINE\/ROLE$/i.test(label)
  ) {
    if (
      [
        'edge',
        'heapster',
        'elasticsearch',
        'master',
        'dns'
      ].indexOf((value || '').toLowerCase()) >= 0
    ) {
      role = nodeRoles.master;
    } else {
      role = nodeRoles.cloudPipelineRole;
    }
  } else if (/^CLOUD-PIPELINE\/(.+)$/i.test(label) && (value || '').toLowerCase() === 'true') {
    const exec = /^CLOUD-PIPELINE\/(.+)$/i.exec(label);
    role = nodeRoles.cloudPipelineRole;
    if (exec) {
      displayValue = exec[1];
    }
  } else if (/^pool_id$/i.test(label)) {
    role = nodeRoles.nodePoolRole;
    const pool = pools.find(p => `${p.id}` === `${value}`);
    if (pool) {
      displayValue = pool.name;
    } else {
      displayValue = `Pool #${value}`;
    }
  }
  return {name: displayName, role, value: displayValue};
}

// Extracting node's roles
export function getRoles (labels) {
  let roles = 0;
  for (let key in labels) {
    if (labels.hasOwnProperty(key)) {
      roles = roles || parseLabel(key, labels[key]).role;
    }
  }
  return roles;
}
