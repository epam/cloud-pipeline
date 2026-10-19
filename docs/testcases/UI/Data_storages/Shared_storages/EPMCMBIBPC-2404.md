# [MANUAL] Validation of delete files and folders on shared bucket

Test verifies canceling and confirming deletion of a file and a folder on a shared bucket.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case |  |
| 2 | Click the **Remove** icon near the file uploaded at step 6 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case |  |
| 3 | Click the **Cancel** button in the pop-up | The file uploaded at step 6 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case is displayed |
| 4 | Repeat step 2 |  |
| 5 | Click the **OK** button in the pop-up | The file uploaded at step 6 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case isn't displayed |
| 6 | Click the **Remove** icon near the folder created at step 4 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case |  |
| 7 | Click the **Cancel** button in the pop-up | The folder created at step 4 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case is displayed |
| 8 | Repeat step 6 |  |
| 9 | Click the **OK** button in the pop-up | The folder created at step 4 of the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case isn't displayed |
