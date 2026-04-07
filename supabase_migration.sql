-- Миграция: добавить колонку encrypted_privkey в таблицу users
-- Выполни это в Supabase → SQL Editor

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS encrypted_privkey TEXT DEFAULT NULL;

-- Проверка
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'encrypted_privkey';
