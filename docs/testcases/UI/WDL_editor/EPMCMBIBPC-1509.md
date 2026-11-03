# [MANUAL] Check of the content of the edit scatter pop-up name field

Test verifies that the Edit scatter pop-up's Name field is pre-filled with the scatter's name.

**Prerequisites**:
- At least one version of a WDL pipeline is present

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the last version of the WDL pipeline |  |
| 2 | Switch to **GRAPH** tab |  |
| 3 | Click **+ ADD SCATTER** button |  |
| 4 | Set a valid name and value |  |
| 5 | Click **ADD** button |  |
| 6 | Save and commit |  |
| 7 | Refresh page |  |
| 8 | Select the just-created scatter |  |
| 9 | Click **EDIT** button | <li> the `Edit scatter <scatter name>` pop-up appears <li> `<scatter name>` is present in the **Name** field |
