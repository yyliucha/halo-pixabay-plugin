<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

const loading = ref(false)
const record = ref<Record<string, any> | null>(null)
const error = ref('')

async function fetchRecord() {
  try {
    const { data } = await axios.get(`${API_PREFIX}/record`)
    record.value = data
  } catch (e: any) {
    const detail = e?.response?.data?.detail || e?.response?.data?.message || e.message
    error.value = String(detail || e)
  }
}

async function triggerDownload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.post(`${API_PREFIX}/download`)
    Toast.success(`下载完成：新增 ${data.added} 张，失败 ${data.failed} 张`)
    await fetchRecord()
  } catch (e: any) {
    const detail = e?.response?.data?.detail || e?.response?.data?.message || e.message
    error.value = String(detail || e)
    Toast.error(`下载失败：${error.value}`)
  } finally {
    loading.value = false
  }
}

onMounted(fetchRecord)
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
          message="点击右上角「立即下载」开始从 Pixabay 拉取图片"
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
