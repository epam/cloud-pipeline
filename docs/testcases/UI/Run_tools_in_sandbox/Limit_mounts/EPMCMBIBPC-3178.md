# Run pipeline with sensitive limit mounts

Test verifies that launching a pipeline configured with both a sensitive and a non-sensitive limit-mount storage mounts exactly those two storages.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case |  |
| 2 | Click **Launch** button |  |
| 3 | Click **Launch** button in the appeared pop-up |  |
| 4 | On the **Runs** page click the just-launched run |  |
| 5 | On the **Run logs** page click the **Parameters** header | At the **Parameters** section, the following text is displayed:<br>`GENERAL`<br>`CP_CAP_LIMIT_MOUNTS: <sensitive_storage_name> <non-sensitive_storage_name>` where `<sensitive_storage_name>` and `<non-sensitive_storage_name>` are the storage names specified at steps 8 and 10 of the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case, respectively |
| 6 | Wait until the **SSH** link appears in the right upper corner |  |
| 7 | Click the **MountDataStorages** task at the left panel | The **MountDataStorages** task log is displayed containing the following text: <li> `Run is launched with mount limits (<sensitive_storage_id>,<non-sensitive_storage_id>) Only 2 storages will be mounted` where `<sensitive_storage_id>,<non-sensitive_storage_id>` equal the storage IDs specified at steps 8 and 10 of the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case <li> `Found 2 available storage(s). Checking mount options.` <li> `--><sensitive_storage_name> mounted to /cloud-data/<sensitive_storage_name>` where `<sensitive_storage_name>` equals the storage name specified at step 8 of the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case <li> `--><non-sensitive_storage_name> mounted to /cloud-data/<non-sensitive_storage_name>` where `<non-sensitive_storage_name>` equals the storage name specified at step 10 of the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case |
| 8 | Click the **SSH** link |  |
| 9 | In the appeared terminal tab input: `ls /cloud-data/` |  |
| 10 | Press **Enter** key | Only two storage names are displayed - the names specified at steps 8 and 10 of the [EPMCMBIBPC-3177](EPMCMBIBPC-3177.md) case |
| 11 | Close the terminal tab |  |

**After**:
- Stop the run
