UPDATE clients
SET deleted = TRUE
WHERE deleted_date IS NOT NULL;