# [MANUAL] Validation of "Add system parameter" button on launch form

Test verifies adding a pre-existing system parameter on the launch form and browsing to a path for its value.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-1676](EPMCMBIBPC-1676.md) case |  |
| 2 | Click **Run** |  |
| 3 | Click the collapse header **Advanced** | The system variable added in the [EPMCMBIBPC-1676](EPMCMBIBPC-1676.md) case is displayed |
| 4 | Click the **Add system parameter** button | The system-variable selection pop-up appears |
| 5 | Select `param2` | The `Select folder or file` pop-up for selecting a folder/file in the storage appears |
| 6 | Click the **folder** icon |  |
| 7 | Select a folder/file in the storage | The path to the selected folder/file in the storage is displayed for the `param2` variable |
