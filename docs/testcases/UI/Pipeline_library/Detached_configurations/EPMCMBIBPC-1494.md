# Select docker version validation for detach config

Test verifies selecting a non-latest tool version for a detached configuration's Docker image.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the configuration from the prerequisites |  |
| 2 | Click the wrench icon opposite the **Docker image** field |  |
| 3 | Select the registry, group and tool |  |
| 4 | Select something other than **latest** in the combobox opposite the tool name |  |
| 5 | Click **OK** button | The **Docker image** field shows text in the format `{docker_registry_address}/{group_name}/{docker_image_name}:{tool_version}` |
