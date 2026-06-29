import {useCallback, useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {Button, message} from 'antd';
import {CalendarOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {configurationScheduleQueryOptions, configurationScheduleKeys} from '../../../../queries/configuration/configuration-schedule.ts';
import {
  type ScheduleRule,
  createConfigurationSchedule,
  updateConfigurationSchedule,
  removeConfigurationSchedule,
} from '../../../../api/configuration/configuration-schedule-api.ts';
import RunScheduleDialog from '../../../runs/run-scheduling/run-scheduling-dialog.jsx';

type ScheduleRuleFromDialog = ScheduleRule & {removed?: boolean};

type ScheduleActionProps = CommonProps & {
  configurationId?: number | string;
};

function ScheduleAction(props: ScheduleActionProps) {
  const {configurationId} = props;
  const numericId = configurationId !== undefined ? Number(configurationId) : undefined;

  const [open, setOpen] = useState(false);
  const [savePending, setSavePending] = useState(false);
  const queryClient = useQueryClient();

  const {data: rules = [], isLoading: loadPending} = useQuery(
    configurationScheduleQueryOptions(numericId),
  );

  const pending = loadPending || savePending;

  const saveRules = useCallback(
    async (updatedRules: ScheduleRuleFromDialog[]) => {
      if (numericId === undefined) return;
      const toRemove: ScheduleRule[] = [];
      const toUpdate: ScheduleRule[] = [];
      const toCreate: ScheduleRule[] = [];

      const ruleChanged = ({scheduleId, action, cronExpression, timeZone, removed}: ScheduleRuleFromDialog) => {
        if (!scheduleId || removed) return true;
        const existing = rules.find((r) => r.scheduleId === scheduleId);
        if (!existing) return true;
        return (
          existing.action !== action ||
          existing.cronExpression !== cronExpression ||
          existing.timeZone !== timeZone
        );
      };

      for (const rule of updatedRules) {
        const {scheduleId, action, cronExpression, timeZone, removed} = rule;
        if (!ruleChanged(rule)) continue;
        const payload = {scheduleId, action, cronExpression, timeZone};
        if (scheduleId) {
          if (removed) {
            toRemove.push(payload);
          } else {
            toUpdate.push(payload);
          }
        } else if (!removed) {
          toCreate.push(payload);
        }
      }

      const hide = message.loading('Saving schedule rules...', 0);
      try {
        await Promise.all([
          toRemove.length > 0 ? removeConfigurationSchedule(numericId, toRemove) : Promise.resolve(),
          toUpdate.length > 0 ? updateConfigurationSchedule(numericId, toUpdate) : Promise.resolve(),
          toCreate.length > 0 ? createConfigurationSchedule(numericId, toCreate) : Promise.resolve(),
        ]);
        await queryClient.invalidateQueries({queryKey: configurationScheduleKeys.detail(numericId)});
        setOpen(false);
      } catch (error) {
        message.error(String(error), 5);
      } finally {
        hide();
      }
    },
    [numericId, rules, queryClient],
  );

  const handleSubmit = useCallback(
    (updatedRules: ScheduleRuleFromDialog[]) => {
      setSavePending(true);
      saveRules(updatedRules).finally(() => setSavePending(false));
    },
    [saveRules],
  );

  const ruleCount = rules.length;

  return (
    <>
      <Button
        id="configuration-schedule-button"
        size="small"
        disabled={pending}
        onClick={() => setOpen(true)}
      >
        <CalendarOutlined />
        {ruleCount > 0 ? `Schedule: ${ruleCount} rule${ruleCount > 1 ? 's' : ''}` : 'Schedule'}
      </Button>
      <RunScheduleDialog
        availableActions={[RunScheduleDialog.Actions.run]}
        title="Run schedule"
        rules={rules}
        disabled={savePending}
        visible={open}
        onClose={() => setOpen(false)}
        onSubmit={handleSubmit}
      />
    </>
  );
}

export {ScheduleAction};
