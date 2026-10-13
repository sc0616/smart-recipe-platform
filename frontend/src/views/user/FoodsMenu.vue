<template>
  <div>
    <el-card class="filter-card">
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索菜谱名" clearable style="width:220px" @keyup.enter="loadData" />
        <el-select v-model="classifyId" placeholder="全部分类" clearable style="width:160px" @change="loadData">
          <el-option v-for="c in classifyList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button type="success" :icon="Plus" @click="openAdd">发布菜谱</el-button>
      </div>
    </el-card>

    <el-row :gutter="20" style="margin-top:20px" v-loading="loading">
      <el-col :span="6" v-for="item in tableData" :key="item.id">
        <div class="menu-card" @click="$router.push('/user/foodsMenu/' + item.id)">
          <div class="menu-img">
            <el-image v-if="item.fileId" :src="getFileUrl(item.fileId)" fit="cover" style="width:100%;height:100%" />
            <div v-else class="no-img">🍴</div>
          </div>
          <div class="menu-body">
            <div class="menu-name">{{ item.name }}</div>
            <div class="menu-tips">{{ item.tips || '暂无小贴士' }}</div>
            <div class="menu-meta">
              <span>{{ item.userName }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="tableData.length === 0 && !loading" class="empty">
      <el-empty description="暂无菜谱" />
    </div>

    <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total,prev,pager,next" @change="loadData" />

    <el-dialog v-model="dialogVisible" title="发布菜谱" width="560px" @close="resetForm">
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
          <el-input v-model="form.tips" placeholder="烹饪小技巧" />
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="菜谱详细步骤..." />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload action="#" :http-request="handleUpload" :show-file-list="false" accept="image/*">
            <div v-if="form.fileId" class="upload-preview">
              <el-image :src="getFileUrl(form.fileId)" fit="cover" style="width:120px;height:90px;border-radius:8px" />
              <div class="upload-tip">点击重新上传</div>
            </div>
            <el-button v-else :icon="Upload" type="default">选择图片</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getFoodsMenuPage, addFoodsMenu, getClassifyAll, uploadImage, getFileUrl } from '@/api'

const route = useRoute()
const searchName = ref('null')
const classifyId = ref(null)
const classifyList = ref([])
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ name: '', classifyId: null, tips: '', description: '', fileId: null, fileName: '' })
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

const openAdd = () => { dialogVisible.value = true }

const resetForm = () => {
  Object.assign(form, { name: '', classifyId: null, tips: '', description: '', fileId: null, fileName: '' })
  formRef.value?.resetFields()
}

const handleUpload = async ({ file }) => {
  const fd = new FormData()
  fd.append('file', file)
  const res = await uploadImage(fd)
  form.fileId = Number(res.data.id)
  form.fileName = res.data.title
  ElMessage.success('图片上传成功')
}

const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    await addFoodsMenu(form)
    ElMessage.success('菜谱发布成功')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

onMounted(async () => {
  const res = await getClassifyAll()
  classifyList.value = res.data
  if (route.query.classifyId) classifyId.value = Number(route.query.classifyId)
  loadData()
})
</script>

<style scoped>
.filter-card { margin-bottom: 4px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.menu-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 20px;
}
.menu-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
.menu-img { height: 160px; background: #f5f5f5; overflow: hidden; }
.no-img { height: 100%; display: flex; align-items: center; justify-content: center; font-size: 40px; }
.menu-body { padding: 14px; }
.menu-name { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.menu-tips { font-size: 13px; color: #999; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.menu-meta { font-size: 12px; color: #bbb; }
.pagination { margin-top: 20px; justify-content: center; }
.empty { margin: 40px 0; }
.upload-preview { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.upload-tip { font-size: 12px; color: #999; }
</style>