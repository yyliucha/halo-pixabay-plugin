import { definePlugin } from '@halo-dev/console-shared'
import PixabayDownloadView from './views/PixabayDownloadView.vue'
import { IconPlug } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/pixabay-downloader',
        name: 'PixabayDownloader',
        component: PixabayDownloadView,
        meta: {
          title: 'Pixabay 图片下载',
          searchable: true,
          menu: {
            name: 'Pixabay 图片下载',
            group: '工具',
            icon: markRaw(IconPlug),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
