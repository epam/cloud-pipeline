# Validation of NFS mount unregister

Test verifies that unregistering and re-creating an NFS mount at the same path keeps its content.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create an NFS storage |  |
| 2 | Select the created storage |  |
| 3 | Create a folder and a file in the selected storage |  |
| 4 | Click on the gear icon at the top of the storage page |  |
| 5 | Copy the value of the **Storage path** |  |
| 6 | Click **Delete** button |  |
| 7 | Click **Unregister** button in the appeared pop-up window | The storage is not displayed on the library-tree on the left panel of the page |
| 8 | Click **+ Create v** button |  |
| 9 | Hover over **Storages** -> click **Create NFS mount** |  |
| 10 | Paste the value saved at step 5 into the **Storage path** field |  |
| 11 | Click **Create** button |  |
| 12 | Select the created storage | The file and folder that were created at step 3 are displayed in the storage |
