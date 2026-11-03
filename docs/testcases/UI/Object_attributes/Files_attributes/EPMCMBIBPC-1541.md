# [MANUAL] Search field clearance after navigate to another entity

Test verifies that the metadata search input is cleared when switching to another entity.

**Prerequisites**:
- The user must have the ROLE_ADMIN or ROLE_ENTITIES_MANAGER role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a folder |  |
| 2 | Open the folder |  |
| 3 | Click the **Upload metadata** button |  |
| 4 | Select a valid metadata file |  |
| 5 | Open the metadata |  |
| 6 | Select any entity |  |
| 7 | Enter any value into the search input |  |
| 8 | Select another entity | The search input string is cleared |
