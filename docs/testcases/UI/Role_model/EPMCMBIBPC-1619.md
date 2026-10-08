# [MANUAL] Third-party tool in user personal group validation

Test verifies that a tool committed into a user's personal group is not viewable by that user without explicit permission.

**Prerequisites**:
- The user must have a personal group and permissions to run a tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Run any tool from the **Auth registry** |  |
| 3 | Commit the tool into the personal group from the prerequisites |  |
| 4 | Login as the user from the prerequisites |  |
| 5 | Click the **Tools** button at the navigation panel |  |
| 6 | Select the registry that contains the personal user group |  |
| 7 | Select the personal group in the address line |  |
| 8 | Click on the tool from the prerequisites | The message "You have no permissions to view tool details" appears on the opened page |
