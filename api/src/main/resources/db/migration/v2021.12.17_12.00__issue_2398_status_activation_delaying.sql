ALTER TABLE pipeline.last_triggered_storage_quota ADD target_status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE pipeline.last_triggered_storage_quota ADD notification_required BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE pipeline.last_triggered_storage_quota ADD status_activation_date TIMESTAMP WITH TIME ZONE NOT NULL
    DEFAULT (now() at time zone 'utc');

DO $$
DECLARE
    target_storage_id BIGINT;
    mount_status_value TEXT;
    trigger_update_date_value TIMESTAMP WITH TIME ZONE;
BEGIN
    FOR target_storage_id, mount_status_value, trigger_update_date_value IN
    SELECT storage_id, mount_status, update_date
        FROM pipeline.last_triggered_storage_quota
        INNER JOIN pipeline.datastorage
        ON datastorage.datastorage_id = last_triggered_storage_quota.storage_id
    LOOP
		UPDATE
			pipeline.last_triggered_storage_quota
		SET
			status_activation_date = trigger_update_date_value,
			target_status = mount_status_value
		WHERE storage_id = target_storage_id;
    END LOOP;
END $$;
