#!/usr/bin/env python3
"""#14 邮件通知渠道开发库对齐(2026-09-08):
   sys_config NOTIFY 组 7 键种子(SMTP 参数全量进 sys_config,授权码 SECRET 回显脱敏拍板见 TODO#14)。
   INSERT IGNORE 幂等(uk_config_key 冲突即跳过,已人工改过值的键不会被覆盖),
   与 docs/sql/01_schema_init.sql 种子逐字同源;连接信息读 local.properties。跑完打印行数自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

CONFIG_ROWS = [
    ("NOTIFY", "erp.mail.enabled", "false",
     "邮件通知总开关(#14 邮箱推送渠道;false 时邮件外推直接跳过,外呼保护性默认关)"),
    ("NOTIFY", "erp.mail.host", "",
     "邮件:SMTP 主机(如 smtp.exmail.qq.com;留空=渠道未就绪不外推)"),
    ("NOTIFY", "erp.mail.port", "465",
     "邮件:SMTP 端口(留空按 SSL 开关取默认:SSL=465/非加密=25)"),
    ("NOTIFY", "erp.mail.username", "",
     "邮件:SMTP 账号(通常即发件邮箱)"),
    ("NOTIFY", "erp.mail.password", "",
     "邮件:SMTP 授权码(SECRET 类型:读侧回显固定 ******,真值不出后端;docs/07 §7 范围例外)"),
    ("NOTIFY", "erp.mail.from", "",
     "邮件:发件人 From 头(留空回落 SMTP 账号)"),
    ("NOTIFY", "erp.mail.ssl", "true",
     "邮件:SSL 加密(465 端口典型 true;587 STARTTLS 场景 false)"),
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for group, key, value, remark in CONFIG_ROWS:
            cur.execute(
                "INSERT IGNORE INTO sys_config (config_group, config_key, config_value, remark) VALUES (%s,%s,%s,%s)",
                (group, key, value, remark))
        conn.commit()

        # 自检:NOTIFY 组应恰有 7 键
        cur.execute("SELECT COUNT(*) FROM sys_config WHERE config_group = 'NOTIFY'")
        count = cur.fetchone()[0]
        cur.execute("SELECT config_key, config_value FROM sys_config WHERE config_group = 'NOTIFY' ORDER BY config_key")
        rows = cur.fetchall()
        print(f"NOTIFY 组键数 = {count}(期望 7)")
        for key, value in rows:
            shown = value if key != "erp.mail.password" else ("(已配置)" if value else "(空)")
            print(f"  {key} = {shown}")
        if count != 7:
            print("FAIL:键数不符,检查上方 INSERT 输出")
            sys.exit(1)
        print("ALL GREEN")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
