<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { Toast } from '@halo-dev/components'

const API_PREFIX =
  '/apis/console.api.pixabay.halo.run/v1alpha1/plugins/pixabay-downloader'

const loading = ref(false)
const record = ref<Record<string, any> | null>(null)
const error = ref('')

async function fetchRecord() {
  const { data } = await axios.get(`${API_PREFIX}/record`)
  record.value = data
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
  <div class="p-6">
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-800">Pixabay 图片下载</h1>
      <p class="mt-1 text-sm text-gray-500">
        从 Pixabay 官方 API 下载图片到附件库。已下载的图片按 Pixabay ID 全局去重，永不重复上传。
      </p>
    </div>

    <div class="mb-6">
      <button
        class="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="loading"
        @click="triggerDownload"
      >
        <span v-if="loading" class="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
        <span>{{ loading ? '下载中...' : '立即下载' }}</span>
      </button>
      <a
        href="/console/attachments"
        class="ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50"
      >
        查看附件库
      </a>
    </div>

    <p v-if="error" class="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-600">{{ error }}</p>

    <div class="overflow-hidden rounded-lg border border-gray-200 bg-white">
      <table class="min-w-full divide-y divide-gray-200 text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">项目</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">值</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr>
            <td class="px-4 py-3 text-gray-500">已下载图片数（全局去重）</td>
            <td class="px-4 py-3 font-medium text-gray-800">
              {{ record?.spec?.downloadedIds?.length ?? 0 }}
            </td>
          </tr>
          <tr>
            <td class="px-4 py-3 text-gray-500">最近一次运行时间</td>
            <td class="px-4 py-3 text-gray-800">{{ record?.spec?.lastRunAt || '尚未运行' }}</td>
          </tr>
          <tr>
            <td class="px-4 py-3 text-gray-500">最近一次结果</td>
            <td class="px-4 py-3 text-gray-800">
              {{ record?.spec?.lastRunMessage || '尚未运行' }}
            </td>
          </tr>
          <tr>
            <td class="px-4 py-3 text-gray-500">最近一次新增</td>
            <td class="px-4 py-3 text-gray-800">{{ record?.spec?.lastRunAdded ?? '-' }} 张</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="mt-4 text-xs text-gray-400">
      定时下载与关键词等配置请在「插件 → Pixabay 图片下载 → 设置」中调整。
    </p>
  </div>
</template>
