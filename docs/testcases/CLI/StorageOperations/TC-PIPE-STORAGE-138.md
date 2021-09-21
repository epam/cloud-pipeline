# Change storage policy with disabled fields

**Actions**:
1. Create storage with disabled sts, lts, b, versioning enabled
2. Change sts and lts policy: `pipe storage policy -n storage_name -sts 20 -lts 30 -v`
3. Change sts, lts and backup duration policy: `pipe storage policy -n storage_name -sts 20 -lts 30 -b 10 -v`
4. Delete storage

***
**Expected result:**

1. Check with Cloud Provider's assert policy utility: sts and lts disabled, backup duration enabled (=20 as default)
2. Check with Cloud Provider's assert policy utility: sts and lts enabled, backup duration enabled (=20 as default) and ensure that values are correct
3. Check with Cloud Provider's assert policy utility: sts and lts enabled, backup duration enabled (=20 as default) and ensure that values are correct
