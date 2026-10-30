# Edit bucket backup duration (re-opening the dialog)

Test verifies that an edited backup duration persists after re-opening the storage's edit dialog.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Check the **Enable versioning** checkbox (if not already checked) | The **Backup duration** field appears |
| 2 | Specify a natural number into the **Backup duration** field |  |
| 3 | Click **Save** button | The pop-up closes and the storage is updated with the value specified at step 2 as its backup duration |
| 4 | Click on the edit icon in the upper right corner again | The value specified at step 2 is displayed in the **Backup duration** field |
