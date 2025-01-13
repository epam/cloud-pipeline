import type { PermissionsResponse } from '@cloud-pipeline/api';
import { fetchPermissions } from '@cloud-pipeline/api';
import type { AclClass } from '@cloud-pipeline/core';
import { useState, useEffect } from 'react';

export const usePermissions = (aclClass: AclClass, id?: number) => {
  const [permissions, setPermissions] = useState<
    PermissionsResponse | undefined
  >();
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (id) {
      const getPermissions = async () => {
        try {
          const response = await fetchPermissions(id, aclClass);
          setPermissions(response);
        } catch (err) {
          if (err instanceof Error) {
            setError(err.message);
          } else {
            setError('Permissions fetch error');
          }
        } finally {
          setIsLoading(false);
        }
      };

      void getPermissions();
    }
  }, [aclClass, id]);

  return { permissions, error, isLoading };
};
