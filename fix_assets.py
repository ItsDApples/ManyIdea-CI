#!/usr/bin/env python3
"""
修复脚本：从原始素材源复制正确纹理到 CE 目录。
用法：
  python fix_assets.py --dry-run    # 预览
  python fix_assets.py              # 执行修复
"""

import hashlib, shutil, sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent

CE_BLOCK_TEX = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/textures/block"
CE_ITEM_TEX  = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/textures/item"

SOURCES = {
    "FD": {
        "tex/block": PROJECT / "temp/main/resources/assets/farmersdelight/textures/block",
        "tex/item":  PROJECT / "temp/main/resources/assets/farmersdelight/textures/item",
    },
    "Casualness": {
        "tex/block": PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/textures/block",
        "tex/item":  PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/textures/item",
    },
    "Fruits": {
        "tex/block": PROJECT / "temp/FruitsDelight/assets/fruitsdelight/textures/block",
        "tex/item":  PROJECT / "temp/FruitsDelight/assets/fruitsdelight/textures/item",
    },
    "Barbeques": {
        "tex/block": PROJECT / "temp/BarbequesDelight-1.21/resources/assets/barbequesdelight/textures/block",
        "tex/item":  PROJECT / "temp/BarbequesDelight-1.21/resources/assets/barbequesdelight/textures/item",
    },
}

DRY_RUN = "--dry-run" in sys.argv


def md5(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def find_tex(filename: str, category: str) -> tuple[str, Path] | None:
    """精确类别 + 跨类别搜索"""
    for src_name, dirs in SOURCES.items():
        key = f"tex/{category}"
        if key in dirs and (c := dirs[key] / filename).exists():
            return (src_name, c)
    for src_name, dirs in SOURCES.items():
        for key, d in dirs.items():
            if key.startswith("tex/") and (c := d / filename).exists():
                return (src_name, c)
    return None


def copy_if_diff(src: Path, dst: Path) -> bool:
    """MD5 不同则从 src 复制到 dst，返回是否执行了复制"""
    if not src.exists():
        return False
    if dst.exists() and md5(src) == md5(dst):
        return False
    if DRY_RUN:
        print(f"     [DRY-RUN] {src.name} -> {dst}")
    else:
        shutil.copy2(src, dst)
        print(f"     FIXED: {dst.name}  <- {src}")
    return True


# ==============================
# 1. 修复 MD5 不一致：用源文件覆盖 CE
# ==============================
print("1. 用源纹理覆盖 CE（MD5 不同时）")
print("-" * 40)

fixed = 0
for cat, ce_dir in [("block", CE_BLOCK_TEX), ("item", CE_ITEM_TEX)]:
    for ce_file in sorted(ce_dir.iterdir()) if ce_dir.exists() else []:
        if ce_file.suffix != ".png":
            continue
        src = find_tex(ce_file.name, cat)
        if src is None:
            continue
        src_name, src_file = src
        if copy_if_diff(src_file, ce_file):
            fixed += 1

print(f"  修复 {fixed} 个纹理")
print()

# ==============================
# 2. 修复误复制：item 纹理与 block 完全一致 + 源仅有 block 版本
# ==============================
print("2. 修复误复制（item == block 且源仅有 block 版本）")
print("-" * 40)

fixed = 0
if CE_BLOCK_TEX.exists() and CE_ITEM_TEX.exists():
    for item_file in sorted(CE_ITEM_TEX.iterdir()):
        if item_file.suffix != ".png":
            continue
        block_file = CE_BLOCK_TEX / item_file.name
        if not block_file.exists():
            continue
        if md5(item_file) != md5(block_file):
            continue

        # CE 里 item 和 block 完全一致，检查源
        # 如果源有 item 版本 → 用源的 item 版本覆盖
        src_item = find_tex(item_file.name, "item") if False else None  # 跳过，源无 item 版本
        # 源无 item 版本但 CE item 与 CE block 相同 → 可能正确（block item）
        # 暂时仅报告，不自动修复
        src_block = find_tex(item_file.name, "block")
        if src_block is None:
            continue

        # 检查能否从源 item 目录复制
        for src_name, dirs in SOURCES.items():
            key = "tex/item"
            if key in dirs:
                src = dirs[key] / item_file.name
                if src.exists() and md5(src) != md5(item_file):
                    if copy_if_diff(src, item_file):
                        fixed += 1
                    break

if fixed == 0:
    print("  (无需修复 — 源中无独立 item 版本，block item 共用贴图是正确行为)")
else:
    print(f"  修复 {fixed} 个纹理")
print()

# ==============================
# 3. 补缺失纹理：从源复制 CE 中没有的
# ==============================
print("3. 补充缺失纹理（CE 无此文件，但源有）")
print("-" * 40)

fixed = 0
for src_name, dirs in SOURCES.items():
    for key, src_dir in dirs.items():
        if not key.startswith("tex/") or not src_dir.exists():
            continue
        cat = key.split("/")[1]  # block or item
        ce_dir = CE_BLOCK_TEX if cat == "block" else CE_ITEM_TEX
        for src_file in sorted(src_dir.iterdir()):
            if src_file.suffix != ".png":
                continue
            ce_file = ce_dir / src_file.name
            if ce_file.exists():
                continue
            # CE 没有 → 从源复制
            if DRY_RUN:
                print(f"     [DRY-RUN] add {src_name}: {src_file.name}")
            else:
                shutil.copy2(src_file, ce_file)
                print(f"     ADDED: {ce_file.name}  <- {src_name}")
            fixed += 1

print(f"  补充 {fixed} 个纹理")
print()
print("完成" if not DRY_RUN else "[DRY-RUN 完成，未实际修改]")
