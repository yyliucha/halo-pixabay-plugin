# Pixabay 图库

定时从 [Pixabay](https://pixabay.com/) 官方 API 下载图片到 Halo 附件库，为内容创作提供稳定的素材来源。

> 本插件需要配置 Pixabay 免费 API Key（[申请地址](https://pixabay.com/api/docs/)），下载的图片遵循 [Pixabay Content License](https://pixabay.com/service/license-summary/)。

## 功能特性

- ✅ **Pixabay 官方 API**：稳定合规，不爬取网页
- ✅ **全局去重**：已下载图片按 Pixabay ID 记录，跨运行永不重复上传
- ✅ **定时自动下载**：cron 表达式（兼容 Quartz 6 段写法），首次启用约 1 分钟内自动执行一次以便验证
- ✅ **手动触发**：控制台「工具 → Pixabay 图库」一键下载
- ✅ **四档尺寸**：原图 / 大图 (~1280px) / 中等 (640px) / 缩略图 (150px)，上传失败自动按层级降级重试
- ✅ **附件无缝集成**：存储策略/附件分组下拉选择（实时读取你的 Halo 配置），附件库可见、可直接插入文章
- ✅ **关键词映射归档**：每个关键词可独立指定存储策略与附件分组，按类别自动归档（未配置关键词走全局兜底）
- ✅ **发布文章自动配图**：发布无封面文章时，自动按文章标签/标题关键词从 Pixabay 选图下载并设为文章封面
- ✅ **后台异步执行**：不阻塞请求，下载完成后页面自动刷新并展示新增/失败统计与失败原因

## 安装使用

1. Halo 后台 → 插件 → 安装，上传插件 JAR（Halo >= 2.20.0）。
2. 前往「插件 → Pixabay 图库 → 设置」，填写 **Pixabay API Key**。
3. 选择附件存储策略与附件分组（留空则自动选择/使用默认分组）。
4. 开启「启用定时下载」并配置 cron 表达式，例如每天 10:20：`20 10 * * *`（或 Quartz 写法 `0 20 10 * * ?`）。
5. 左侧「工具」菜单中的「Pixabay 图片下载」页面可手动触发下载、查看下载记录。

## 设置项

| 字段 | 说明 | 默认值 |
|---|---|---|
| Pixabay API Key | 必填，https://pixabay.com/api/docs/ 免费申请 | - |
| 搜索关键词 | 英文逗号分隔 | `mountain,landscape,forest` |
| 每关键词下载数量 | 每次运行每个关键词下载的新图数量 | `50` |
| 图片尺寸 | 原图 / 大图 / 中等 / 缩略图 | `original` |
| 图片类型 | 照片 / 插画 / 矢量图 / 全部 | `photo` |
| 附件存储策略 | 下拉选择，选项实时读取 Halo 存储策略 | - |
| 附件分组 | 下拉选择，选项实时读取 Halo 附件分组 | - |
| 启用定时下载 | 定时任务总开关 | `false` |
| 定时表达式 (cron) | 分 时 日 月 周（兼容带秒 6 段写法） | `0 12 27 * *` |

> 下载规则：自动翻页直到凑够指定数量的**新图**或结果耗尽；只有上传成功的图片才记入去重历史；失败的图下次运行自动重试。

## 第三方服务与数据说明

- 本插件调用 **Pixabay 官方 API**（`pixabay.com/api/`）搜索图片，并从 Pixabay 图片 CDN 下载图片到你的 Halo 附件库。
- **数据仅流向 Pixabay**：发送给 Pixabay 的只有搜索关键词、API Key 与图片尺寸/类型参数；**不会**上传或导出你的站点内容、用户数据、附件、配置或日志。
- 所有外部请求均带 30 秒超时与重试/降级策略（自动尝试多档图片 URL）。
- 下载图片的使用遵循 [Pixabay Content License](https://pixabay.com/service/license-summary/)。
- 本插件与 Pixabay 官方无任何隶属、背书或合作关系。

## 开源协议

[MIT](https://github.com/yyliucha/halo-pixabay-plugin/blob/main/LICENSE)

- 源码仓库：https://github.com/yyliucha/halo-pixabay-plugin
- 问题反馈：https://github.com/yyliucha/halo-pixabay-plugin/issues
