# [MANUAL] Check of the content of the scatter Properties panel

Test verifies that the Properties panel for a scatter object has no editable Name field.

**Prerequisites**:
- At least one version of a WDL pipeline is present

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the last version of the WDL pipeline |  |
| 2 | Switch to **GRAPH** tab |  |
| 3 | Click **PROPERTIES** button |  |
| 4 | Click **Actions** button -> **Add scatter** |  |
| 5 | Save and commit |  |
| 6 | Refresh page |  |
| 7 | Select the just-created scatter | The **Properties** panel for the selected scatter appears, containing: **Actions** button, a **Scatter item** collapsed header, a **Remove** button; no **Name** field is displayed |
