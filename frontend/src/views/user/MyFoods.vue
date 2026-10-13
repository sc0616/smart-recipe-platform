<template>
  <div>
    <el-card style="margin-bottom:16px">
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索菜谱名" clearable style="width:220px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane label="我的收藏菜谱" name="menu">
        <el-table :data="tableData" stripe border v-loading="loading" style="width:100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="图片" width="90">
            <template #default="{ row }">
              <el-image v-if="row.fileId" :src="getFileUrl(row.fileId)" style="width:60px;height:60px;border-radius:6px" fit="cover" />
              <span v-else style="color:#ccc">无图</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="菜谱名称" />
          <el-table-column prop="tips" label="小贴士" show-overflow-tooltip />
        </el-table>
        <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
          :total="total" layout="total,prev,pager,next" @change="loadData" />
      </el-tab-pane>

      <el-tab-pane label="全部食材" name="material">
        <el-table :data="materialData" stripe border v-loading="loading" style="width:100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="图片" width="90">
            <template #default="{ row }">
              <el-image v-if="row.fileId" :src="getFileUrl(row.fileId)" style="width:60px;height:60px;border-radius:6px" fit="cover" />
              <span v-else style="color:#ccc">无图</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="食材名称" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
        </el-table>
        <el-pagination class="pagination" v-model:current-page="materialPageNum" v-model:page-size="pageSize"
          :total="materialTotal" layout="total,prev,pager,next" @change="loadMaterial" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFoodsMenuPage, getFoodsMaterialPage, getFileUrl } from '@/api'

const searchName = ref('null')
const activeTab = ref('menu')
const tableData = ref([])
const materialData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const materialPageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const materialTotal = ref(0)

const loadData = async () => {
  if (activeTab.value === 'menu') {
    loading.value = true
    try {
      const res = await getFoodsMenuPage(searchName.value || 'null', null, pageNum.value, pageSize.value)
      tableData.value = res.data.records
      total.value = res.data.total
    } finally { loading.value = false }
  } else {
    loadMaterial()
  }
}

const loadMaterial = async () => {
  loading.value = true
  try {
    const res = await getFoodsMaterialPage(searchName.value || 'null', materialPageNum.value, pageSize.value)
    materialData.value = res.data.records
    materialTotal.value = res.data.total
  } finally { loading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
