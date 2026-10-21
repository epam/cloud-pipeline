# Validation of fields editing prohibition

Test verifies that the Docker image and Cmd template fields, and the Start idle checkbox and Add parameter button, are disabled on a linked detach configuration.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1114](EPMCMBIBPC-1114.md) case |  |
| 2 | Open the detach configuration from step 1 |  |
| 3 | Try to edit the **Docker image** field | The **Docker image** field is disabled |
| 4 | Expand the **Advanced** section |  |
| 5 | Try to edit the **Cmd template** field | The **Cmd template** field is disabled |
| 6 | Try to set the **Start idle** checkbox | The **Start idle** checkbox's value isn't changed |
| 7 | Click on the **Add parameter** button | The **Add parameter** button is disabled |
