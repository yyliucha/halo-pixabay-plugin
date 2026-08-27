<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import {
  IconExternalLinkLine,
  IconHistoryLine,
  IconPlug,
  IconRefreshLine,
  Toast,
  VAlert,
  VButton,
  VCard,
  VEmpty,
  VPageHeader,
  VSpace,
} from '@halo-dev/components'

const API_PREFIX =
  '/apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader'

/** Max time to poll for a triggered download before giving up. */
const MAX_POLL_MS = 10 * 60 * 1000
const POLL_INTERVAL_MS = 3000

const loading = ref(false)
const record = ref<Record<string, any> | null>(null)
const error = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null

async function fetchRecord(silent = false) {
  try {
    const { data } = await axios.get(`${API_PREFIX}/record`)
    record.value = data
    return data
  } catch (e: any) {
    if (!silent) {
      const detail = e?.response?.data?.detail || e?.response?.data?.message || e.message
      error.value = String(detail || e)
    }
    return null
  }
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling(beforeRunAt: string | null | undefined) {
  stopPolling()
  const startedAt = Date.now()
  pollTimer = setInterval(async () => {
    await fetchRecord(true)
    const runAt = record.value?.spec?.lastRunAt
    if (runAt && runAt !== beforeRunAt) {
      stopPolling()
      loading.value = false
      const added = record.value?.spec?.lastRunAdded ?? 0
      const failed = record.value?.spec?.lastRunFailed ?? 0
      const message = record.value?.spec?.lastRunMessage
      if (added > 0 || failed > 0) {
        Toast.success(`下载完成：新增 ${added} 张，失败 ${failed} 张`)
      } else {
        Toast.info(message || '下载完成，未新增图片')
      }
    } else if (Date.now() - startedAt > MAX_POLL_MS) {
      stopPolling()
      loading.value = false
      Toast.info('下载仍在后台执行中，稍后刷新本页即可查看结果')
    }
  }, POLL_INTERVAL_MS)
}

async function triggerDownload() {
  if (loading.value) {
    return
  }
  error.value = ''
  loading.value = true
  const beforeRunAt = record.value?.spec?.lastRunAt ?? null
  try {
    const { data } = await axios.post(`${API_PREFIX}/download`)
    if (data?.status !== 'started') {
      throw new Error('下载任务未正常启动')
    }
    Toast.success('已开始下载，正在后台执行…')
    startPolling(beforeRunAt)
  } catch (e: any) {
    const detail = e?.response?.data?.detail || e?.response?.data?.message || e.message
    error.value = String(detail || e)
    Toast.error(`下载失败：${error.value}`)
    loading.value = false
  }
}

onMounted(fetchRecord)
onUnmounted(stopPolling)
</script>

<template>
  <VPageHeader title="Pixabay 图片下载">
    <template #icon>
      <IconPlug />
    </template>
    <template #actions>
      <VSpace spacing="sm">
        <VButton type="primary" :loading="loading" :disabled="loading" @click="triggerDownload">
          <template #icon>
            <IconRefreshLine />
          </template>
          立即下载
        </VButton>
        <VButton route="/attachments">
          <template #icon>
            <IconExternalLinkLine />
          </template>
          查看附件库
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="page-body">
    <VAlert
      v-if="error"
      type="error"
      title="操作失败"
      :description="error"
      closable
      @close="error = ''"
    />

    <VCard>
      <template #header>
        <div class="card-title">
          <IconHistoryLine />
          <span>下载记录</span>
        </div>
      </template>

      <template #default>
        <div v-if="record?.spec?.lastRunAt" class="stat-grid">
          <div class="stat-item">
            <span class="stat-label">已下载图片数（全局去重）</span>
            <strong class="stat-value">{{ record?.spec?.downloadedIds?.length ?? 0 }}</strong>
          </div>
          <div class="stat-item">
            <span class="stat-label">最近一次运行时间</span>
            <strong class="stat-value">{{ record?.spec?.lastRunAt }}</strong>
          </div>
          <div class="stat-item">
            <span class="stat-label">最近一次结果</span>
            <strong class="stat-value">{{ record?.spec?.lastRunMessage || '无' }}</strong>
          </div>
          <div class="stat-item">
            <span class="stat-label">最近一次新增</span>
            <strong class="stat-value">{{ record?.spec?.lastRunAdded ?? '-' }} 张</strong>
          </div>
        </div>

        <VEmpty
          v-else
          title="尚未运行"
          message="点击右上角「立即下载」开始从 Pixabay 拉取图片，下载在后台执行，本页会自动刷新结果"
        >
          <template #actions>
            <VButton
              type="secondary"
              size="sm"
              :loading="loading"
              :disabled="loading"
              @click="triggerDownload"
            >
              立即下载
            </VButton>
          </template>
        </VEmpty>
      </template>

      <template #footer>
        <p class="page-hint">
          定时下载与关键词等配置请在「插件 → Pixabay 图片下载 → 设置」中调整。
        </p>
      </template>
    </VCard>
  </div>
</template>

<style scoped>
.page-body {
  padding: 16px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
}

.stat-item {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background-color: #fff;
}

.stat-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: #6b7280;
}

.stat-value {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  word-break: break-all;
}

.page-hint {
  font-size: 12px;
  color: #9ca3af;
}
</style>
