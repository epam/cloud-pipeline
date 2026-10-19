# [MANUAL] Clone project folder test, define path

Test verifies cloning a Project into a chosen destination folder rather than alongside the original.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button -> click **Folder** item in the appeared dropdown list |  |
| 3 | Specify a valid folder name in the appeared pop-up, click **OK** button |  |
| 4 | Perform steps 1-6 of the [EPMCMBIBPC-1938](EPMCMBIBPC-1938.md) case |  |
| 5 | In the **Select destination folder** pop-up select the folder created at step 3 |  |
| 6 | Specify a valid new name for the Project |  |
| 7 | Click **Clone into {folder_name}** button | A folder of the cloned Project is displayed that contains: <li> the **Method-Configurations** folder <li> a data storage with the name equal to the Project name specified at step 6 <li> the **History** object <li> attributes equal to the attributes saved at step 4; the cloned folder is on the path selected at step 3 |
