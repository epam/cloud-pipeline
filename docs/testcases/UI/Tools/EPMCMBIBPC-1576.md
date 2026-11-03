# [NEGATIVE] Create group with invalid name

Test verifies that creating a tool group with an invalid name is rejected with a validation error.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Click **Settings** button (gear icon) |  |
| 4 | Select **Group** → **Create** in the appeared list |  |
| 5 | Specify `!@#$%^&*()[]{}` into the **Name** field |  |
| 6 | Click **CREATE** button | Error message `Name can contain only lowercase letters, digits '-' and '.'.` appears |
