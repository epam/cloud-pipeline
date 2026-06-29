import cloudPipelineApi from '../cloud-pipeline-api.ts';

export type ScheduleRule = {
  scheduleId?: number;
  action: string;
  cronExpression: string;
  timeZone?: string;
};

type RawScheduleRule = Omit<ScheduleRule, 'scheduleId'> & {id?: number};

export async function loadConfigurationSchedule(configurationId: number): Promise<ScheduleRule[]> {
  const raw = await cloudPipelineApi.jsonGet<RawScheduleRule[]>({
    uri: `schedule/configuration/${configurationId}`,
  });
  return (raw ?? []).map(({id, ...rest}) => ({scheduleId: id, ...rest}));
}

export async function createConfigurationSchedule(
  configurationId: number,
  rules: ScheduleRule[],
): Promise<void> {
  await cloudPipelineApi.jsonPost<void>({
    uri: `schedule/configuration/${configurationId}`,
    body: rules,
  });
}

export async function updateConfigurationSchedule(
  configurationId: number,
  rules: ScheduleRule[],
): Promise<void> {
  await cloudPipelineApi.jsonPut<void>({
    uri: `schedule/configuration/${configurationId}`,
    body: rules,
  });
}

export async function removeConfigurationSchedule(
  configurationId: number,
  rules: ScheduleRule[],
): Promise<void> {
  await cloudPipelineApi.jsonDelete<void>({
    uri: `schedule/configuration/${configurationId}`,
    body: rules,
  });
}
