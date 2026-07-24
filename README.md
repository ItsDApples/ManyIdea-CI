# ManyIdea

基于 CraftEngine 的 Paper 农夫乐事插件，为游戏添加丰富的烹饪系统、自定义作物、切割板与盛宴机制。

## 特性

- **烹饪系统** — 烹饪锅、油炸锅、烧烤架，真实多阶段烹饪
- **切割板** — 多种刀具与食材处理，自定义切割配方
- **自定义作物** — 可种植和收获的作物系统
- **盛宴方块** — 放置大型食物供多人取食
- **舒适度系统** — 食物品质影响玩家状态
- **200+ 自定义食物** — 每种都有独立模型与效果
- **可视化资源包** — 通过 CraftEngine 自动生成与分发

## 依赖

| 插件 | 类型 |
|------|------|
| [CraftEngine](https://modrinth.com/plugin/craftengine) 26.7.4+ | 必须 |
| [UltimateAdvancementAPI](https://modrinth.com/plugin/ultimateadvancementapi) 2.8.0+ | 必须 |

## 安装

1. 确保已安装 CraftEngine 和 UltimateAdvancementAPI
2. 将 `ManyIdea.jar` 放入 `plugins/` 目录
3. 重启服务器或加载插件
4. 插件会自动解压资源包配置到 CraftEngine 目录并触发加载
5. 玩家加入时自动接收资源包

## 开发

```bash
# 编译
./gradlew build

# 本地调试（自动下载依赖插件）
./gradlew runServer
```

## 致谢

- [农夫乐事 / Farmer's Delight](https://github.com/vectorwing/FarmersDelight)
- [果园乐事 / Fruits Delight](https://github.com/Minecraft-LightLand/FruitsDelight)
- [烧烤乐事 / Barbeques Delight](https://github.com/Minecraft-LightLand/BarbequesDelight)
- [随意乐事 / Casualness Delight](https://github.com/UsubaShihori/Casualness-Delight)

## 许可证

GNU General Public License v3.0 — 详见 [LICENSE](LICENSE)
