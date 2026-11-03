# [MANUAL] Duplicated parameter name validation (negative)

Test verifies that entering the same invalid name into several parameter name fields reports the validation error for each field.

**Preparations**:
1. Complete the [EPMCMBIBPC-1446](EPMCMBIBPC-1446.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | For each parameter name field, click the **Name** field |  |
| 2 | Enter the same invalid name (e.g. `@#$ ,^^`) |  |
| 3 | Click off the field | <li> error message `Name can contain only letters, digits and '_'.` appears under the first name field <li> error message `Name can contain only letters, digits and '_'., No duplicates are allowed` appears under every name field except the first <li> the names are kept in the fields |
