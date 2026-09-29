# [MANUAL] Path parameter browse button validation

Test verifies that the browse pop-up for a path parameter shows the folder/file selection controls.

**Prerequisites**:
- The user (or a group the user is in) has an available storage with folders and/or files
- The [EPMCMBIBPC-1446](EPMCMBIBPC-1446.md) case is complete

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | For each path parameter, click the parameter icon (browse button) | A pop-up appears that contains: <li> the **Select folder or file** sign <li> **Clear selection**, **Cancel**, **OK** buttons <li> a file tree of the available folders, files and storages <li> a file list of the selected tree member <li> **Name**, **Size**, **Date changed** column headers in the file list |
