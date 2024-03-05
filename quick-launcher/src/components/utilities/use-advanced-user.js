import {useEffect, useState} from 'react';

export default function useAdvancedUser (settings, user) {
  const [canPublishApps, setCanPublishApps] = useState(false);
  const [canEditPublishedApps, setCanEditPublishedApps] = useState(false);
  useEffect(() => {
    if (user && settings) {
      const {admin, roles = []} = user;
      const folderApplicationAdvancedUserRoleNames = Array.isArray(settings.folderApplicationAdvancedUserRoleName)
        ? new Set(settings.folderApplicationAdvancedUserRoleName)
        : new Set([settings.folderApplicationAdvancedUserRoleName].filter(Boolean));
      const canPublishAppsValue = admin ||
        !!(roles.find(role => folderApplicationAdvancedUserRoleNames.has(role.name)));
      setCanPublishApps(canPublishAppsValue);
      setCanEditPublishedApps(admin);
    } else {
      setCanPublishApps(false);
      setCanEditPublishedApps(false);
    }
  }, [settings, user]);
  return {
    canPublishApps,
    canEditPublishedApps
  };
}
