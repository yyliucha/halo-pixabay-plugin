# Halo Pixabay 图库插件

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.java.com/)
[![Halo](https://img.shields.io/badge/Halo-2.20%2B-4b5563.svg)](https://www.halo.run/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

一个 [Halo 2.x](https://www.halo.run/) 插件：通过 **Pixabay 官方 API** 下载图片并保存到 Halo 附件库，支持定时自动下载、全局去重、手动触发与下载记录。

[English](README.md)

## 功能特性

- ✅ **Pixabay 官方 API**：稳定合规，不爬网页
- ✅ **全局去重**：已下载的图片按 Pixabay ID 记录，**永不重复上传**（跨运行生效）
- ✅ **定时自动下载**：设置页可配 cron 表达式（如 `0 12 27 * *` = 每月27日 12:00）；首次启用约 1 分钟内自动执行一次，便于验证
- ✅ **手动触发**：控制台一键「立即下载」
- ✅ **附件无缝集成**：通过 Halo `AttachmentService` 上传，落入你配置的存储策略（本地/OSS/S3 等），附件库可见、可直接插入文章
- ✅ **四档尺寸**：原图 / 大图(约1280px) / 中等(640px) / 缩略图(150px)
- ✅ **自动重试**：搜索请求指数退避重试
- ✅ **下载记录**：控制台展示最近运行时间、新增数量与结果信息
- ✅ **关键词映射归档**：可为每个关键词独立指定存储策略与附件分组（未配置的关键词走全局兜底）
- ✅ **发布文章自动配图**：开启后，发布无封面文章时自动按文章标签/标题关键词从 Pixabay 选图下载，并设为文章封面（图片同时进入附件库与去重记录）

## 环境要求

- Halo **>= 2.20.0**（运行于 JDK 21）
- 免费 Pixabay API Key：https://pixabay.com/api/docs/

## 安装

1. 构建插件（需要 JDK 17+ 与 Node.js 18+）：

   ```bash
   ./gradlew build
   ```

   产物：`build/libs/halo-pixabay-plugin-1.0.0-SNAPSHOT.jar`

2. Halo 后台 → **插件 → 安装**，上传 jar 并启用。

3. 打开 **插件 → Pixabay 图片下载 → 设置**，填写 **Pixabay API Key**，按需调整关键词/数量/尺寸/cron，勾选启用定时下载。

4. 左侧菜单「工具」分组下的 **Pixabay 图片下载** 页面可手动触发下载并查看下载记录。

## 设置项

| 字段 | 说明 | 默认值 |
|---|---|---|
| Pixabay API Key | 必填，在 https://pixabay.com/api/docs/ 获取 | - |
| 搜索关键词 | 英文逗号分隔 | `mountain,landscape,forest` |
| 每关键词下载数量 | 每次运行每个关键词下载的新图数量 | `50` |
| 图片尺寸 | 原图 / 大图 / 中等 / 缩略图 | `original` |
| 图片类型 | 照片 / 插画 / 矢量图 / 全部 | `photo` |
| 附件存储策略 | 下拉选择，选项自动读取 Halo 存储策略；留空自动选择（仅一个策略时） | - |
| 附件分组 | 下拉选择，选项自动读取 Halo 附件分组；留空为默认分组 | - |
| 启用定时下载 | 定时任务总开关 | `false` |
| 定时表达式 (cron) | 分 时 日 月 周 | `0 12 27 * *` |

> 下载规则与姊妹项目 [pixabay-downloader](https://github.com/yyliucha/pixabay-downloader)（Python 版）完全一致：自动翻页直到凑够指定数量的**新图**或结果耗尽；只有上传成功的图片才记入去重历史；失败的图下次运行自动重试。

## 控制台 API

- `POST /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/download` - 手动触发一次下载
- `GET /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/record` - 获取下载历史记录

## 第三方服务与数据说明

- 本插件调用 **Pixabay 官方 API**（`pixabay.com/api/`）搜索图片，并从 Pixabay 图片 CDN（`cdn.pixabay.com` / `pixabay.com/get/`）下载图片到你的 Halo 附件库。
- **数据仅流向 Pixabay**：发送给 Pixabay 的内容只有搜索关键词、API Key 与图片尺寸/类型参数；**不会**上传或导出你的站点内容、用户数据、附件、配置或日志。
- 需要在设置中填写 **Pixabay API Key**：在 https://pixabay.com/api/docs/ 凭免费账号申请即可，费用由 Pixabay 按其政策决定，与本插件无关。
- 所有外部请求均带超时（30 秒）与重试/降级策略（自动尝试多档图片 URL）。
- 下载图片的使用遵循 [Pixabay Content License](https://pixabay.com/service/license-summary/)。
- 本插件与 Pixabay 官方无任何隶属、背书或合作关系，仅在标识"图片来源"时使用其名称。

## 开发

```bash
./gradlew build        # 编译 + 测试 + UI 构建 + 打包
./gradlew test         # 仅运行单元测试
./gradlew halo --args="--reset"   # 可选：启动内置 Halo 本地调试
```

测试覆盖与 Python 版对应：mock API 解析与分页、跨运行去重、缺少 API key、并发保护、cron 到期判断。

## License

[MIT](LICENSE)。下载的图片遵循 [Pixabay Content License](https://pixabay.com/service/license-summary/)。
