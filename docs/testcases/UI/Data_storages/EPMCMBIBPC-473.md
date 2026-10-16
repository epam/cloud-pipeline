# Edit bucket backup duration

Test verifies editing the bucket's backup duration.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Check the **Enable versioning** checkbox (if not already checked) | The **Backup duration** field appears |
| 2 | Specify a natural number into the **Backup duration** field |  |
| 3 | Click **Save** button |  |
| 4 | Click on the edit icon in the upper right corner again | The value specified at step 2 is displayed in the **Backup duration** field |
