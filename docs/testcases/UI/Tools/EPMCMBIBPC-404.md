# Switch between groups validation

Test verifies switching between tool groups from the group dropdown on the **Tools** page.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry (if it's not selected) |  |
| 3 | Click a group name | A dropdown list with the accessible groups appears |
| 4 | Select a group in the appeared list | <li> the tools list with names, short descriptions and labels is displayed <li> the name of the group selected at step 4 is shown instead of **personal** <li> a tool search field, **Show attributes** button and **Settings** button are displayed |
