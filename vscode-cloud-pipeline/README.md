# Cloud Pipeline Remote

This extension brings **[Cloud Pipeline](https://cloud-pipeline.com)** into your development environment: you can see your active compute runs, start new ones from the tools your organization exposes, and open a run in a **remote development window** so you work on the machine where the job is running.

---

## What you need

- A Cloud Pipeline account your administrator has set up for you.
- To **connect** to a run and edit files on it, you also need a **Remote SSH** extension (the editor will prompt you to install the right one for Visual Studio Code or Cursor if it is missing).

---

## Signing in

When you are not signed in, the runs list shows **Sign in**, and a **Sign in** entry may appear in the **status bar**.

Sign-in uses your **web browser** and single sign-on. After you finish in the browser, the extension saves your session for future use.

If you already use the Cloud Pipeline command-line tools with a saved profile on this computer, the extension can use that existing sign-in instead.

---

## Your runs list

The list shows **runs that are currently running** for your user. Each row includes the run identifier, the tool or pipeline name, and the owner when shown.

The list **updates automatically** so you can see when something finishes starting or when a new run appears.

---

## Operations: start, connect, stop

- **Start** — Toolbar **Start new run**: pick a tool and version; the run appears in the list when it is up. You get a confirmation with the run id.
- **Connect** — When the run is ready for SSH: **Connect via SSH** from the context menu, or open the run from the tree (same as your list open mode). Opens a remote window on the machine. **Sensitive** runs cannot be connected.
- **Stop run** — Ends the job in Cloud Pipeline (with confirmation) and closes any SSH tunnel for that run.
- **Stop SSH Tunnel** — Closes only the local tunnel and remote session; the run keeps running. If nothing is selected, choose which tunnel to close.
