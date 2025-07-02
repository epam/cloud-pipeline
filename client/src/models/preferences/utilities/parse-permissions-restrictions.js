import roleModel from '../../../utils/roleModel';

export function parsePermissionsRestrictionsConfig (restrictions) {
  const parseRule = (rule) => {
    const {
      role = 'ALL',
      disable = '',
      readOnly = false,
      readonly = readOnly,
      // eslint-disable-next-line camelcase
      only_default_storage = false,
      onlyDefaultStorage = only_default_storage
    } = rule;
    return role
      .split(/[,;\s]/g)
      .filter((aRole) => aRole.length > 0)
      .map((aRole) => ({
        role: aRole,
        disable,
        readonly,
        onlyDefaultStorage
      }));
  };
  const rules = restrictions
    .reduce((result, rule) => ([
      ...result,
      ...parseRule(rule)
    ]), [])
    .filter(Boolean);
  return rules.map((rule) => {
    const {
      role,
      disable = '',
      readonly,
      onlyDefaultStorage
    } = rule;
    const masks = disable.split(/[,;\s]/g).filter((mask) => mask.length);
    const disableRead = masks.some((aMask) => /^read$/i.test(aMask));
    const disableWrite = masks.some((aMask) => /^write$/i.test(aMask));
    const disableExecute = masks.some((aMask) => /^execute$/i.test(aMask));
    return {
      role,
      readonly,
      onlyDefaultStorage,
      disabled: masks,
      disableRead,
      disableWrite,
      disableExecute,
      enabledMask: roleModel.buildPermissionsMask(
        !disableRead,
        !disableRead,
        !disableWrite,
        !disableWrite,
        !disableExecute,
        !disableExecute
      ),
      defaultMask: roleModel.buildPermissionsMask(
        0,
        disableRead,
        0,
        disableWrite,
        0,
        disableExecute
      )
    };
  });
}
