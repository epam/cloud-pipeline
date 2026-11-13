# Link storage with their URLs and not only with their IDs

Test verifies that it's possible to limits number of S3 buckets mounted to an instance, defining buckets using their names instead of their IDs via CP_CAP_LIMIT_MOUNTS parameter.

**Prerequisites**:
- Admin user

**Preparations**
1. Login as admin user from the prerequisites
2. Create 2 object storages with lowercase names (`test-storage1`, `test-storage2`)
3. Open each storage created at step 2 and create the file `test_file1.txt` in it
4. Create pipeline (`test_pipeline1`)

| Steps | Actions | Expected results |
|:---:|---|---|
| 1 | Open the **Library** page | |
| 2 | Open the pipeline created at step 4 of the preparations | |
| 3 | Click the pipeline draft version | |
| 4 | Click the **CODE** tab | |
| 5 | Click the `config.json` file | |
| 6 | Click the **EDIT** button | |
| 7 | Insert the following code into the `parameters` section of the `default` configuration: <br> `"CP_CAP_LIMIT_MOUNTS" : {` <br> `"value" : "<test_storage1_id>,<test_storage2_name>",` <br> `"type" : "string",` <br> `"required" : false` <br> `}` <br> where `<test_storage1_id>` - `test-storage1` id ,`<test_storage2_name>` - `test-storage2` name | |
| 8 | Click the **SAVE** button | |
| 9 | Specify the commit message, commit changes | |
| 10 | Click the **RUN** button | |
| 11 | Expand the **Advanced** section, set **Price type** to `On-demand`. Launch the run | |
| 12 | At the **Runs** page, click the just-launched run | |
| 13 | Wait until the **MountDataStorages** task appears and finishes successfully. The **SSH** hyperlink may not appear: it is shown only while the pipeline's script runs, which takes a few seconds | |
| 14 | Click the **MountDataStorages** task | The run log contains: <li> `Run is launched with mount limits (<test_storage1_id>,<test_storage2_name>) Only 2 storages will be mounted` <li> `--><test_storage1_name> mounted to /cloud-data/<test_storage1_name>` <li> `--><test_storage2_name> mounted to /cloud-data/<test_storage2_name>` |
| 15 | Expand the **Parameters** section | The **Parameters** section contains `CP_CAP_LIMIT_MOUNTS:` with two storage links, `<test_storage1_name>` and `<test_storage2_name>` |

**After:**
- Stop the run launched at step 11, if it is still running
- Remove the storages created at step 2 of the preparations, and the pipeline created at step 4 of the preparations
