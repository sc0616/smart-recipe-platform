<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索菜谱名" clearable style="width:220px" @keyup.enter="loadData" />
        <el-select v-model="classifyId" placeholder="全部分类" clearable style="width:160px" @change="loadData">
          <el-option v-for="c in classifyList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button type="success" @click="openAdd">发布菜谱</el-button>
      </div>
      <el-table :data="tableData" stripe border style="width:100%;margin-top:12px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="图片" width="90">
          <template #default="{ row }">
            <el-image v-if="row.fileId" :src="getFileUrl(row.fileId)" style="width:60px;height:60px;border-radius:6px" fit="cover" />
            <span v-else style="color:#ccc">无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="菜谱名称" />
        <el-table-column prop="tips" label="小贴士" show-overflow-tooltip />
        <el-table-column prop="userName" label="上传人" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" layout="total,prev,pager,next,sizes" @change="loadData" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜谱' : '发布菜谱'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="菜谱名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类" prop="classifyId">
          <el-select v-model="form.classifyId" style="width:100%">
            <el-option v-for="c in classifyList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="小贴士">
          <el-input v-model="form.tips" />
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload action="#" :http-request="handleImgUpload" :show-file-list="false" accept="image/*">
            <el-image v-if="form.fileId" :src="getFileUrl(form.fileId)" style="width:120px;height:80px;border-radius:6px;cursor:pointer" fit="cover" />
            <el-button v-else>选择图片</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFoodsMenuPage, addFoodsMenu, updateFoodsMenu, deleteFoodsMenu, getClassifyAll, uploadImage, getFileUrl } from '@/api'

const searchName = ref('null')
const classifyId = ref(null)
const classifyList = ref([])
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saving = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', classifyId: null, tips: '', description: '', fileId: null, fileName: '' })
const rules = {
  name: [{ required: true, message: '请输入菜谱名称', trigger: 'blur' }],
  classifyId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getFoodsMenuPage(searchName.value || 'null', classifyId.value, pageNum.value, pageSize.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

const openAdd = () => {
  isEdit.value = false
  dialogVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, { id: null, name: '', classifyId: null, tips: '', description: '', fileId: null, fileName: '' })
  formRef.value?.resetFields()
}

const handleImgUpload = async ({ file }) => {
  const fd = new FormData()
  fd.append('file', file)
  const res = await uploadImage(fd)
  form.fileId = Number(res.data.id)
  form.fileName = res.data.title
  ElMessage.success('图片上传成功')
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    isEdit.value ? await updateFoodsMenu(form) : await addFoodsMenu(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

const handleDelete = (id) => {
  ElMessageBox.confirm('确定删除该菜谱吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteFoodsMenu(id)
    ElMessage.success('删除成功')
    loadData()
  })
}

onMounted(async () => {
  const res = await getClassifyAll()
  classifyList.value = res.data
  loadData()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
