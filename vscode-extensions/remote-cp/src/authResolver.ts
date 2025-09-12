// export const REMOTE_SSH_AUTHORITY = 'ssh-remote';
export const REMOTE_CP_AUTHORITY = "cp-remote";

export function getRemoteAuthority(host: string) {
  return `${REMOTE_CP_AUTHORITY}+${host}`;
}
