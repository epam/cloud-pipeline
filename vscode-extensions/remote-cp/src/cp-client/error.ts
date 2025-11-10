export class CpTokenExpiredError extends Error {
  public static re: RegExp =
    /^Access token is expired: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})/m;

  public static soonRe: RegExp =
    /^Access token will expire in/m; /* This is not an error, just info */
}

export class CpAuthInvalidError extends Error {}
