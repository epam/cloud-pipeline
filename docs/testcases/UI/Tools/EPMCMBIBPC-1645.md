# Validation of first "group default" repository is opened

Test verifies that a user restricted to a single tool or group lands on that group by default when opening the **Tools** page.

**Prerequisites**:
- User does not have the **ROLE_TOOL_GROUP_MANAGER** role
- User does not have the **ROLE_ADMIN** role
- User does not have **WRITE** permission on the **Default** registry
- User has only **READ** permission on at least one tool or group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open **Tools** page | <li> tools group is displayed <li> gear icon in the right upper corner is displayed |
| 3 | Click the gear icon in the right upper corner | Dropdown list appears with the **Group** item |
| 4 | Select **Group** item in the appeared list | Dropdown list appears with the **+ Create personal** item |
