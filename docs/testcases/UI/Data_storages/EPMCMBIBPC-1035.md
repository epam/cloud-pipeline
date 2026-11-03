# Validation of navigation buttons in storage

Test verifies the **Next page**/**Previous page**/**Select page** navigation buttons behavior when a storage folder holds more items than fit on one page.

**Prerequisites**:
- The storage created in [EPMCMBIBPC-448](EPMCMBIBPC-448.md) is selected

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Hover the mouse pointer over the **+ Create v** button, select **Folder** item |  |
| 2 | Input a valid value into the **Name** field in the pop-up window |  |
| 3 | Click **OK** button |  |
| 4 | Repeat steps 2-3 41 times | The folders are sorted by name, the **Next page** button at the bottom becomes enabled |
| 5 | Click the enabled **Next page** button | Two folders are displayed |
| 6 | Click **Select page** button | The checkboxes opposite the folder names are checked |
| 7 | Click the enabled **Previous page** button | 40 folders are displayed, the checkboxes opposite their names are unchecked |
| 8 | Click the enabled **Next page** button | Two folders are displayed, the checkboxes opposite their names are checked |
