<template>
  <div class="page-container">
    <el-card class="form-card">
      <template #header><span>修改密码</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width:480px">
        <el-form-item label="原密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">确认修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { updatePassword } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ password: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次密码不一致'))
  else callback()
}
const rules = {
  password: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

const handleSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await updatePassword(form)
    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page-container { padding: 24px; }
.form-card { max-width: 600px; }
</style>