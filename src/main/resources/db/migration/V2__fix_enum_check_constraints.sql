-- Resync enum CHECK constraints with the lowercase values produced by the
-- AttributeConverter classes (Role.RoleConverter, UserType.UserTypeConverter,
-- DocumentSource.DocumentSourceConverter, ...). Existing prod databases were
-- created when the entities still used @Enumerated(STRING), leaving CHECK
-- constraints with uppercase enum names that reject every insert today.
--
-- Each block:
--   1. drops the old constraint if present (so the migration is idempotent
--      and works on prod, fresh DBs, and any partial state in between);
--   2. lowercases existing rows in case any uppercase values slipped through;
--   3. re-adds the constraint with the lowercase value set the converter
--      produces.

-- users.role  (Role)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users SET role = lower(role) WHERE role <> lower(role);
ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('user', 'admin'));

-- users.user_type  (UserType)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
UPDATE users SET user_type = lower(user_type) WHERE user_type <> lower(user_type);
ALTER TABLE users
    ADD CONSTRAINT users_user_type_check CHECK (user_type IN ('student', 'staff'));

-- staff_profiles.department  (StaffDepartment)
ALTER TABLE staff_profiles DROP CONSTRAINT IF EXISTS staff_profiles_department_check;
UPDATE staff_profiles SET department = lower(department) WHERE department <> lower(department);
ALTER TABLE staff_profiles
    ADD CONSTRAINT staff_profiles_department_check
    CHECK (department IN (
        'student_support', 'academic_affairs', 'admissions',
        'industry_cooperation', 'international_office',
        'general_affairs', 'other'
    ));

-- student_profiles.department  (StudentDepartment)
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS student_profiles_department_check;
UPDATE student_profiles SET department = lower(department) WHERE department <> lower(department);
ALTER TABLE student_profiles
    ADD CONSTRAINT student_profiles_department_check
    CHECK (department IN ('software', 'ai'));

-- student_profiles.academic_status  (AcademicStatus)
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS student_profiles_academic_status_check;
UPDATE student_profiles SET academic_status = lower(academic_status) WHERE academic_status <> lower(academic_status);
ALTER TABLE student_profiles
    ADD CONSTRAINT student_profiles_academic_status_check
    CHECK (academic_status IN ('enrolled', 'on_leave', 'returning'));

-- documents.source  (DocumentSource)
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_source_check;
UPDATE documents SET source = lower(source) WHERE source <> lower(source);
ALTER TABLE documents
    ADD CONSTRAINT documents_source_check CHECK (source IN ('sw', 'kmu'));

-- documents.status  (DocumentStatus)
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_status_check;
UPDATE documents SET status = lower(status) WHERE status <> lower(status);
ALTER TABLE documents
    ADD CONSTRAINT documents_status_check
    CHECK (status IN ('uploaded', 'processing', 'completed', 'failed', 'reprocessing'));

-- chat_sessions.source_type  (SourceType)
ALTER TABLE chat_sessions DROP CONSTRAINT IF EXISTS chat_sessions_source_type_check;
UPDATE chat_sessions SET source_type = lower(source_type) WHERE source_type <> lower(source_type);
ALTER TABLE chat_sessions
    ADD CONSTRAINT chat_sessions_source_type_check
    CHECK (source_type IN ('normal', 'faq'));

-- chat_messages.role  (MessageRole)
ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chat_messages_role_check;
UPDATE chat_messages SET role = lower(role) WHERE role <> lower(role);
ALTER TABLE chat_messages
    ADD CONSTRAINT chat_messages_role_check CHECK (role IN ('user', 'assistant'));

-- chat_messages.confidence_level  (ConfidenceLevel)
ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chat_messages_confidence_level_check;
UPDATE chat_messages SET confidence_level = lower(confidence_level)
    WHERE confidence_level IS NOT NULL AND confidence_level <> lower(confidence_level);
ALTER TABLE chat_messages
    ADD CONSTRAINT chat_messages_confidence_level_check
    CHECK (confidence_level IS NULL OR confidence_level IN ('low', 'medium', 'high'));
