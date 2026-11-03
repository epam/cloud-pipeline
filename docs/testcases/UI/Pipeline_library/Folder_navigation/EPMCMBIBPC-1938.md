# [MANUAL] Clone project folder test

Test verifies cloning a Project into a new Project at the same level, preserving its attributes.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button -> click **PROJECT** item in the appeared dropdown list |  |
| 3 | Specify a valid Project name in the appeared pop-up, click **OK** button |  |
| 4 | Open the just-created Project |  |
| 5 | Save the values of the attributes |  |
| 6 | Hover over the gear icon in the right upper corner -> click **Clone** item in the appeared dropdown list |  |
| 7 | Specify a valid new name for the Project in the appeared pop-up |  |
| 8 | Click **Clone into {folder_name}** button | A folder of the cloned Project is displayed that contains: <li> the **Method-Configurations** folder <li> a data storage with the name equal to the Project name specified at step 7 <li> the **History** object <li> attributes equal to the attributes saved at step 5; the cloned folder is at the same level as the original Project created at step 3 |
