# [MANUAL] Validation of NFS mount remove

Test verifies unregistering an NFS mount and re-creating a storage at the same path.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2303](EPMCMBIBPC-2303.md) |  |
| 2 | Click on the gear icon |  |
| 3 | Remember the value of the **Storage path** field |  |
| 4 | Click **Delete** button |  |
| 5 | Click **Delete** button in the appeared confirmation | The storage is not displayed on the **Library** page |
| 6 | Click **Create** button |  |
| 7 | Hover over **Storage** |  |
| 8 | Click **Create new NFS mount** |  |
| 9 | Enter the string remembered at step 3 into the **Storage path** field |  |
| 10 | Click **Create** button |  |
| 11 | Click on the created storage | An empty storage is displayed |
