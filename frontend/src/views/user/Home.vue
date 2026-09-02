<template>
  <div>
    <div class="banner">
      <h1>发现美味，分享烹饪</h1>
      <p>探索来自世界各地的精选菜谱，让每一餐都成为享受</p>
      <div class="banner-actions">
        <el-button type="primary" size="large" round @click="$router.push('/user/foodsMenu')">
          探索菜谱
        </el-button>
        <el-button size="large" round class="ai-btn" @click="$router.push('/user/ai')">
          🤖 AI 美食助手
        </el-button>
      </div>
    </div>

    <div class="section">
      <h2 class="section-title">菜谱分类</h2>
      <el-row :gutter="16">
        <el-col :span="4" v-for="c in classifyList" :key="c.id">
          <div class="classify-card" @click="goToMenu(c.id)">
            <div class="classify-icon">🍽️</div>
            <div class="classify-name">{{ c.name }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="section">
      <h2 class="section-title">最新菜谱</h2>
      <el-row :gutter="20">
        <el-col :span="6" v-for="item in latestMenus" :key="item.id">
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
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getClassifyAll, getFoodsMenuPage, getFileUrl } from '@/api'

const router = useRouter()
const classifyList = ref([])
const latestMenus = ref([])

const goToMenu = (classifyId) => {
  router.push({ path: '/user/foodsMenu', query: { classifyId } })
}

onMounted(async () => {
  const [c, m] = await Promise.all([
    getClassifyAll(),
    getFoodsMenuPage('null', null, 1, 8)
  ])
  classifyList.value = c.data
  latestMenus.value = m.data.records
})
</script>

<style scoped>
.banner {
  background: linear-gradient(135deg, #f5a623 0%, #e74c3c 100%);
  border-radius: 16px;
  padding: 60px 40px;
  text-align: center;
  color: #fff;
  margin-bottom: 40px;
}
.banner h1 { font-size: 36px; margin-bottom: 12px; }
.banner p { font-size: 16px; opacity: 0.9; margin-bottom: 24px; }
.banner-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.ai-btn {
  background: rgba(255, 255, 255, 0.95) !important;
  border-color: transparent !important;
  color: #e74c3c !important;
  font-weight: 600;
}
.ai-btn:hover { background: #fff !important; transform: translateY(-2px); }
.section { margin-bottom: 40px; }
.section-title { font-size: 22px; font-weight: 700; margin-bottom: 20px; color: #333; }
.classify-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 16px;
}
.classify-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
.classify-icon { font-size: 32px; margin-bottom: 8px; }
.classify-name { font-size: 14px; color: #555; font-weight: 500; }
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
.menu-tips { font-size: 13px; color: #999; margin-bottom: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.menu-meta { font-size: 12px; color: #bbb; }
</style>