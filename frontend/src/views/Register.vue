<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>🍜 美食菜谱平台</h1>
        <p>注册新账号</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="level">
          <el-radio-group v-model="form.level" class="role-group">
            <el-radio-button :label="1">注册管理员</el-radio-button>
            <el-radio-button :label="2">注册用户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="name">
          <el-input v-model="form.name" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="nickName">
          <el-input v-model="form.nickName" placeholder="请输入昵称" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请确认密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleRegister">注 册</el-button>
        </el-form-item>
        <div class="login-footer">
          <span>已有账号？</span>
          <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register } from '@/api'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ name: '', nickName: '', password: '', confirmPassword: '', level: 2 })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次密码不一致'))
  else callback()
}

const rules = {
  name: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

const handleRegister = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
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
.login-header { text-align: center; margin-bottom: 36px; }
.login-header h1 { font-size: 28px; color: #333; margin-bottom: 8px; }
.login-header p { color: #999; font-size: 14px; }
.role-group { width: 100%; display: flex; }
.role-group :deep(.el-radio-button) { flex: 1; }
.role-group :deep(.el-radio-button__inner) { width: 100%; }
.login-btn { width: 100%; }
.login-footer { text-align: center; margin-top: -8px; font-size: 14px; color: #666; }
</style>