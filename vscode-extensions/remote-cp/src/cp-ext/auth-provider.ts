import * as vscode from "vscode";
import * as http from "http";
import * as crypto from "crypto";
import open from "open";

import { ILogger } from "../common/logger";
import { Disposable } from "../common/disposable";
import { ICpExtConfig } from "../config";

const CP_SECRET_STORAGE_KEY = "epam.remote-cp.sessionJson";

interface ITokenResponse {
  readonly id_token: string;
  readonly access_token: string;
}

export class CpAuthProvider
  extends Disposable
  implements vscode.AuthenticationProvider
{
  public readonly onDidChangeSessions =
    new vscode.EventEmitter<vscode.AuthenticationProviderAuthenticationSessionsChangeEvent>()
      .event;

  protected constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly logger: ILogger,
  ) {
    super();
  }

  public static async createAndRegister(
    cpExtConfig: ICpExtConfig,
    context: vscode.ExtensionContext,
    logger: ILogger,
  ): Promise<CpAuthProvider> {
    logger.debug("CpAuthProvider.create()");
    const res = new CpAuthProvider(context, logger);
    context.subscriptions.push(
      vscode.authentication.registerAuthenticationProvider(
        "epam-cp",
        `${cpExtConfig.prefix} Authenthication`,
        res,
        { supportsMultipleAccounts: false },
      ),
    );
    return res;
  }

  async getSessions(
    _scopes: readonly string[] | undefined,
    _options: vscode.AuthenticationProviderSessionOptions,
  ): Promise<vscode.AuthenticationSession[]> {
    this.logger.info(
      `cpAuthProvider.getSessions(), ` +
        `scopes=${JSON.stringify(_scopes)}, ` +
        `options=${JSON.stringify(_options)}`,
    );

    const sessionJson: string | undefined = await this.context.secrets.get(
      CP_SECRET_STORAGE_KEY,
    );
    if (!sessionJson) return [];

    const session: vscode.AuthenticationSession = JSON.parse(sessionJson);
    return [session];
  }

  async createSession(scopes: string[]): Promise<vscode.AuthenticationSession> {
    this.logger.info(
      `cpAuthProvider.createSession(), scopes=${scopes.join(",")}`,
    );

    const clientId = "your-client-id";
    const clientSecret = "your-client-secret";
    const authUrl = "https://auth.epam.com/oauth/authorize";
    const tokenUrl = "https://auth.epam.com/oauth/token";
    const redirectUri = "http://127.0.0.1:54321/callback";

    let state: string;
    if (process.env.CP_STUBS) {
      state = "stub-state";
    } else {
      state = crypto.randomBytes(16).toString("hex");
    }

    const codeVerifier = crypto.randomBytes(32).toString("hex");
    const codeChallenge = this.base64URLEncode(
      crypto.createHash("sha256").update(codeVerifier).digest(),
    );

    const authorizationUrl =
      `${authUrl}?` +
      new URLSearchParams({
        response_type: "code",
        client_id: clientId,
        redirect_uri: redirectUri,
        scope: scopes.join(" "),
        state,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
      });

    await open(authorizationUrl);

    const authCode = await new Promise<string>((resolve, reject) => {
      const server = http.createServer(async (req, res) => {
        if (req.url?.startsWith("/callback")) {
          const url = new URL(req.url, redirectUri);
          const code = url.searchParams.get("code");
          const returnedState = url.searchParams.get("state");
          if (code && returnedState === state) {
            res.writeHead(200, { "Content-Type": "text/html" });
            res.end("<h1>You can close this tab now.</h1>");
            server.close();
            resolve(code);
          } else {
            res.writeHead(400);
            res.end("Invalid request");
            server.close();
            reject(new Error("Invalid OAuth callback"));
          }
        }
      });
      server.listen(54321, "127.0.0.1");
    });

    // Exchange code for access_token
    const resp = await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code: authCode,
        redirect_uri: redirectUri,
        client_id: clientId,
        client_secret: clientSecret,
        code_verifier: codeVerifier,
      }),
    });

    if (!resp.ok) {
      throw new Error(`Token request failed: ${resp.statusText}`);
    }

    const tokenResponse = (await resp.json()) as ITokenResponse;
    const accessToken = tokenResponse.access_token;
    const accountLabel = tokenResponse.id_token
      ? this.decodeJwt(tokenResponse.id_token).email
      : "epam-user";

    // Сохраняем в SecretStorage
    await this.context.secrets.store("epam-token", accessToken);
    await this.context.secrets.store("epam-account", accountLabel);

    const session: vscode.AuthenticationSession = {
      id: "epam-session",
      accessToken,
      account: { label: accountLabel, id: accountLabel },
      scopes,
    };

    throw new Error("Not implemented fire event");
    // // Уведомляем VS Code
    // this.onDidChangeSessions({
    //   added: [session],
    //   removed: [],
    //   changed: [],
    // });

    return session;
  }

  async removeSession(sessionId: string): Promise<void> {
    this.logger.info(`cpAuthProvider.removeSession(), sessionId=${sessionId}`);
    await this.context.secrets.delete(CP_SECRET_STORAGE_KEY);
  }

  // routines

  private base64URLEncode(buffer: Buffer): string {
    return buffer
      .toString("base64")
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  }

  private decodeJwt(token: string): any {
    const [, payload] = token.split(".");
    return JSON.parse(Buffer.from(payload, "base64").toString("utf8"));
  }
}
