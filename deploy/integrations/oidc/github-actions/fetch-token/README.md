# Fetch a Cloud Pipeline token

A composite GitHub Action that exchanges a workflow run's GitHub OIDC token for a short-lived
Cloud Pipeline API token, as [RFC 8693](https://www.rfc-editor.org/rfc/rfc8693) describes, and
exports it for the steps that follow.

No Cloud Pipeline secret is stored in GitHub. The job proves which repository, branch and
environment it is running in with a token GitHub mints for it, and Cloud Pipeline decides what that
is worth.

## Usage

```yaml
permissions:
  id-token: write          # without this no OIDC token is available to the job
  contents: read

steps:
  - uses: epam/cloud-pipeline/deploy/integrations/oidc/github-actions/fetch-token@develop
    with:
      api-url: https://cloud-pipeline.example.com

  - run: pipe storage ls cp://my-bucket/
```

`API` and `API_TOKEN` are exported to the rest of the job, which is all `pipe` needs, so later
steps require no further configuration. To use the token directly instead:

```yaml
  - uses: epam/cloud-pipeline/deploy/integrations/oidc/github-actions/fetch-token@develop
    id: cp
    with:
      api-url: https://cloud-pipeline.example.com

  - run: |
      curl -sS -H "Authorization: Bearer ${{ steps.cp.outputs.token }}" \
        "${{ steps.cp.outputs.api-url }}/whoami"
```

## Input

| Name | Required | Description |
|---|---|---|
| `api-url` | yes | The Cloud Pipeline URL. Either the API root as `pipe` expects it in `API` — `https://host/pipeline/restapi` — or just the host, in which case `/pipeline/restapi` is appended. The resolved value is printed at the start of the step. |

**The resolved value is also the audience requested from GitHub**, so it has to match one of the
`audiences` of the issuer entry configured on the deployment. A mismatch there is the most common
reason for a refusal.

## Outputs

| Name | Description |
|---|---|
| `token` | The Cloud Pipeline API token. Masked in the log, but it is a credential: do not echo it or write it to an artefact. |
| `api-url` | The API root the token was issued by, after the input was resolved. |
| `expires-in` | How long the token is valid for, in seconds. |

## What the deployment needs

This action is only half of the setup; an administrator has to configure the other half once. In
full, see
[12.16. OIDC token exchange](../../../../../docs/md/manual/12_Manage_Settings/12.16._OIDC_token_exchange.md).

1. A platform user for the job to act as. The exchange never creates one.
2. `oidc.token.exchange.enabled=true` on the API service, and a restart. Until then the endpoint is
   not registered and answers `404`.
3. An entry in the `system.oidc.token.exchange.issuers` preference, for example:

   ```json
   [
     {
       "issuer": "https://token.actions.githubusercontent.com",
       "audiences": ["https://cloud-pipeline.example.com/pipeline/restapi"],
       "algorithms": ["RS256"],
       "clients": [
         {
           "id": "github-actions",
           "rules": [
             { "claim": "repository_id", "operator": "EQUALS", "value": "<repository id>" },
             { "claim": "ref", "operator": "EQUALS", "value": "refs/heads/main" }
           ],
           "assumeStrategy": {
             "type": "SPECIFIC_USER",
             "user": "<platform user>",
             "rolesToKeep": ["ROLE_USER"],
             "maxTokenLifetime": 3600,
             "ignoreSubjectTokenLifetime": true
           }
         }
       ]
     }
   ]
   ```

   `gh api repos/<org>/<repo> --jq .id` gives the repository id. Pin the id rather than the name: a
   name can be released and claimed by somebody else.

   The second condition matters as much as the first. Every repository on github.com shares one
   issuer and any workflow can ask for any audience, so the claim conditions are the only boundary
   there is — with `repository_id` alone, anyone able to push a branch in that repository can obtain
   the token.

   `ignoreSubjectTokenLifetime` is needed because a GitHub token expires minutes after it is issued;
   without it the Cloud Pipeline token is capped at whatever is left of it and a long transfer dies
   midway.

## Requirements

- `jq` and `curl` on the runner. Both are present on GitHub-hosted runners; a minimal self-hosted
  runner may need `jq` installed.
- The runner must be able to reach the Cloud Pipeline API. GitHub-hosted runners are on the public
  internet, so a deployment with a private API needs a self-hosted runner inside the network.
- The API must be able to reach `https://token.actions.githubusercontent.com` to fetch GitHub's
  signing keys, unless the issuer entry carries an inline `jwks`.

## When it fails

The API answers a refusal with an error code and nothing else, by design: the endpoint needs no
platform token, so a caller must not be able to learn which check refused them. The action maps the
code to the likeliest cause and prints that; the actual reason is in the Cloud Pipeline API log, one
line per attempt with the issuer, subject, matched client and user.

| Code | Likeliest cause |
|---|---|
| `404` | The exchange is not enabled, or the API was not restarted after enabling it |
| `unauthorized_client` | No configured client matched this run — wrong repository id, or the ref or environment does not match |
| `invalid_grant` | The configured audience does not match `api-url`, or the platform user named by the client does not exist |

A missing `permissions: id-token: write` is caught before any request is made, with a message
saying so.

## Note on the issued token

It is a normal platform token, accepted by the API, the edge and `pipe` alike. It is **not
renewable**: `GET /restapi/user/token` is refused for it, which is what keeps its life tied to
GitHub's. A job that genuinely needs to mint platform tokens needs `"renewable": true` on its
client, accepting that the tie is given up.
