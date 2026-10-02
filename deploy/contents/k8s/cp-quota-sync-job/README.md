# cp-quota-sync-job

A daily Kubernetes CronJob that gives every platform user a personal compute
[spending quota](../../../../docs/md/manual/Appendix_D/Appendix_D._Costs_management.md#spending-quotas).

The platform has no "default per-user quota" setting: a `GROUP` or `OVERALL` quota limits the **sum**
of all its users' spending, not each user separately. This job creates a `USER` quota for each user
who does not have one yet.

## What it does

Once a day, `sync_quotas.py`:

1. loads all users (`GET /users`) and all quotas (`GET /quotas`)
2. skips every user who already has a personal compute quota (`COMPUTE_INSTANCE` / `USER`), of any
   period — so a quota you changed for one user is kept as it is
3. creates the quota for every other user (`POST /quotas`):
    - compute only, `MONTH`, `1000`
    - the user is the recipient of the notifications
    - `NOTIFY` at `100%` and at `600%`

It never updates or deletes a quota. Blocked users get a quota too.
The job fails if any quota could not be created. The users who got one keep it.

A notification is sent once for each threshold in each period, and only to the quota's recipients.
For it to arrive:

- the `billing.quotas.enabled` system preference has to be `true`
- the `BILLING_QUOTA_EXCEEDING` email notification has to be enabled and have a template
- the user has to have a valid email address — the notifier skips a recipient without one

## Settings

Set in the `env` of `cp-quota-sync-job-cron.yaml`:

| Variable | Default | |
|---|---|---|
| `CP_QUOTA_SYNC_VALUE` | `1000` | the quota value, in the billing currency |
| `CP_QUOTA_SYNC_PERIOD` | `MONTH` | `MONTH`, `QUARTER` or `YEAR` |
| `CP_QUOTA_SYNC_NOTIFY_THRESHOLDS` | `100,600` | comma-separated percentages, at least one; each one becomes a `NOTIFY` action |
| `CP_QUOTA_SYNC_DRY_RUN` | `false` | `true` logs the quotas it would create, and creates none |
| `CP_QUOTA_SYNC_LOG_LEVEL` | `INFO` | |
| `CP_QUOTA_SYNC_VERIFY_SSL` | see below | `true` or `false` — whether the API's TLS certificate is checked |

These only apply to the quotas the job creates. Changing them does not change existing quotas.

The API address and the admin token come from the `cp-config-global` ConfigMap
(`CP_API_SRV_INTERNAL_HOST`, `CP_API_SRV_INTERNAL_PORT`, `CP_API_JWT_ADMIN`). Set `API` and
`API_TOKEN` to override them.

The in-cluster API address serves a self-signed certificate, so the job does not check the certificate
for it. When `API` is set, the certificate is checked, because the address may be outside the
cluster. `CP_QUOTA_SYNC_VERIFY_SSL` overrides either default. Turn the check off for an outside
address only when you trust the network: the request carries an admin token.

The job runs the stock `python:3.12-slim` image from Docker Hub, on the node labelled
`cloud-pipeline/cp-api-srv=true`. If that node cannot reach Docker Hub, change `image` to a copy in a
registry it can reach. The manifest uses `batch/v1beta1`, which is what the Kubernetes version
installed by `pipectl` supports. On Kubernetes 1.25 or newer, change it to `batch/v1`.

## Deploy

`pipectl` does not deploy this job. Run these commands from this directory on the cluster's master
node:

```bash
kubectl create configmap cp-quota-sync-job-script --from-file=sync_quotas.py -n default
kubectl apply -f cp-quota-sync-job-cron.yaml
```

To update the script later, recreate the ConfigMap:

```bash
kubectl delete configmap cp-quota-sync-job-script -n default
kubectl create configmap cp-quota-sync-job-script --from-file=sync_quotas.py -n default
```

## Try it first

To see what the job would create without creating anything, run the script from any machine that
reaches the API, with an admin token:

```bash
API=https://<host>/pipeline/restapi/ API_TOKEN=<admin token> CP_QUOTA_SYNC_DRY_RUN=true \
    python3 sync_quotas.py
```

## Run it now and check

Without waiting for the schedule:

```bash
kubectl create job --from=cronjob/cp-quota-sync-job cp-quota-sync-job-manual -n default
kubectl logs -f job/cp-quota-sync-job-manual -n default
kubectl delete job cp-quota-sync-job-manual -n default
```

The log starts with how many users were found without a quota, and ends with how many quotas were
created and how many failed. The new quotas are
listed in the GUI, under **Billing** → **Quotas** → **Compute instances**.

## Tests

```bash
python3 -m unittest -v test_sync_quotas
```

They need only the standard library, and are not part of any CI run.
