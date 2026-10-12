# [MANUAL] Validation of alias and description editing for NFS mount

Test verifies that canceling an alias/description edit on an NFS mount discards the change, and confirming it applies the change.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to an NFS storage |  |
| 2 | Click on the gear icon |  |
| 3 | Enter a new value into the **Alias** field |  |
| 4 | Enter a new value into the **Description** field |  |
| 5 | Click **Cancel** button | The values on the page don't change |
| 6 | Repeat steps 2-4 |  |
| 7 | Click **OK** button | The name and description change to the values entered at steps 3 and 4 |
