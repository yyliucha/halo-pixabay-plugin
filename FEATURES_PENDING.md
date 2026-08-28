# 待实现功能：关键词 ↔ 存储策略/附件分组一一对应

> 状态：**已记录，待商店首审通过后实现（目标版本 1.2.0）**
> 记录时间：2026-08-27（2026-08-28 更新：v1.1.0 已发布 GitHub Release，
> 商店开发者申请/首次审核进行中；开发者确认等商店首审通过后开工）

## 需求背景

当前插件所有搜索关键词共用一套「附件存储策略 + 附件分组」配置，下载的图片全部落入
同一个策略/分组。用户希望**每个搜索关键词可以独立指定存储策略和附件分组**，按图片
类型/主题分门别类地入库。

典型场景：

| 关键词 | 策略 | 分组 |
|---|---|---|
| mountain, landscape（风景类） | 本地存储 | 风景图片 |
| flower, plant（植物类） | 对象存储 OSS/S3 | 植物图片 |
| ... | ... | ... |

## 目标

- 设置页可配置多条「关键词 → 策略/分组」映射（一一对应，可增删排序）
- 运行时按关键词命中规则下载到对应策略/分组
- 未命中任何映射的关键词使用全局兜底策略/分组（保留现有字段及行为）
- 全局去重逻辑不变（按 Pixabay ID，跨策略/分组生效）

## 实现要点（预研结论，供开发时参考）

1. **表单**：Halo 控制台内置 FormKit 输入已有 `list` / `repeater` / `array` 类型
   （`ui/src/formkit/inputs/`，自 Halo 2.20 起可用），可直接在 `settings.yaml` 中
   用 `$formkit: list`（或 repeater/array）渲染「关键词-策略-分组」条目列表，
   **无需自定义 FormKit 组件**。条目内字段建议复用 `attachmentPolicySelect` /
   `attachmentGroupSelect`（下拉自动读真实数据）。
2. **配置模型**：`PixabaySetting` 增加可选字段
   `keywordConfigs: List<KeywordConfig>`（`keyword` / `attachmentPolicy` /
   `attachmentGroup`）；旧字段 `keywords` / `attachmentPolicy` / `attachmentGroup`
   保留，作为未配置映射时的兜底。
3. **下载循环**：`PixabayDownloadService.runDownload` 改为「先按映射分组、再逐组
   下载」：遍历已配置的映射，每组用组内策略/分组解析（沿用现有 `resolvePolicy` /
   `resolveGroup`，接受组内显式策略名）；未配置映射的关键词走全局默认。
4. **兼容性**：老配置（只有全局字段）行为不变；映射列表为空时等价于现状。新增字段
   为可选，升级无需迁移。
5. **结果汇总**：`lastRunMessage` 可扩展为按组展示（如
   `mountain → 本地: added 5; flower → OSS: added 3`），或保持聚合统计不变。

## 发布确认项

- [x] v1.1.0 首版发布（GitHub Release 已建）
- [ ] 商店首次审核通过（进行中）
- [ ] 实现上述要点并回归测试（含老配置兼容、并发保护、去重），版本 1.2.0
