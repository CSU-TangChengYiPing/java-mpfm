-- v4 破坏性收口：删除历史兼容上传表（当前运行时已切换为任务系统 + 会话文件存储）
DROP TABLE IF EXISTS upload_v2_idempotency;
DROP TABLE IF EXISTS upload_v2_parts;
DROP TABLE IF EXISTS upload_v2_sessions;
DROP TABLE IF EXISTS upload_chunks;
DROP TABLE IF EXISTS upload_sessions;
