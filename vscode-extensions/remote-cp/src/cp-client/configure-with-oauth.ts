import { ILogger } from "../common/logger";
import { ICpExtConfig } from "../config";
import { ICpClientConfig } from "./cp-client-config";

export async function configureWithOAuth(
  cpExtConfig: ICpExtConfig,
  logger: ILogger,
): Promise<ICpClientConfig | null> {
  logger.info("Configuring with OAuth ...");
  // TODO: Enable OAuth
  // const session = await vscode.authentication.getSession(
  //   "epam-cp",
  //   ["openid", "profile", ""],
  //   { createIfNone: true },
  // );
  // if (session) {
  //   this.logger.info("Found existing authentication session");
  // }
  return null;
}
