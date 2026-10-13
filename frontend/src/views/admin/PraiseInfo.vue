<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索用户账号" clearable style="width:220px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
      </div>
      <el-table :data="tableData" stripe border style="width:100%;margin-top:12px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="点赞人" />
        <el-table-column prop="foodsId" label="菜谱ID" width="100" />
        <el-table-column prop="time" label="点赞时间" />
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'danger' : 'success'" size="small">{{ row.level === 1 ? '管理员' : '用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" layout="total,prev,pager,next,sizes" @change="loadData" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPraisePage, deletePraise } from '@/api'

const searchName = ref('null')
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPraisePage(searchName.value || 'null', null, pageNum.value, pageSize.value)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}
const handleDelete = (id) => {
  ElMessageBox.confirm('确定删除该点赞记录吗？', '提示', { type: 'warning' }).then(async () => {
    await deletePraise(id)
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