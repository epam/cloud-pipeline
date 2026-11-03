# Case sensitive validation in the storage

Test verifies that folder names in a storage are case-sensitive, so `test` and `TEST` coexist as distinct folders.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button, click **Folder** in the appeared list |  |
| 3 | Input `test` as a folder name in the appeared window, click **OK** button | The `test` folder appears |
| 4 | Repeat steps 2-3 but use `TEST` as the folder name | The `TEST` folder appears |
| 5 | Click the delete icon opposite the `test` folder |  |
| 6 | Click **OK** button in the appeared window | <li> the `test` folder doesn't display <li> the `TEST` folder is displayed |
