# [MANUAL] Groups of instance types validation on tool setting form

Test verifies that the **Instance type** combo-box on the tool settings form groups the available instances by instance family.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Go to **Tools** |  |
| 3 | Click any tool |  |
| 4 | Go to **SETTINGS** |  |
| 5 | Click the **Instance type** combo-box | A dropdown list opens. The instance types are grouped in it - possible groups are:<br><ul><li>`General purpose`</li><li>`Compute optimized`</li><li>`Memory optimized`</li><li>`Storage optimized`</li><li>`Accelerated computing`</li><li>etc.</li></ul>Each group contains an own list of instance types. |
