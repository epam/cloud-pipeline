# Rerun pipeline without limit mounts

Test verifies that clearing the limit-mount storage on a rerun mounts all available storages instead of just the previously limited one.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2682](EPMCMBIBPC-2682.md) case |  |
| 2 | Open the **Run logs** page of the run launched at step 1 |  |
| 3 | Click **STOP** button in the right upper corner |  |
| 4 | Click **STOP** button in the appeared pop-up |  |
| 5 | Wait until the **RERUN** button appears in the right upper corner |  |
| 6 | Click **RERUN** button |  |
| 7 | On the appeared **Launch** page expand the **Advanced** section | In the **Limit mounts** field, the name of the storage created at step 4 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case is displayed |
| 8 | Click the **Limit mounts** field |  |
| 9 | Click the **Select all non-sensitive** button in the appeared pop-up |  |
| 10 | Click **OK** button | In the **Limit mounts** field, the text `All available non-sensitive storages` is displayed |
| 11 | Click **Launch** button |  |
| 12 | Click **Launch** button in the appeared pop-up |  |
| 13 | On the **Runs** page click the just-launched run | The **Parameters** section doesn't display |
| 14 | On the **Run logs** page wait until the **SSH** link appears in the right upper corner |  |
| 15 | Click the **MountDataStorages** task at the left panel | The **MountDataStorages** task log is displayed containing the following text: <li> `Found {storages_count} available storage(s). Checking mount options.` where `{storages_count} > 1` <li> the text `Run is launched with mount limits` is not displayed <li> `-->{storage_name} mounted to /cloud-data/{storage_name}` where `{storage_name}` equals the storage name specified at step 3 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case <li> `-->{storage_name2} mounted to /cloud-data/{storage_name2}` where `{storage_name2}` equals the storage name created at step 5 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case |
| 16 | Click the **SSH** link |  |
| 17 | In the appeared terminal tab input: `ls /cloud-data/` |  |
| 18 | Press **Enter** key | The storage names created at steps 4 and 5 of the Preparations of the [EPMCMBIBPC-2681](EPMCMBIBPC-2681.md) case are displayed |
| 19 | Close the terminal tab |  |
