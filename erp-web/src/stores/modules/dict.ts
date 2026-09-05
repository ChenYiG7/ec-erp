import { defineStore } from 'pinia'
import type { DictItem } from '@/api/interface'
import { useStorage } from '@vueuse/core'
import { DictApi } from '@/api/apis/system/dict'
import { DICT_CACHE_TIME } from '@/constants'

/**
 * 字典 store:按 dictType 惰性加载(GET /api/system/dicts/type/{dictType})并缓存 30 天
 * 供下拉框/枚举标签渲染(components/Dict、生成页 enum 渲染走 spec 内联)
 */
export const useDictStore = defineStore('erp-dict', () => {
  const dict = useStorage<Record<string, { list: DictItem[]; __cache_time: number }>>('erp-dict', {}, localStorage, {
    mergeDefaults: true
  })

  const getDict = (code: string) => {
    const cached = dict.value[code]
    if (cached && cached.__cache_time && Date.now() - cached.__cache_time < DICT_CACHE_TIME) {
      return Promise.resolve(cached.list)
    }
    return DictApi.getDictByType(code).then(data => {
      dict.value[code] = { list: data, __cache_time: Date.now() }
      return data
    })
  }

  const clearDict = () => {
    dict.value = {}
  }

  return { getDict, clearDict }
})
