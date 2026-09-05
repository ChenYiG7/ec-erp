// @see https://unocss.dev/guide/config-file

import {
  defineConfig,
  presetWind4,
  presetAttributify,
  presetIcons,
  presetWebFonts,
  presetTypography,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'
import { FileSystemIconLoader } from 'unplugin-icons/loaders'
import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'

const iconsDir = './src/assets/icons/svg'

// 全部加载本地图标，为了图标选择器使用，正式项目如果没有图标选择的需求，可以删除此部分代码，让组件做按需加载
const generateLocalIconSafelist = () => {
  const iconPath = resolve(iconsDir)
  const files = readdirSync(iconPath)
  return files.filter(file => file.endsWith('.svg')).map(file => `i-localSvgIcon:${file.replace('.svg', '')}`)
}

export default defineConfig({
  theme: {
    colors: {
      primary: 'var(--el-color-primary)',
      primary_dark: 'var(--el-color-primary-light-5)',
    },
  },
  presets: [
    presetWind4({
      preflights: {
        reset: true,
      },
    }),
    presetAttributify(),

    presetIcons({
      autoInstall: true,
      // 额外属性
      collections: {
        localSvgIcon: FileSystemIconLoader(iconsDir, svg => {
          return svg.includes('fill="') ? svg : svg.replace(/^<svg /, '<svg fill="currentColor" ')
        }),
      },
      prefix: 'i-',
      extraProperties: {
        display: 'inline-block',
        width: '1em',
        height: '1em',
        'vertical-align': 'middle',
      },
      scale: 1.2,
    }),
    presetTypography(),
    presetWebFonts({
      fonts: {
        // ...
      },
    }),
  ],
  safelist: generateLocalIconSafelist(),
  transformers: [transformerDirectives(), transformerVariantGroup()],
})
