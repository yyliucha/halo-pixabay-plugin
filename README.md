# Halo Pixabay Downloader Plugin

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.java.com/)
[![Halo](https://img.shields.io/badge/Halo-2.20%2B-4b5563.svg)](https://www.halo.run/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A [Halo 2.x](https://www.halo.run/) plugin that downloads images from [Pixabay](https://pixabay.com/) via the official API and saves them into your Halo attachment library - with scheduled downloads, global dedupe, and a manual trigger.

[简体中文](README.zh-CN.md)

## Features

- ✅ **Official Pixabay API** - stable and compliant, no scraping
- ✅ **Global dedupe** - every downloaded image is recorded by its Pixabay ID; the same image is **never uploaded twice**, even across runs
- ✅ **Scheduled downloads** - cron expression configurable in the settings page (e.g. `0 12 27 * *` = monthly on the 27th at 12:00); the first enable runs once immediately for verification
- ✅ **Manual trigger** - one-click "Download now" button in the console
- ✅ **Attachment integration** - images are uploaded through the Halo `AttachmentService`, so they land in your configured storage policy (local / OSS / S3, etc.) and appear in the attachment library, ready to insert into posts
- ✅ **Size tiers** - original / large (~1280px) / webformat (640px) / preview (150px)
- ✅ **Automatic retries** - search requests retry with exponential backoff
- ✅ **Download history** - last run time, added count and message shown in the console

## Requirements

- Halo **>= 2.20.0** (runs on JDK 21)
- A free Pixabay API key: https://pixabay.com/api/docs/

## Install

1. Build the plugin (requires JDK 17+ and Node.js 18+):

   ```bash
   ./gradlew build
   ```

   The plugin jar is generated at `build/libs/halo-pixabay-plugin-1.0.0-SNAPSHOT.jar`.

2. In the Halo admin console, go to **Plugins → Install**, upload the jar, and enable the plugin.

3. Open **Plugins → Pixabay 图片下载 → Settings**, fill in your **Pixabay API Key**, adjust keywords / count / size / cron as needed, and enable scheduled downloads.

4. Use the **Pixabay 图片下载** page in the left sidebar ("工具" group) to trigger a manual download and view the download history.

## Settings

| Field | Description | Default |
|---|---|---|
| Pixabay API Key | Required. Get it at https://pixabay.com/api/docs/ | - |
| Keywords | Comma-separated search keywords | `mountain,landscape,forest` |
| Count per keyword | New images to download per keyword per run | `50` |
| Image size | original / large / webformat / preview | `original` |
| Image type | photo / illustration / vector / all | `photo` |
| Attachment policy | Blank = auto-resolve when only one policy exists; fill in when multiple | - |
| Attachment group | Attachment group name; blank = default group | - |
| Enable scheduled downloads | Master switch for the cron trigger | `false` |
| Cron expression | `min hour day month weekday` | `0 12 27 * *` |

> Rules are the same as the companion Python project [pixabay-downloader](https://github.com/yyliucha/pixabay-downloader): pagination continues until the target count of **new** images is reached or results are exhausted; only successfully uploaded images are recorded into the dedupe history; failed ones are retried on the next run.

## Console API

- `POST /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/download` - trigger a manual run
- `GET /apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader/record` - download history record

## Development

```bash
./gradlew build        # compile + tests + UI build + package
./gradlew test         # run unit tests only
./gradlew halo --args="--reset"   # optional: run an embedded Halo for local debugging
```

Test coverage mirrors the Python project: mock API parsing/pagination, dedupe across runs, missing API key, concurrent-run protection, and cron due-check logic.

## License

[MIT](LICENSE). Downloaded images follow the [Pixabay Content License](https://pixabay.com/service/license-summary/).
