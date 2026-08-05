<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索分类名" clearable style="width:220px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button type="success" :icon="Plus" @click="openAdd">新增分类</el-button>
      </div>
      <el-table :data="tableData" stripe border style="width:100%;margin-top:12px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分类' : '新增分类'" width="480px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
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
import { getClassifyPage, addClassify, updateClassify, deleteClassify } from '@/api'

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
const form = reactive({ id: null, name: '', description: '' })
const rules = { name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }] }

const loadData = async () => {
  loading.value = true
  try {
    const res = await getClassifyPage(searchName.value || 'null', pageNum.value, pageSize.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}
const openAdd = () => { isEdit.value = false; dialogVisible.value = true }
const openEdit = (row) => { isEdit.value = true; Object.assign(form, row); dialogVisible.value = true }
const resetForm = () => { Object.assign(form, { id: null, name: '', description: '' }); formRef.value?.resetFields() }
const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    isEdit.value ? await updateClassify(form) : await addClassify(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}
const handleDelete = (id) => {
  ElMessageBox.confirm('确定删除该分类吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteClassify(id)
    ElMessage.success('删除成功')
    loadData()
  })
}
onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>