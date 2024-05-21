const configuration = {
  restAPI: 'https://<host>/pipeline/restapi',
  launch: {
    params: {
      fastqs: {
        storage: <storage_id>
      },
      transcriptome: {
        values: {
          human: 's3://genome-bucket/human/transcriptome',
          mouse: 's3://genome-bucket/mouse/transcriptome',
          'human-mouse': 's3://genome-bucket/human-mouse/transcriptome',
          tiny: 's3://genome-bucket/tiny/transcriptome'
        },
        default: 'tiny'
      },
      workdir: {
        storage: <storage_id>
      },
      instance: 'r5.xlarge',
      disk: 100
    },
    resultFile: (run) => {
      const {pipelineRunParameters = []} = run;
      const [results] = pipelineRunParameters.filter(p => /^results$/i.test(p.name));
      if (results) {
        const path = results.resolvedValue || results.value;
        if (path) {
          return `${path}/cloud-cellranger/outs/web_summary.html`;
        }
      }
      return null;
    }
  },
  launchOptionsFn: (options) => ({
    cmdTemplate: `cellranger count --id cloud-cellranger --fastqs ${options.fastqs} --transcriptome ${options.transcriptome}`,
    dockerImage: 'single-cell/cellranger:latest',
    force: true,
    hddSize: +options.disk,
    instanceType: options.instance,
    params: {
      fastqs: options.fastqs,
      transcriptome: options.transcriptome,
      results: options.workdir,
    }
  })
};

export default configuration;
