#!/usr/bin/env python3
"""
CE ↔ FD/CD/Fruits/Barbeques 纹理/模型完整性校验脚本。
用法：python check_assets.py
"""

import hashlib, json
from pathlib import Path

PROJECT = Path(__file__).resolve().parent

# CE 资源目录
CE_BLOCK_TEX = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/textures/block"
CE_ITEM_TEX  = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/textures/item"
CE_BLOCK_MODELS = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/models/block"
CE_ITEM_MODELS  = PROJECT / "src/main/resources/addon/manyidea/resourcepack/assets/manyidea/models/item"

# 原始素材源
SOURCES = {
    "FD": {
        "tex/block": PROJECT / "temp/main/resources/assets/farmersdelight/textures/block",
        "tex/item":  PROJECT / "temp/main/resources/assets/farmersdelight/textures/item",
        "mdl/block": PROJECT / "temp/main/resources/assets/farmersdelight/models/block",
        "mdl/item":  PROJECT / "temp/main/resources/assets/farmersdelight/models/item",
        "ns": "farmersdelight",
    },
    "Casualness": {
        "tex/block": PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/textures/block",
        "tex/item":  PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/textures/item",
        "mdl/block": PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/models/block",
        "mdl/item":  PROJECT / "temp/Casualness-Delight-1.20.1-Forge/resources/assets/casualness_delight/models/item",
        "ns": "casualness_delight",
    },
    "Fruits": {
        "tex/block": PROJECT / "temp/FruitsDelight/assets/fruitsdelight/textures/block",
        "tex/item":  PROJECT / "temp/FruitsDelight/assets/fruitsdelight/textures/item",
        "mdl/block": PROJECT / "temp/FruitsDelight/assets/fruitsdelight/models/block",
        "mdl/item":  PROJECT / "temp/FruitsDelight/assets/fruitsdelight/models/item",
        "ns": "fruitsdelight",
    },
    "Barbeques": {
        "tex/item":  PROJECT / "temp/BarbequesDelight-1.21/resources/assets/barbequesdelight/textures/item",
        "tex/block": PROJECT / "temp/BarbequesDelight-1.21/resources/assets/barbequesdelight/textures/block",
        "mdl/custom": PROJECT / "temp/BarbequesDelight-1.21/resources/assets/barbequesdelight/models/custom",
        "ns": "barbequesdelight",
    },
}


def md5(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def find_tex(filename: str, category: str, strict: bool = False) -> tuple[str, Path, str] | None:
    """搜索纹理，strict=True 只匹配同类别。返回 (源名称, 路径, 匹配类别) 或 None"""
    for src_name, dirs in SOURCES.items():
        key = f"tex/{category}"
        if key in dirs:
            candidate = dirs[key] / filename
            if candidate.exists():
                return (src_name, candidate, category)
    if strict:
        return None
    for src_name, dirs in SOURCES.items():
        for key, d in dirs.items():
            if key.startswith("tex/"):
                candidate = d / filename
                if candidate.exists():
                    return (src_name, candidate, key.split("/")[1])
    return None


def find_model(filename: str) -> tuple[str, Path, str] | None:
    """返回 (源名称, 路径, 命名空间) 或 None"""
    for src_name, dirs in SOURCES.items():
        for mdl_key in ("mdl/block", "mdl/item", "mdl/custom"):
            if mdl_key in dirs:
                candidate = dirs[mdl_key] / filename
                if candidate.exists():
                    return (src_name, candidate, dirs["ns"])
    return None


def iter_files(path: Path, suffix: str) -> list[Path]:
    if path.exists():
        return sorted(f for f in path.iterdir() if f.suffix == suffix)
    return []


# ==============================
# 纹理比对
# ==============================
print("=" * 60)
print("  纹理 MD5 比对")
print("=" * 60)

categories = {"block": CE_BLOCK_TEX, "item": CE_ITEM_TEX}
total = 0
mismatches = []
missing_src = []

for cat, ce_dir in categories.items():
    for ce_file in iter_files(ce_dir, ".png"):
        total += 1
        src = find_tex(ce_file.name, cat)
        if src is None:
            missing_src.append((cat, ce_file.name))
            continue
        src_name, src_file, src_cat = src
        if md5(ce_file) != md5(src_file):
            tag = f" (源 {src_cat})" if src_cat != cat else ""
            mismatches.append((cat, ce_file.name, src_name, ce_file, src_file, tag))

print(f"  扫描: {total} 个纹理")
print(f"  匹配: {total - len(mismatches) - len(missing_src)} 个")
if missing_src:
    print(f"\n  ❌ 无源 ({len(missing_src)} 个):")
    for cat, name in sorted(missing_src):
        print(f"     {cat}/{name}")
if mismatches:
    print(f"\n  ⚠ MD5 不一致 ({len(mismatches)} 个):")
    for cat, name, src, p1, p2, tag in sorted(mismatches):
        print(f"     {cat}/{name}  ← {src} (CE={md5(p1)[:8]} 源={md5(p2)[:8]}){tag}")
else:
    print("  ✅ 所有纹理 MD5 与源一致")

# ==============================
# block↔item 误复制检测
# ==============================
print()
print("=" * 60)
print("  block ↔ item 误复制检测")
print("=" * 60)

item_names = {f.name for f in iter_files(CE_ITEM_TEX, ".png")}
block_names = {f.name for f in iter_files(CE_BLOCK_TEX, ".png")}
wrong_copies = []

for name in sorted(item_names & block_names):
    if md5(CE_ITEM_TEX / name) == md5(CE_BLOCK_TEX / name):
        src_item = find_tex(name, "item", strict=True)
        src_block = find_tex(name, "block", strict=True)
        if src_block is not None and src_item is None:
            wrong_copies.append((name, src_block[0]))

if wrong_copies:
    print(f"  ⚠ {len(wrong_copies)} 个 item 纹理可能是从 block 误复制的:")
    for name, src in wrong_copies:
        print(f"     item/{name} == block/{name}  源={src} (仅有 block 版本)")
else:
    print("  ✅ 未发现误复制")

# ==============================
# 模型比对
# ==============================
print()
print("=" * 60)
print("  模型 JSON 比对")
print("=" * 60)

for category, ce_dir in [("block", CE_BLOCK_MODELS), ("item", CE_ITEM_MODELS)]:
    if not ce_dir.exists():
        continue
    found = 0
    for ce_file in iter_files(ce_dir, ".json"):
        found += 1
        src = find_model(ce_file.name)
        if src is None:
            alt = ce_file.name.replace("_block.json", ".json")
            src = find_model(alt)
        if src is None:
            print(f"  ❌ 无源 ({category}): {ce_file.name}")
            continue

        src_name, src_file, src_ns = src
        try:
            ce_obj = json.loads(ce_file.read_text(encoding="utf-8"))
            fd_obj = json.loads(src_file.read_text(encoding="utf-8"))
        except:
            print(f"  ❌ JSON 解析失败: {ce_file.name}")
            continue

        ce_json = json.dumps(ce_obj)
        fd_json = json.dumps(fd_obj)
        ce_json = ce_json.replace("minecraft:block/custom/", f"{src_ns}:block/")
        ce_json = ce_json.replace("minecraft:item/custom/",  f"{src_ns}:item/")
        ce_json = ce_json.replace("minecraft:item/food/",    f"{src_ns}:item/")
        ce_json = ce_json.replace("minecraft:item/material/",f"{src_ns}:item/")
        ce_json = ce_json.replace("manyidea:block/",         f"{src_ns}:block/")
        ce_json = ce_json.replace("manyidea:item/",          f"{src_ns}:item/")

        if ce_json == fd_json:
            print(f"  ✅ ({category}) {ce_file.name}  ← {src_name}")
        else:
            print(f"  ⚠ ({category}) {ce_file.name}  ← {src_name} (结构不一致)")

    if found > 0:
        print(f"\n  目录 {category}: {found} 个模型")

print()
print("=" * 60)
print("  完成")
print("=" * 60)
