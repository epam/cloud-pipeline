# [MANUAL] Validation of access to NFS from container

Test verifies that files written from a running tool's SSH session to the mounted NFS path are visible in the corresponding storage afterward.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2301](EPMCMBIBPC-2301.md) |  |
| 2 | Click **SSH** |  |
| 3 | In the new tab, run the commands: `cd /mnt/{NFS_STORAGE_NAME}` and `echo "test" > "test.txt"` |  |
| 4 | Close the tab |  |
| 5 | Stop the tool run |  |
| 6 | Open the storage created in the [EPMCMBIBPC-2274](EPMCMBIBPC-2274.md) case | The storage displays the file `test.txt` containing the line `test` |
