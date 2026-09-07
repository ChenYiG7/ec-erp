# -*- coding: utf-8 -*-
"""扫描 erp-web/src 下所有 .vue:模板中使用 <el-xxx> 但 script 未 import 对应 El 组件的文件。

项目为手动按需导入(main.ts 无 app.use(ElementPlus),无 unplugin 自动导入),
模板组件必须显式 import。el-* 标签 -> PascalCase ElXxx,对照 'element-plus' 导入集合。
"""
import re
import sys
import io
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ROOT = Path(r'D:\Work\project\own\ec-erp\erp-web\src')
tag_re = re.compile(r'<(el-[a-z-]+)(?=[\s/>])')
imp_re = re.compile(r"import\s*\{([^}]+)\}\s*from\s*'element-plus'")

def to_pascal(tag: str) -> str:
    return ''.join(p.capitalize() for p in tag.split('-'))  # el-radio-button -> ElRadioButton

problems = []
for f in ROOT.rglob('*.vue'):
    text = f.read_text(encoding='utf-8', errors='ignore')
    # 只扫 <template> 段,避免 :deep(.el-xxx) 样式误报
    m = re.search(r'<template>(.*)</template>', text, re.S)
    if not m:
        continue
    used = set(tag_re.findall(m.group(1)))
    if not used:
        continue
    imported = set()
    for im in imp_re.finditer(text):
        imported |= {x.strip() for x in im.group(1).split(',') if x.strip()}
    # defineOptions/组件名误报排除:仅比较标签
    missing = {t for t in used if to_pascal(t) not in imported}
    if missing:
        problems.append((str(f.relative_to(ROOT)), sorted(missing)))

if problems:
    print(f'{len(problems)} file(s) missing element-plus imports:')
    for path, tags in problems:
        print(f'  {path}: {", ".join(tags)}')
else:
    print('all clean: every <el-*> tag has a matching import')
