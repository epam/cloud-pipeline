# Autoscaling cluster configuration

Test verifies that utility **sge** allows to dynamically configure grid engine autoscaling and create additional grid engine queues for already running clusters 

**Prerequisites:**
- Admin user

| Steps | Actions | Expected results |
|:---:|--- |---|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Launch a selected tool with **Custom settings** | |
| 5 | Expand the **Exec environment** section | |
| 6 | Click on ***Configure cluster*** link | |
| 7 | Select ***Auto-scaled cluster*** tab | |
| 8 | Specify `1` into *Auto-scaled up to:* field. Tick *Enable GridEngine* checkbox. Click **OK** button | |
| 9 | Expand **_Advanced_** section and set **_Price type_** to `On-demand` if needed. | |
| 10 | Start a tool with a **Launch** button | |
| 11 | Open the just-launched run (`parent_run_ID`) | |
| 12 | Wait until the **SSH** hyperlink appears | |
| 13 | Click the **SSH** hyperlink | |
| 14 | Enter and perform the command: `sge list` | Response contains: <br> `Initiating grid engine profiles listing...` <br> `Grid engine profile has been found: main.q` |
| 15 | Enter and perform the command: `sge create testprofile.q` | Response contains template of the profile's configuration file opened in a text editor |
| 16 | Close text editor | |
| 17 | Enter and perform the command: `sge list` | Response contains <br> `Grid engine profile has been found: testprofile.q` |
| 18 | Enter and perform the command: `sge configure testprofile.q` | Template of the profile's configuration file is opened in a text editor |
| 19 | Uncomment and set incorrect value `nontrue` for the `CP_CAP_AUTOSCALE` parameter. Save configuration file | The changes are reverted, and messages `Boolean parameter CP_CAP_AUTOSCALE has invalid value nontrue. Please specify true/false/yes/no/on/off. `, `Grid engine profile verification has failed. Reverting the changes...` are shown. |
| 20 | Enter and perform the command: `sge configure testprofile.q` | Template of the profile's configuration file is opened in a text editor |
| 21 | Uncomment and set following values: <br> `export CP_CAP_AUTOSCALE="true"` <br> `export CP_CAP_AUTOSCALE_WORKERS="3"` <br> `export CP_CAP_AUTOSCALE_HYBRID="true"` <br> `export CP_CAP_AUTOSCALE_HYBRID_FAMILY="c5"` <br> `export CP_CAP_AUTOSCALE_INSTANCE_DISK="45"` <br> `export CP_CAP_AUTOSCALE_INSTANCE_IMAGE="<other_test_tool>"`. <br> Save configuration file | Text editor is closed. Log contains `Grid engine testprofile.q autoscaling has been launched.` message. |
| 22 | Enter and perform the command: `qsub -b y -q testprofile.q -pe local 4 -t 1:2 sleep 5m` | Response contains `Your job 1 ("sleep") has been submitted` message |
| 23 | Open run log page. Select `SGEProfiles` task | Log contains rows: <br> `> export CP_CAP_AUTOSCALE="true"` <br> `> export CP_CAP_AUTOSCALE_WORKERS="3"` <br> `> export CP_CAP_AUTOSCALE_HYBRID="true"` <br> `> export CP_CAP_AUTOSCALE_HYBRID_FAMILY="c5"` <br> `> export CP_CAP_AUTOSCALE_INSTANCE_DISK="45"` <br> `> export CP_CAP_AUTOSCALE_INSTANCE_IMAGE="<other_test_tool>"`. <br> `Grid engine testprofile.q autoscaling has been launched.` | 
| 24 | Wait until the label *Nested runs* appears. |  |
| 25 | Click on the `<child_runID>` in the **_Nested runs_** list | Child Run Log page opens |
| 26 | Expand the **Parameters** section | The **Parameters** section contains parameters: `CP_CAP_SGE_HOSTLIST_NAME: @testprofile.q` <br> `CP_CAP_SGE_QUEUE_NAME: testprofile.q` |
| 27 | Expand the **Instance** section. Check values for *Node type*, *Disk* and *Docker image* | Values for *Node type*, *Disk* and *Docker image* correspond values specified in profile configure file at step 21 |

**After:**
- Stop run launched in case
