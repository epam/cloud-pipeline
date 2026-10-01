# [MANUAL] Parameter name validation (negative)

Test verifies that entering an invalid parameter name reports a validation error that persists after clicking off the field.

**Preparations**:
1. Complete the [EPMCMBIBPC-1446](EPMCMBIBPC-1446.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | For each name field, click the **Name** field |  |
| 2 | Enter a unique invalid name (e.g. `new name_!@#`) |  |
| 3 | Click off the field | <li> error message `Name can contain only letters, digits and '_'.` appears <li> the error message stays after clicking off the field <li> the incorrect name is kept in the field |
