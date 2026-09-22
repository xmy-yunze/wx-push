-- ============================================================
--  迁移 002：给 wx_message_log 加去重键 —— 修复事件消息幂等失效
--  制定时间：2026-09-22
-- ============================================================
--
--  背景（已实测复现）：
--    原幂等方案是「msg_id 唯一索引 + INSERT IGNORE」。但事件消息
--    （subscribe / unsubscribe / CLICK …）的报文里没有 MsgId，落库为 NULL；
--    而 MySQL 的唯一索引**不比较 NULL** —— 多行 NULL 被视为互不相同，
--    于是唯一约束对事件消息完全失效。
--    实测：同一条 CLICK 事件连发两次（模拟微信重推），写入了两行。
--
--  本脚本做四件事：
--    1) 加 dedup_key 列
--    2) 回填历史行
--    3) 在 dedup_key 上建唯一索引（幂等从这里开始真正生效）
--    4) 把旧的 uk_msg_id 降级为普通索引
--
--  ⚠️ 只能执行一次。MySQL 的 ALTER TABLE ADD COLUMN / ADD INDEX 不支持 IF NOT EXISTS，
--     重复执行会报 Duplicate column name / Duplicate key name —— 那是预期行为，不是脚本坏了。
--
--  执行（把 <密码> 换成你的；注意本项目 MySQL 经典协议端口是 3326，不是 3306）：
--    mysql -h127.0.0.1 -P3326 -uroot -p < backend/src/main/resources/db/alter-002-add-dedup-key.sql
--
--  验证（执行完应看到 dedup_key 一行，且 uk_dedup_key 唯一、idx_msg_id 非唯一）：
--    SHOW INDEX FROM wx_message_log;
--
--  回滚（仅在需要时手工执行，会重新暴露出事件消息重复入库的问题）：
--    ALTER TABLE wx_message_log DROP INDEX uk_dedup_key, DROP COLUMN dedup_key;
--    ALTER TABLE wx_message_log DROP INDEX idx_msg_id, ADD UNIQUE KEY uk_msg_id (msg_id);
-- ============================================================

USE wx_push;

-- 1) 加列。先允许 NULL 是为了给历史行留回填空间：
--    唯一索引允许多个 NULL，所以这一列全为 NULL 时也不会互相冲突。
ALTER TABLE wx_message_log
    ADD COLUMN dedup_key VARCHAR(255) DEFAULT NULL
        COMMENT '去重键：msg:<MsgId> 或 evt:<FromUser>:<Event>:<EventKey>:<CreateTime>'
        AFTER id;

-- 2) 回填历史行。
--    普通消息能还原出真实去重键（msg_id 本身唯一）；
--    事件消息的历史行当初没有存 EventKey，无法还原，用一个由主键派生的值占位 ——
--    它只为满足唯一索引，并不代表真实的去重键（也就不会去挡任何新消息）。
UPDATE wx_message_log
SET dedup_key = CASE
                    WHEN msg_id IS NOT NULL THEN CONCAT('msg:', msg_id)
                    ELSE CONCAT('legacy:', id)
                END
WHERE dedup_key IS NULL;

-- 3) 建唯一索引 —— 幂等从这一行开始真正生效
ALTER TABLE wx_message_log
    ADD UNIQUE KEY uk_dedup_key (dedup_key);

-- 4) 旧唯一索引降级为普通索引：保留按 msg_id 查询的能力，去掉多余的唯一约束。
--    ⚠️ 若你的库中 uk_msg_id 已经不存在（比如手工改过），这一步会报 Can't DROP，
--       跳过它即可，不影响前三步的效果。
ALTER TABLE wx_message_log
    DROP INDEX uk_msg_id,
    ADD KEY idx_msg_id (msg_id);

-- 5) 自检
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'wx_push'
  AND TABLE_NAME = 'wx_message_log'
ORDER BY ORDINAL_POSITION;

SHOW INDEX FROM wx_message_log;
