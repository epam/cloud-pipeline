# [MANUAL] User own tool in personal group validation

Test verifies the UI available to a user for their own tool in their personal tool group, with no other registry/group/tool permissions.

**Prerequisites**:
- The user has their own tool in the Auth registry -> personal tool group
- The user, and the group they belong to, have no permissions on registries, tool groups, or other tools
- The user has no roles that grant access to registries, tool groups, or other tools

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat the action steps of the [EPMCMBIBPC-1617](EPMCMBIBPC-1617.md) case, with the tool located in the user's personal group | <li>After action step 3: the **EDIT** button is located opposite **Short description**; the **EDIT** button is located opposite **Full description**; the **Show attributes**, settings, and **Run** buttons are located in the upper-right corner <li>After action step 4, the **Run** and delete buttons are located opposite every version in the list <li>After action step 8, the changes are saved <li>After action step 11, the "Select user" pop-up appears <li>After action step 13, the "Select group" pop-up appears |
