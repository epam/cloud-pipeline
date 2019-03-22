workflow HelloWorld {
  call HelloWorld_print

    scatter(myscatter in) {
        call MyTask
    }
}

task HelloWorld_print {
  command {
    pipe_log SUCCESS "Running WDL pipeline" "Task1"
  }
}

task MyTask {
}