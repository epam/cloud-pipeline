# [MANUAL] Enable tool pop-up focus validation

Test verifies that the **Enable tool** pop-up opens with focus on the **Image** field and that pressing **Enter** confirms the entered tool name.

**Prerequisites**:
- Repeat the [EPMCMBIBPC-1404](EPMCMBIBPC-1404.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Tools** button |  |
| 2 | Select the registry and group used in [EPMCMBIBPC-1404](EPMCMBIBPC-1404.md) |  |
| 3 | Click the edit button (gear icon in the right upper corner) |  |
| 4 | Press **+ Enable tool** | **Enable tool** pop-up appears with focus on the **Image** field (cursor is already there, field edges are highlighted blue) |
| 5 | Start typing the tool name from [EPMCMBIBPC-1404](EPMCMBIBPC-1404.md) |  |
| 6 | Press **Enter** key when finished | After the **Enter** key is pressed, the tool appears in the corresponding registry → group |
