-- ============================================================
--  wx-push · 表结构（唯一真相源）
--
--  执行：mysql -h 127.0.0.1 -P 3326 -u root -p --default-character-set=utf8mb4 < schema.sql
--
--  ⚠️ 必须带 --default-character-set=utf8mb4，否则中文 COMMENT 会存成乱码。
--
--  本脚本设计为**幂等**：库和表都用 IF NOT EXISTS / ALTER，
--  对空库和已有的库各执行一遍都不会报错、不会丢数据。
-- ============================================================


-- ============================================================
--  一、数据库
-- ============================================================

CREATE DATABASE IF NOT EXISTS wx_push
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 上面的 IF NOT EXISTS 只在库不存在时才生效，对已存在的库不会改 collation。
-- 所以补一条幂等的 ALTER 兜底：库已存在时就靠它把 collation 纠正过来。
--
-- 为什么统一到 0900_ai_ci 而不是 general_ci：
--   general_ci 是 utf8mb4 的**历史遗留**默认值，自 MySQL 8.0 起 utf8mb4 的
--   固有默认已改为 0900_ai_ci。建 wx_message_log 时没显式写 COLLATE，
--   于是它以字符集固有默认落了地 —— 结果就是「库是 general_ci，表是 0900_ai_ci」。
--   两表 collation 不一致时，JOIN 的关联列会报 Illegal mix of collations。
--   统一到 0900_ai_ci 是向前对齐，且它对 Unicode 的排序/比较规则也更正确。
--
-- 这是纯元数据变更，不重写任何数据行，执行是秒级的。
ALTER DATABASE wx_push CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE wx_push;


-- ============================================================
--  二、微信消息流水表（P0 已有，此处仅补齐显式 COLLATE）
-- ============================================================

-- ⚠️ 若该表在本机已存在，CREATE TABLE IF NOT EXISTS 不会改动它。
--    你的本机表实际 collation 已经是 utf8mb4_0900_ai_ci（字符集固有默认），
--    与本次统一的目标一致，因此无需 ALTER，保持原样即可。
CREATE TABLE IF NOT EXISTS wx_message_log
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    msg_id     VARCHAR(64)     DEFAULT NULL COMMENT '微信消息 ID；事件消息没有该字段，为 NULL',
    from_user  VARCHAR(64)     NOT NULL COMMENT '发送者 openid',
    to_user    VARCHAR(64)     NOT NULL COMMENT '公众号原始 ID',
    msg_type   VARCHAR(32)     NOT NULL COMMENT '消息类型：text / image / event ...',
    `event`    VARCHAR(32)     DEFAULT NULL COMMENT '事件类型：subscribe / unsubscribe / CLICK ...',
    content    TEXT COMMENT '消息内容',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    -- 幂等的关键：唯一索引 + INSERT IGNORE
    -- MySQL 唯一索引允许多个 NULL，所以事件消息（msg_id 为空）不会被互相误挡
    UNIQUE KEY uk_msg_id (msg_id),
    KEY idx_from_user (from_user),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT ='微信消息流水';


-- ============================================================
--  三、管理后台账号表（鉴权前置，新增）
-- ============================================================

-- 设计要点：
--   COLLATE 显式写死 —— 不重复 wx_message_log 那个「靠默认识别」的坑。
--     将来若与其它表按 username 关联，不会出现 collation 冲突。
--   password_hash 留 200 字符 —— 存的是自描述文本格式
--     pbkdf2$迭代次数$盐的Base64$哈希的Base64（当前约 84 字符），
--     留足余量是为了将来调高迭代次数或换算法时不用 ALTER TABLE。
--   绝不存明文，也不存 MD5/SHA1 —— 后两者扛不住撞库。
--   status 用「禁用」而不是物理删号 —— 账号出问题时应能停用，
--     保留审计线索（谁在什么时候登录过）。
--   uk_username 唯一索引 —— 把「账号不重复」交给数据库保证，
--     而不是应用层先查后插（先查后插在并发下有竞态：两个请求可能都查到不存在）。
CREATE TABLE IF NOT EXISTS admin_user
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)     NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(200)    NOT NULL COMMENT '密码哈希，格式 pbkdf2$迭代次数$盐$哈希',
    display_name  VARCHAR(64)     DEFAULT NULL COMMENT '显示名',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1=启用，0=禁用',
    last_login_at DATETIME        DEFAULT NULL COMMENT '最后一次登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT ='管理后台账号';


-- ============================================================
--  四、初始化账号（⚠️ 不要直接执行，先按下面两步走）
-- ============================================================

-- 第 1 步：在 backend/ 目录下，用你自己的密码生成哈希。
--         密码只经过你的终端，不要发给任何人。
--
--   cd backend
--   java scripts/GeneratePasswordHash.java 你想设的密码
--
--         输出形如：pbkdf2$310000$xxxxxxxx$yyyyyyyy
--
-- 第 2 步：把上面输出的整串替换掉下面的 $HASH 占位符，再执行这一条 INSERT。
--         注意整串要包在单引号里，且中间不能有换行。
--
-- INSERT INTO admin_user (username, password_hash, display_name)
-- VALUES ('admin', '$HASH', '云泽');


-- ============================================================
--  五、事后核对
-- ============================================================

-- 确认库与两张表的 collation 已全部对齐（三行都应为 utf8mb4_0900_ai_ci）：
--
-- SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
--   FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'wx_push';
--
-- SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = 'wx_push';
--
-- 确认新表字段：
--
-- SHOW CREATE TABLE admin_user\G
