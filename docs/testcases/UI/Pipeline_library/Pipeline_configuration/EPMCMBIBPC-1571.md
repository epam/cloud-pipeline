# [MANUAL] [NEGATIVE] Validation of editing new configuration

Test verifies that invalid values entered into a new configuration's numeric fields are rejected with error messages and are not saved.

**Preparations**:
1. Complete the [EPMCMBIBPC-796](EPMCMBIBPC-796.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the new configuration |  |
| 2 | Enter an invalid value into the **Name** field and press **Save** button: `{}`, `<empty_string>` |  |
| 3 | Switch to another configuration tab and back |  |
| 4 | Repeat steps 2-3 for the **Disk (Gb)**, **CPU**, **RAM**, **GPU** field, **Timeout** field using the values: `-20`, `<empty_string>`, `{}`, `asd`, `0`, `11,5` |  |
| 5 | Repeat steps 2-3 for the **CPU**, **RAM**, **GPU** field using the value `11.5` | <li> a corresponding error message appears under the field for every invalid input <li> after pressing **Save** nothing happens <li> after switching between configuration tabs all values revert to defaults <li> after pressing **Save** at step 5 the best match for **Instance type** is chosen automatically and saved |
