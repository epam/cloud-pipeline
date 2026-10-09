# Search for detach config by configuration

Test verifies searching for a detached configuration by its inner configuration name.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the home icon in the left menu panel |  |
| 2 | Click the search icon in the left menu panel |  |
| 3 | Enter into the search query the configuration name specified at step 10 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 4 | Press **Enter** | A search result list appears with 1 item, with the name specified at step 3 |
| 5 | Hover in the search result list over the found item | A content panel appears, containing: <li>a header with: the configuration name specified at step 10 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case; the detached configuration name specified at step 6 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case <li>the **Found in entryName**, **Found in name** fields <li>the **Execution environment**, **Docker image**, **Instance type**, **Cloud region**, **Disk size (Gb)**, **Price type**, **Timeout (minutes)**, **Cmd template** fields |
| 6 | Click on the found item | The detached configuration created at step 6 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case opens |
