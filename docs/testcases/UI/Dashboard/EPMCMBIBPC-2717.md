# [MANUAL] TOOLS widget search

Test verifies that the TOOLS widget's search field surfaces a matching tool via global search, and reflects an emptied personal tools group.

**Preparations**:
1. Perform the [EPMCMBIBPC-2716](EPMCMBIBPC-2716.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | In the **TOOLS** widget, in the **Search tools** field, specify the name of the tool selected at step 1 of the EPMCMBIBPC-2716 case | In the **TOOLS** widget, a **Global_search** section appears that contains the tool selected at step 1 of the EPMCMBIBPC-2716 case |
| 3 | Open the **Tools** page, select a personal tools group |  |
| 4 | Hover over the gear icon in the upper-right corner -> select **Group** in the list that appears -> click **Delete** in the list that appears |  |
| 5 | In the pop-up that appears, set the **Delete child tools** checkbox, click **Delete** |  |
| 6 | Repeat step 1 | The message "There are no personal tools" is displayed in the **TOOLS** widget |
