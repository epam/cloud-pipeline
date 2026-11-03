# Run pipeline with limit mounts

Test verifies that launching a pipeline configured with a single limit-mount storage mounts only that storage.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case |  |
| 2 | Click **Launch** button |  |
| 3 | Click **Launch** button in the appeared pop-up |  |
| 4 | On the **Runs** page click the just-launched run |  |
| 5 | On the **Run logs** page click the **Parameters** header | At the **Parameters** section, the following text is displayed:<br>`GENERAL`<br>`CP_CAP_LIMIT_MOUNTS: {storage_name}` where `{storage_name}` equals the storage name specified at step 3 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case |
| 6 | Wait until the **SSH** link appears in the right upper corner |  |
| 7 | Click the **MountDataStorages** task at the left panel | The **MountDataStorages** task log is displayed containing the following text: <li> `Run is launched with mount limits ({storage_number_id}) Only 1 storages will be mounted` where `{storage_number_id}` equals the ID of the storage specified at step 3 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case <li> `Found 1 available storage(s). Checking mount options.` <li> `-->{storage_name} mounted to /cloud-data/{storage_name}` where `{storage_name}` equals the storage name specified at step 3 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case |
| 8 | Click the **SSH** link |  |
| 9 | In the appeared terminal tab input: `ls /cloud-data/` |  |
| 10 | Press **Enter** key | <li> the storage name created at step 4 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case is displayed <li> the storage name created at step 5 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case is not displayed |
| 11 | Close the terminal tab |  |
