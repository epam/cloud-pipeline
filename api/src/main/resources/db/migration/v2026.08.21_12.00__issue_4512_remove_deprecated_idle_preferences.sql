DELETE FROM pipeline.preference
WHERE preference_name IN (
    'system.idle.cpu.threshold',
    'system.max.idle.timeout.minutes',
    'system.idle.action.timeout.minutes',
    'system.idle.action'
);
