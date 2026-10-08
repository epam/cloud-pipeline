# [MANUAL] Validation of estimated price availability of empty tool

Test verifies that no estimated price is shown on the launch page for a tool with empty execution settings.

**Prerequisites**:
- User can execute at least one empty tool of a group

**Preparations**:
1. Press **Tools** button
2. Select registry
3. Select group
4. Select a tool that is executable for the user
5. Make sure the **Instance type** and **Disk** fields are empty
6. Open the dropdown list on the right of the **Run** button
7. Press **Custom settings**

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Look at the left upper corner of the page | <li> the `Launch CMD` sign is present <li> no estimated price is present <li> no information icon is present |
