<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索账号" clearable style="width:220px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button type="success" :icon="Plus" @click="openAdd">新增用户</el-button>
      </div>
      <el-table :data="tableData" stripe border style="width:100%;margin-top:12px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="账号" />
        <el-table-column prop="nickName" label="昵称" />
        <el-table-column prop="sex" label="性别" width="70" />
        <el-table-column prop="age" label="年龄" width="70" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" layout="total,prev,pager,next,sizes" @change="loadData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="520px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="name">
          <el-input v-model="form.name" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickName" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio label="男">男</el-radio><el-radio label="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄"><el-input-number v-model="form.age" :min="1" :max="120" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, addUser, updateUser, deleteUser } from '@/api'

const searchName = ref('null')
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', password: '', nickName: '', sex: '男', age: null, phone: '', email: '', address: '' })
const rules = {
  name: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUserPage(searchName.value || 'null', pageNum.value, pageSize.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}
const openAdd = () => { isEdit.value = false; dialogVisible.value = true }
const openEdit = (row) => { isEdit.value = true; Object.assign(form, row); dialogVisible.value = true }
const resetForm = () => {
  Object.assign(form, { id: null, name: '', password: '', nickName: '', sex: '男', age: null, phone: '', email: '', address: '' })
  formRef.value?.resetFields()
}
const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    isEdit.value ? await updateUser(form) : await addUser(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}
const handleDelete = (id) => {
  ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }).then(async () => {
    await deleteUser(id)
    ElMessage.success('删除成功')
    loadData()
  })
}
onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>