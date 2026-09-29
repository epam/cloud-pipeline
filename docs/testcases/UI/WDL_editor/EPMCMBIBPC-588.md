# Check buttons for edit wdl graph

Test verifies the buttons shown on the WDL graph diagram for a new WDL pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | In the appeared list select **Pipeline** item -> **WDL** item |  |
| 4 | Specify a valid pipeline name in the appeared pop-up |  |
| 5 | Click **CREATE** button |  |
| 6 | Click on the just-created pipeline |  |
| 7 | Click on the pipeline version |  |
| 8 | Click **GRAPH** tab | The WDL diagram appears, containing: <li> on the left side: **Save**, **Revert changes** buttons (disabled); **Layout**, **Fit to screen**, **Show links**, **Zoom in**, **Zoom out**, **Fullscreen** buttons (enabled) <li> on the right side: **PROPERTIES** button (enabled) |
