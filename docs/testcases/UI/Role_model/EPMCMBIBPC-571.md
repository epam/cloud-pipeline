# [MANUAL] Check tools page user with access permission on registry and deny on group

Test verifies that a user granted access on a registry, but with the group inside it inaccessible, sees no groups configured.

**Prerequisites**:
- The user, and the group they belong to, must have no permissions on any Docker registries or the groups within them

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the administrator |  |
| 2 | Grant the user permissions on the **Auto_EPM-CMBI_Test3@epam.com** registry only |  |
| 3 | Login as the **Auto_EPM-CMBI_Test3@epam.com** user |  |
| 4 | Open the **Tools** page | The message "No groups configured" is displayed |
