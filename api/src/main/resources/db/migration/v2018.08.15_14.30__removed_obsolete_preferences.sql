-- Remove obsolete preferences 'data.sharing.proxy.endpoint.id' and 'data.sharing.proxy.metadata':
-- they are replaced with 'system.external.services.endpoints' preference
DELETE FROM pipeline.preference WHERE preference_name IN ('data.sharing.proxy.metadata', 'data.sharing.proxy.endpoint.id');