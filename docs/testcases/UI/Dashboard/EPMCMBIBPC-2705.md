# [MANUAL] Configure widgets

Test verifies enabling, disabling, and restoring the default set of Home page dashboard widgets.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the checkboxes near the following items: **Activities**, **Data**, **Notifications**, **Tools**, **Pipelines**, **Projects**, **Recently completed runs**, **Active runs**, **Services** |  |
| 4 | Click **OK** | The following widgets appear: **ACTIVE RUNS**, **ACTIVITIES**, **DATA**, **NOTIFICATIONS**, **PIPELINES**, **PROJECTS**, **RECENTLY COMPLETED RUNS**, **SERVICES**, **TOOLS** |
| 5 | Click the close ("X") button in the upper-right corner of the **ACTIVE RUNS** widget | The **ACTIVE RUNS** widget disappears |
| 6 | Repeat step 5 for the **TOOLS** widget | The **TOOLS** widget disappears |
| 7 | Repeat step 2 | In the **Configure dashboard** pop-up: <li>the following checkboxes are unset: **Active runs**, **Tools** <li>the following checkboxes are set: **Activities**, **Data**, **Notifications**, **Pipelines**, **Projects**, **Recently completed runs**, **Services** |
| 8 | In the pop-up that appears, unset the checkboxes near the following items: **Activities**, **Notifications**, **Pipelines**, **Projects**, **Recently completed runs**, **Services** | The checkbox near **Data** is set, but disabled |
| 9 | Click **OK** | <li>The **DATA** widget is displayed <li>The close ("X") button in the upper-right corner of the **DATA** widget is not displayed <li>The **ACTIVE RUNS**, **ACTIVITIES**, **NOTIFICATIONS**, **PIPELINES**, **PROJECTS**, **RECENTLY COMPLETED RUNS**, **SERVICES**, **TOOLS** widgets are not displayed |
| 10 | Repeat step 2 |  |
| 11 | In the pop-up that appears, click **Restore default layout** |  |
