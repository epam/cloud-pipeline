# Prepare limit mounts

Test verifies the "Select data storages to limit mounts" pop-up's default state and single -storage selection for a non-sensitive-only setup.

**Prerequisites**:
- Login as admin

**Preparations**:
1. Open the **Library** page
2. Hover over **+ Create v** button -> click **Storages** item in the appeared list
3. Specify a valid storage name in the appeared pop-up
4. Click **Create** button
5. Repeat steps 1-4
6. Open the **Tools** page, select the tool
7. Open the tool settings
8. Disable the **Allow sensitive storages** checkbox if enabled
9. Save changes

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the tool from step 6 of the Preparations |  |
| 2 | Hover over the **Run v** button -> click **Custom settings** in the appeared list |  |
| 3 | Expand the **Advanced** section | In the **Limit mounts** field, the text `All available non-sensitive storages` is displayed |
| 4 | Click the field next to the **Limit mounts** label | The `Select data storages to limit mounts` pop-up appears, containing: <li> a search field <li> **Select all**, **Select all non-sensitive** button (disabled) <li> **Clear selection**, **Cancel**, **OK** buttons (enabled) <li> a table with available data storages: at least 2 records with the names of the storages created in the Preparations |
| 5 | Click the **Clear selection** button | <li> **Select all**, **Select all non-sensitive** buttons become enabled <li> the **Clear selection** button doesn't display <li> the **OK** button is disabled |
| 6 | Input into the search field the name specified at step 3 of the Preparations |  |
| 7 | Set the checkbox in front of the row that contains the storage name specified at step 3 of the Preparations | <li> the **Clear selection** button is displayed <li> the **OK** button is enabled |
| 8 | Click **OK** button | In the **Limit mounts** field, the name of the storage specified at step 3 of the Preparations is displayed |
