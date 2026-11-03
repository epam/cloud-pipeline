# [MANUAL] Validation of short cut information of custom run form tabs

Test verifies that the collapsed **Exec environment** and **Advanced** sections of the custom settings form show a short summary of their field values.

**Prerequisites**:
- The tool has an executable command filled in

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the tool |  |
| 2 | Press the arrow button located near the **Run** button |  |
| 3 | Click **Custom settings** |  |
| 4 | Look at the lines opposite **Exec environment** and **Advanced** |  |
| 5 | Open the **Advanced** and **Exec environment** tabs | The represented fields are filled in with the information observed at step 4 |
| 6 | Change the **Docker image**, **Instance type**, **Disk (Gb)**, **Price type**, **Timeout (min)**, **Cmd template** field content |  |
| 7 | Close the **Advanced** and **Exec environment** tabs |  |
| 8 | Look at the lines opposite **Exec environment** and **Advanced** | All the information entered at step 6 is represented |
| 9 | Open the **Advanced** tab |  |
| 10 | Check the **Start idle** checkbox |  |
| 11 | Close the **Advanced** tab |  |
| 12 | Look at the lines opposite **Advanced** | **Start idle** is represented instead of the **Cmd template** content |
