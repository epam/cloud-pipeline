# [MANUAL] Groups of instance types validation on launch form

Test verifies that the **Type** combo-box on the launch form groups the available instances by instance family.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user who has permissions to launch the pipeline |  |
| 2 | Click the pipeline |  |
| 3 | Click the **Launch** button |  |
| 4 | Click **Exec environment** |  |
| 5 | Click the **Type** combo-box | A dropdown list opens. The instance types are grouped in it - possible groups are:<br><ul><li>`General purpose`</li><li>`Compute optimized`</li><li>`Memory optimized`</li><li>`Storage optimized`</li><li>`Accelerated computing`</li><li>etc.</li></ul>Each group contains an own list of instance types. |