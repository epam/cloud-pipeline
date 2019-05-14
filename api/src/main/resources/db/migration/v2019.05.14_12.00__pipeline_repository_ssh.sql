ALTER TABLE pipeline.pipeline ADD repository_ssh text NULL;

-- Fills repository_ssh with repository altering it with the following pattern:
--  Repository https url: [https://]cp-git.default.svc.cluster.local[:30080/]root/gitlabsshpipeline.git
--  Repository ssh url:   [git@    ]cp-git.default.svc.cluster.local[:      ]root/gitlabsshpipeline.git
UPDATE pipeline.pipeline SET repository_ssh = REGEXP_REPLACE(REPLACE(repository, 'https://', 'git@'), ':\d+/', ':');

ALTER TABLE pipeline.pipeline ALTER COLUMN repository_ssh SET NOT NULL;
