#!/usr/bin/env python3
"""#6 AI 客服 RAG V1 开发库对齐(2026-09-08):
   ai_kb_document/ai_kb_chunk 建表 + sys_menu 30(AI知识库) + sys_role_menu (1,30) + sys_config AI 组 2 键。
   全部幂等(CREATE IF NOT EXISTS / INSERT IGNORE),与 docs/sql/01_schema_init.sql 种子逐字同源;
   连接信息读 local.properties(键=环境变量名),不含硬编码密码。跑完打印行数自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

DDL = [
    """CREATE TABLE IF NOT EXISTS ai_kb_document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title        VARCHAR(128) NOT NULL COMMENT '文档标题(上传文件名去后缀/粘贴文本首行截断)',
    source_type  VARCHAR(16)  NOT NULL COMMENT '来源:UPLOAD文件上传/TEXT粘贴文本',
    file_name    VARCHAR(255) NULL COMMENT '原始文件名(仅source_type=UPLOAD)',
    char_count   INT NOT NULL DEFAULT 0 COMMENT '原文总字符数',
    chunk_count  INT NOT NULL DEFAULT 0 COMMENT '分块数(与ai_kb_chunk行数一致)',
    status       VARCHAR(16) NOT NULL DEFAULT 'FAILED' COMMENT '状态:READY可检索/FAILED向量化失败(修复后可重建转READY)',
    uploaded_by  BIGINT NOT NULL COMMENT '上传人(sys_user.id)',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_created (created_at)
) COMMENT='AI客服知识库文档(RAG语料正本元数据,分块文本在ai_kb_chunk)'""",
    """CREATE TABLE IF NOT EXISTS ai_kb_chunk (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键(String.valueOf(id)=向量库文档ID,删除按此对齐)',
    document_id  BIGINT NOT NULL COMMENT '所属文档ID(ai_kb_document.id)',
    chunk_index  INT NOT NULL COMMENT '块序号(0起,同文档内连续)',
    content      TEXT NOT NULL COMMENT '块文本(TokenTextSplitter切分,向量化重建正本)',
    char_count   INT NOT NULL DEFAULT 0 COMMENT '块字符数',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_doc_idx (document_id, chunk_index),
    KEY idx_doc (document_id)
) COMMENT='AI客服知识库分块(RAG检索语料正本,向量不入库——向量是索引派生物)'""",
]

CONFIG_ROWS = [
    ("AI", "erp.ai.kb.retrieval-top-k", "4",
     "知识库RAG:检索命中条数上限(注入 chat 上下文的片段数;0=关闭注入)"),
    ("AI", "erp.ai.kb.retrieval-min-score", "0.5",
     "知识库RAG:检索相似度下限(0~1,低于此分不注入)"),
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for sql in DDL:
            cur.execute(sql)
        cur.execute(
            "INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
            "VALUES (30, 25, 'AI知识库', 2, 'ai:kb:list', '/ai/kb', 'ai/kb/index', 'notebook', 4)")
        cur.execute("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 30)")
        for group, key, value, remark in CONFIG_ROWS:
            cur.execute(
                "INSERT IGNORE INTO sys_config (config_group, config_key, config_value, remark) VALUES (%s,%s,%s,%s)",
                (group, key, value, remark))
        conn.commit()
        # 自检
        cur.execute("SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema='erp' AND table_name IN ('ai_kb_document','ai_kb_chunk')")
        tables = cur.fetchone()[0]
        cur.execute("SELECT config_key, config_value FROM sys_config WHERE config_key LIKE 'erp.ai.kb.%'")
        cfg = cur.fetchall()
        cur.execute("SELECT COUNT(*) FROM sys_menu WHERE id=30")
        menu = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=30")
        grant = cur.fetchone()[0]
        print(f"tables={tables}/2 menu30={menu} grant={grant} config={cfg}")
        print("ALL GREEN" if tables == 2 and menu == 1 and grant == 1 and len(cfg) == 2 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
