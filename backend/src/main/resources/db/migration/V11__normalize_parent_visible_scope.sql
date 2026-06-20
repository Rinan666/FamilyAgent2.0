UPDATE diary_entries
SET visibility = 'CARE_VISIBLE'
WHERE visibility = 'PARENT_VISIBLE';

UPDATE growth_guard_records
SET visibility = 'CARE_VISIBLE'
WHERE visibility = 'PARENT_VISIBLE';

UPDATE growth_guard_reports
SET visibility = 'CARE_VISIBLE'
WHERE visibility = 'PARENT_VISIBLE';

UPDATE memory_entries
SET scope = 'CARE_VISIBLE'
WHERE scope = 'PARENT_VISIBLE';

UPDATE chat_sessions
SET visibility = 'CARE_VISIBLE'
WHERE visibility = 'PARENT_VISIBLE';

UPDATE mirror_agent_data
SET visibility = 'CARE_VISIBLE'
WHERE visibility = 'PARENT_VISIBLE';
