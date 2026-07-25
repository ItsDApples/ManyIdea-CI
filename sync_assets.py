#!/usr/bin/env python3
"""
从 CE 运行目录同步资源到 src 源目录。
用法：python sync_assets.py
     python sync_assets.py --dry-run  (仅预览，不实际修改)
"""

import hashlib, shutil, sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent
SRC_DIR = PROJECT / "run/plugins/CraftEngine/resources/manyidea"
DST_DIR = PROJECT / "src/main/resources/addon/manyidea"

DRY_RUN = "--dry-run" in sys.argv


def md5(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def collect_files(root: Path) -> dict[str, Path]:
    """递归收集 root 下所有文件，返回 {相对路径: 绝对路径}"""
    result = {}
    for f in root.rglob("*"):
        if f.is_file():
            result[str(f.relative_to(root))] = f
    return result


# ==============================
# 1. 收集两边文件
# ==============================
src_files = collect_files(SRC_DIR) if SRC_DIR.exists() else {}
dst_files = collect_files(DST_DIR) if DST_DIR.exists() else {}

src_set = set(src_files)
dst_set = set(dst_files)

# ==============================
# 2. 比对 & 操作
# ==============================
copied = 0
skipped = 0
deleted = 0
updated = 0

print("=" * 60)
print("  同步 CE → src")
if DRY_RUN:
    print("  *** DRY-RUN 模式，不会实际修改文件 ***")
print("=" * 60)
print(f"  源: {SRC_DIR}")
print(f"  目标: {DST_DIR}")
print()

# --- 新增/更新 ---
for rel in sorted(src_set):
    src_path = src_files[rel]
    dst_path = DST_DIR / rel

    if rel in dst_set:
        # 文件已存在，比对内容
        if md5(src_path) == md5(dst_path):
            skipped += 1
        else:
            if not DRY_RUN:
                dst_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src_path, dst_path)
            updated += 1
            print(f"  ✎ 更新  {rel}")
    else:
        if not DRY_RUN:
            dst_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src_path, dst_path)
        copied += 1
        print(f"  + 新增  {rel}")

# --- 删除 ---
for rel in sorted(dst_set - src_set):
    if not DRY_RUN:
        (DST_DIR / rel).unlink()
    deleted += 1
    print(f"  - 删除  {rel}")

# --- 清理空目录 ---
if not DRY_RUN:
    for d in sorted(DST_DIR.rglob("*"), reverse=True):
        if d.is_dir() and not any(d.iterdir()):
            d.rmdir()

print()
print(f"  新增: {copied}  更新: {updated}  跳过: {skipped}  删除: {deleted}")
print("=" * 60)

if DRY_RUN and (copied + updated + deleted) > 0:
    print("  提示: 去掉 --dry-run 以实际执行同步")
print("  完成")
print("=" * 60)
