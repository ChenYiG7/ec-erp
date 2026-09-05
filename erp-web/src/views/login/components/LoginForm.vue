<template>
  <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large">
    <el-form-item prop="username">
      <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username">
        <template #prefix>
          <el-icon class="el-input__icon">
            <user />
          </el-icon>
        </template>
      </el-input>
    </el-form-item>
    <el-form-item prop="password">
      <el-input
        v-model="loginForm.password"
        type="password"
        placeholder="密码"
        show-password
        autocomplete="new-password"
      >
        <template #prefix>
          <el-icon class="el-input__icon">
            <lock />
          </el-icon>
        </template>
      </el-input>
    </el-form-item>
  </el-form>
  <div class="login-btn">
    <el-button :icon="CircleClose" round size="large" @click="resetForm(loginFormRef)"> 重置 </el-button>
    <el-button :icon="UserFilled" round size="large" type="primary" :loading="loading" @click="login(loginFormRef)">
      登录
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ElButton, ElForm, ElFormItem, ElIcon, ElInput, ElNotification } from 'element-plus'
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTimeState, parseRedirect } from '@/utils'
import { AuthApi, type ReqLoginForm } from '@/api/apis/system/auth'
import { useUserStore } from '@/stores/modules/user'
import { useTabsStore } from '@/stores/modules/tabs'
import { useKeepAliveStore } from '@/stores/modules/keepAlive'
import { initDynamicRouter } from '@/routers/modules/dynamicRouter'
import { CircleClose, UserFilled } from '@element-plus/icons-vue'
import { useLoadingStore } from '@/stores/modules/loading'
import { storeToRefs } from 'pinia'

// todo caps lock
// todo forget password
// todo remember me
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const tabsStore = useTabsStore()
const keepAliveStore = useKeepAliveStore()

type FormInstance = InstanceType<typeof ElForm>
const loginFormRef = ref<FormInstance>()
const loginRules = reactive({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
})

const { loading } = storeToRefs(useLoadingStore())
// 安全红线:禁硬编码/提示默认凭证(docs/09 §10)
const loginForm = reactive<ReqLoginForm>({
  username: '',
  password: ''
})

// login
const login = (formEl: FormInstance | undefined) => {
  if (!formEl) {
    return
  }
  formEl.validate(async valid => {
    if (!valid) {
      return
    }
    try {
      // 1.执行登录接口(后端 BCrypt 校验明文密码,前端禁做摘要/加盐)
      const data = await AuthApi.login({ ...loginForm })
      userStore.setToken(data.token!)

      // 2.添加动态路由
      await initDynamicRouter()

      // 3.清空 tabs、keepAlive 数据
      tabsStore.setTabs([])
      keepAliveStore.setKeepAliveName([])

      // 4.跳转到 redirect 或首页
      const { path, queryParams } = parseRedirect(route.query)
      router.push({ path, query: queryParams })
      ElNotification({
        title: getTimeState(),
        message: `欢迎登录 ${import.meta.env.VITE_GLOB_APP_TITLE}`,
        type: 'success',
        duration: 3000
      })
    } catch (error) {
      // 拦截器已统一弹错,这里仅吞掉 Promise 链
    }
  })
}

// resetForm
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) {
    return
  }
  formEl.resetFields()
}

onMounted(() => {
  // 监听 enter 事件（调用登录）
  document.onkeydown = (e: KeyboardEvent) => {
    if (e.code === 'Enter' || e.code === 'enter' || e.code === 'NumpadEnter') {
      if (loading.value) {
        return
      }
      login(loginFormRef.value)
    }
  }
})

onBeforeUnmount(() => {
  document.onkeydown = null
})
</script>

<style scoped lang="scss">
@use '../index';
</style>
