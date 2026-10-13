<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="searchName" placeholder="搜索菜谱名" clearable style="width:220px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
      </div>
      <el-row :gutter="20" style="margin-top:20px" v-loading="loading">
        <el-col :span="6" v-for="item in tableData" :key="item.id">
          <div class="menu-card">
            <div class="menu-img" @click="goDetail(item.foodsId)">
              <el-image v-if="item.foodsInfo && item.foodsInfo.fileId"
                :src="getFileUrl(item.foodsInfo.fileId)" fit="cover" style="width:100%;height:100%" />
              <div v-else class="no-img">🍴</div>
            </div>
            <div class="menu-body">
              <div class="menu-name" @click="goDetail(item.foodsId)">{{ item.foodsName || '菜谱ID:' + item.foodsId }}</div>
              <div class="menu-time">收藏于 {{ item.time }}</div>
              <el-button size="small" type="danger" text @click="handleCancel(item.id)">取消收藏</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
      <div v-if="tableData.length === 0 && !loading" style="padding:40px 0">
        <el-empty description="暂无收藏" />
      </div>
      <el-pagination class="pagination" v-model:current-page="pageNum" v-model:page-size="pageSize"
        :total="total" layout="total,prev,pager,next" @change="loadData" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCollectPage, deleteCollect, getFoodsMenuById, getFileUrl } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const searchName = ref('null')
const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const goDetail = (foodsId) => {
  if (foodsId) router.push('/user/foodsMenu/' + foodsId)
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCollectPage(searchName.value || 'null', userStore.userInfo.id, pageNum.value, pageSize.value)
    const records = res.data.records
    total.value = res.data.total
    const enriched = await Promise.all(
      records.map(async (item) => {
        if (item.foodsId) {
          try {
            const r = await getFoodsMenuById(item.foodsId)
            item.foodsName = r.data.name
            item.foodsInfo = r.data
          } catch {
            item.foodsName = '菜谱ID:' + item.foodsId
          }
        }
        return item
      })
    )
    tableData.value = enriched
  } finally {
    loading.value = false
  }
}

const handleCancel = (id) => {
  ElMessageBox.confirm('确定取消收藏吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteCollect(id)
    ElMessage.success('已取消收藏')
    loadData()
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; }
.menu-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 20px;
  transition: all 0.2s;
}
.menu-card:hover { box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
.menu-img { height: 160px; background: #f5f5f5; overflow: hidden; cursor: pointer; }
.no-img { height: 100%; display: flex; align-items: center; justify-content: center; font-size: 40px; }
.menu-body { padding: 14px; }
.menu-name { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 4px; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.menu-name:hover { color: #f5a623; }
.menu-time { font-size: 12px; color: #bbb; margin-bottom: 8px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
