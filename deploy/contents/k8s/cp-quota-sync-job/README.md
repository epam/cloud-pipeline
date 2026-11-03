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
    - the user is the recipient of the notifications, followed by any
      [extra recipients](#extra-recipients)
    - `NOTIFY` at `100%` and at `600%`

It never updates or deletes a quota. Blocked users get a quota too.
The job fails if any quota could not be created. The users who got one keep it.

A notification is sent once for each threshold in each period, and only to the quota's recipients.
The "Keep admins informed", "Keep owners informed" and "Informed users" settings of the
`BILLING_QUOTA_EXCEEDING` notification are not applied to it, so an admin who wants a copy has to be
a recipient. For it to arrive:

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
| `CP_QUOTA_SYNC_EXTRA_RECIPIENTS` | empty | comma-separated users, roles and groups added to each new quota's recipients — see [Extra recipients](#extra-recipients) |
| `CP_QUOTA_SYNC_DRY_RUN` | `false` | `true` logs the quotas it would create, and creates none |
| `CP_QUOTA_SYNC_LOG_LEVEL` | `INFO` | |
| `CP_QUOTA_SYNC_VERIFY_SSL` | see below | `true` or `false` — whether the API's TLS certificate is checked |

These only apply to the quotas the job creates. Changing them does not change existing quotas.

### Extra recipients

Each entry of `CP_QUOTA_SYNC_EXTRA_RECIPIENTS` is one of:

| Entry | Means |
|---|---|
| `USER:<name>` | a user, by user name |
| `ROLE:<name>` | every member of a role (`ROLE:ROLE_ADMIN`) or of a user group (`ROLE:CORA-DEV`) |
| `<name>` | a role if it starts with `ROLE_`, a user otherwise |

For example, `ROLE_ADMIN,USER:JOHN.DOE@EXAMPLE.COM` copies every admin and one more user on each new
quota. A role or group is resolved into its members when a notification is sent, so a user who joins
it later gets the next notification too.

A user is checked against the platform's users, ignoring case. An unknown user stops the run before
any quota is created, since the job never corrects a quota it has created. A role or group name is
upper-cased, as the platform stores it, but is not checked, so make sure it is spelt right — a dry run
shows the recipients each quota would get. A recipient named twice is added once.

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

## Delete quotas

`delete_quotas.py` deletes quotas — those of one quota group and/or type, or all of them. The job
does not run it: run it by hand, from this directory, on a machine that reaches the API. It needs
`sync_quotas.py` beside it.

It only lists the quotas it would delete, unless `CP_QUOTA_DELETE_DRY_RUN` is `false`. So run it twice.
To delete the quotas this job creates:

```bash
# list
API=https://<host>/pipeline/restapi/ API_TOKEN=<admin token> \
    CP_QUOTA_DELETE_GROUP=COMPUTE_INSTANCE CP_QUOTA_DELETE_TYPE=USER python3 delete_quotas.py
# delete
API=https://<host>/pipeline/restapi/ API_TOKEN=<admin token> \
    CP_QUOTA_DELETE_GROUP=COMPUTE_INSTANCE CP_QUOTA_DELETE_TYPE=USER CP_QUOTA_DELETE_DRY_RUN=false \
    python3 delete_quotas.py
```

| Variable | Default | |
|---|---|---|
| `CP_QUOTA_DELETE_GROUP` | any | `GLOBAL`, `COMPUTE_INSTANCE` or `STORAGE` |
| `CP_QUOTA_DELETE_TYPE` | any | `OVERALL`, `BILLING_CENTER`, `USER` or `GROUP` |
| `CP_QUOTA_DELETE_ALL` | `false` | `true` selects **every** quota — storage, group, billing center and global ones too. It cannot be combined with a filter |
| `CP_QUOTA_DELETE_DRY_RUN` | `true` | only `false` deletes |

A group, a type or `CP_QUOTA_DELETE_ALL=true` is required: a run without any of them selects nothing
and fails. Any other `CP_QUOTA_DELETE_*` variable fails the run too, so a misspelt filter is never
ignored.

The API settings — `API`, `API_TOKEN`, `CP_QUOTA_SYNC_VERIFY_SSL` — are the job's. Set
`CP_QUOTA_SYNC_VERIFY_SSL=false` for an address with a self-signed certificate, on a network you trust.

Before you delete, know what it costs:

- deleting a quota deletes its applied actions, so the restrictions they put in place end at once: a
  user blocked by a quota can log in again, and one whose new jobs were disabled can launch them
- the job recreates only personal compute quotas (`COMPUTE_INSTANCE` / `USER`). Any other quota you
  delete is gone for good
- a recreated quota has no applied actions, so a user already over a threshold gets that threshold's
  notification again, on the next quota check
- users have no quota until the job runs again — [run it now](#run-it-now-and-check) to recreate the
  quotas at once
- a quota changed by hand comes back with the job's defaults

## Tests

```bash
python3 -m unittest -v test_sync_quotas test_delete_quotas
```

They need only the standard library, and are not part of any CI run.
