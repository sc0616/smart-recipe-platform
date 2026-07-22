<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>🍜 美食菜谱平台</h1>
        <p>发现美味，分享烹饪</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="level">
          <el-radio-group v-model="form.level" class="role-group">
            <el-radio-button :label="1">管理员登录</el-radio-button>
            <el-radio-button :label="2">用户登录</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="name">
          <el-input v-model="form.name" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">登 录</el-button>
        </el-form-item>
        <div class="login-footer">
          <span>还没有账号？</span>
          <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ name: '', password: '', level: 2 })
const rules = {
  name: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form)
    userStore.setLogin(res.data)
    ElMessage.success('登录成功')
    if (res.data.level === 1) {
      router.push('/admin/dashboard')
    } else {
      router.push('/user/home')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5a623 0%, #e74c3c 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-box {
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px;
  width: 420px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.login-header {
  text-align: center;
  margin-bottom: 36px;
}
.login-header h1 {
  font-size: 28px;
  color: #333;
  margin-bottom: 8px;
}
.login-header p {
  color: #999;
  font-size: 14px;
}
.role-group { width: 100%; display: flex; }
.role-group :deep(.el-radio-button) { flex: 1; }
.role-group :deep(.el-radio-button__inner) { width: 100%; }
.login-btn { width: 100%; }
.login-footer { text-align: center; margin-top: -8px; font-size: 14px; color: #666; }
</style>