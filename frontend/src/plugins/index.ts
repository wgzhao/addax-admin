/**
 * plugins/index.ts
 *
 * Automatically included in `./src/main.ts`
 */

// Plugins
import vuetify from './vuetify'
import router from '@/router'
import p from './pinia'

// Types
import type { App } from 'vue'

//highlight
// 延迟加载 highlight.js：仅在实际需要的组件中动态导入
// import HljsVuePlugin from '@highlightjs/vue-plugin'
// import 'highlight.js/styles/github-dark.css'
// import 'highlight.js/lib/common'

export function registerPlugins(app: App) {
  app.use(vuetify).use(router).use(p)
  // 如果后续需要代码高亮，可在对应组件里：
  // const { default: HljsVuePlugin } = await import('@highlightjs/vue-plugin')
  // import('highlight.js/styles/github-dark.css')
  // app.use(HljsVuePlugin)
}
