<template>
  <div class="detail-container" v-loading="loading">
    <el-page-header @back="$router.back()" content="菜谱详情" style="margin-bottom:24px" />
    <el-row :gutter="32" v-if="detail">
      <el-col :span="10">
        <el-image v-if="detail.fileId" :src="getFileUrl(detail.fileId)"
          fit="cover" style="width:100%;border-radius:16px;max-height:400px" />
        <div v-else class="no-img-large">🍴</div>
      </el-col>
      <el-col :span="14">
        <h1 class="detail-title">{{ detail.name }}</h1>
        <div class="detail-meta">
          <span>发布人：{{ detail.userName }}</span>
          <el-tag v-if="classifyName" type="warning" style="margin-left:12px">{{ classifyName }}</el-tag>
        </div>
        <div class="detail-tips" v-if="detail.tips">
          <el-icon><InfoFilled /></el-icon> {{ detail.tips }}
        </div>
        <div class="action-bar">
          <el-button :type="praised ? 'primary' : 'default'" round @click="handlePraise">
            {{ praised ? '已点赞' : '点赞' }} ({{ praiseCount }})
          </el-button>
          <el-button :type="collected ? 'warning' : 'default'" round @click="handleCollect">
            {{ collected ? '已收藏' : '收藏' }}
          </el-button>
        </div>
        <el-divider />
        <div class="detail-desc">
          <h3>菜谱详情</h3>
          <p>{{ detail.description || '暂无详细说明' }}</p>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getFoodsMenuById, getClassifyAll,
  checkPraised, addPraise, deletePraise, getPraisePage,
  checkCollected, addCollect, deleteCollect,
  getFileUrl
} from '@/api'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)
const classifyName = ref('')
const praised = ref(false)
const collected = ref(false)
const praiseCount = ref(0)
const praiseId = ref(null)
const collectId = ref(null)

const loadDetail = async () => {
  loading.value = true
  try {
    const id = route.params.id
    const [detailRes, classifyRes, praisedRes, collectedRes] = await Promise.all([
      getFoodsMenuById(id),
      getClassifyAll(),
      checkPraised(id),
      checkCollected(id)
    ])
    detail.value = detailRes.data
    praised.value = praisedRes.data
    collected.value = collectedRes.data
    const classify = classifyRes.data.find(c => c.id === detailRes.data.classifyId)
    classifyName.value = classify?.name || ''

    const praisePageRes = await getPraisePage('null', null, 1, 9999)
    const allPraises = praisePageRes.data.records
    praiseCount.value = allPraises.filter(p => String(p.foodsId) === String(id)).length
    const myPraise = allPraises.find(p => String(p.foodsId) === String(id) && praised.value)
    if (myPraise) praiseId.value = myPraise.id
  } finally {
    loading.value = false
  }
}

const handlePraise = async () => {
  if (praised.value) {
    if (praiseId.value) {
      await deletePraise(praiseId.value)
      praised.value = false
      praiseCount.value--
      praiseId.value = null
      ElMessage.success('已取消点赞')
    }
  } else {
    const res = await addPraise({ foodsId: Number(route.params.id) })
    praised.value = true
    praiseCount.value++
    praiseId.value = res.data.id
    ElMessage.success('点赞成功')
  }
}

const handleCollect = async () => {
  if (collected.value) {
    if (collectId.value) {
      await deleteCollect(collectId.value)
      collected.value = false
      collectId.value = null
      ElMessage.success('已取消收藏')
    }
  } else {
    const res = await addCollect({ foodsId: Number(route.params.id) })
    collected.value = true
    collectId.value = res.data.id
    ElMessage.success('收藏成功')
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-container { max-width: 1000px; margin: 0 auto; }
.detail-title { font-size: 28px; font-weight: 700; color: #333; margin-bottom: 12px; }
.detail-meta { color: #999; font-size: 14px; margin-bottom: 16px; }
.detail-tips {
  background: #fff8e6;
  border-left: 4px solid #f5a623;
  padding: 12px 16px;
  border-radius: 6px;
  color: #666;
  font-size: 14px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.action-bar { display: flex; gap: 12px; margin-bottom: 20px; }
.detail-desc h3 { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.detail-desc p { color: #555; line-height: 1.8; white-space: pre-wrap; }
.no-img-large {
  width: 100%;
  height: 300px;
  background: #f5f5f5;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60px;
}
</style>
