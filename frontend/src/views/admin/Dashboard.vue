<template>
  <div>
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="s in stats" :key="s.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-body">
            <div class="stat-icon" :style="{ background: s.color }">
              <el-icon :size="28"><component :is="s.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ s.value }}</div>
              <div class="stat-label">{{ s.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="chart-header">
              <span>数据统计</span>
              <el-select v-model="selectedModel" size="small" style="width:150px" @change="loadChart">
                <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </template>
          <div ref="pieRef" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>柱状图</span></template>
          <div ref="barRef" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getEchartsOptions, getEchartsData, getAdminAll, getUserAll, getFoodsMenuAll, getFoodsMaterialAll } from '@/api'

const stats = ref([
  { label: '管理员', value: 0, icon: 'UserFilled', color: 'linear-gradient(135deg,#667eea,#764ba2)' },
  { label: '用户', value: 0, icon: 'User', color: 'linear-gradient(135deg,#f093fb,#f5576c)' },
  { label: '菜谱', value: 0, icon: 'KnifeFork', color: 'linear-gradient(135deg,#4facfe,#00f2fe)' },
  { label: '食材', value: 0, icon: 'Bowl', color: 'linear-gradient(135deg,#43e97b,#38f9d7)' }
])

const options = ref([])
const selectedModel = ref('userInfo')
const pieRef = ref(null)
const barRef = ref(null)
let pieChart = null
let barChart = null

const loadChart = async () => {
  const res = await getEchartsData(selectedModel.value)
  const charts = res.data
  const pieData = charts.find(c => c.type === 'pie')
  const barData = charts.find(c => c.type === 'bar')

  if (pieData && pieChart) {
    pieChart.setOption({
      title: { text: pieData.title?.text || '', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left', data: pieData.legend },
      series: [{ type: 'pie', radius: '60%', data: pieData.seriesData, emphasis: { itemStyle: { shadowBlur: 10 } } }]
    })
  }
  if (barData && barChart) {
    barChart.setOption({
      title: { text: barData.title?.text || '', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: barData.xAxisData },
      yAxis: { type: 'value' },
      series: [{ name: barData.seriesName, type: 'bar', data: barData.seriesData, itemStyle: { color: '#f5a623' } }]
    })
  }
}

onMounted(async () => {
  const [adminRes, userRes, menuRes, materialRes] = await Promise.all([
    getAdminAll(), getUserAll(), getFoodsMenuAll(), getFoodsMaterialAll()
  ])
  stats.value[0].value = adminRes.data?.length ?? 0
  stats.value[1].value = userRes.data?.length ?? 0
  stats.value[2].value = menuRes.data?.length ?? 0
  stats.value[3].value = materialRes.data?.length ?? 0

  const res = await getEchartsOptions()
  options.value = res.data
  await nextTick()
  pieChart = echarts.init(pieRef.value)
  barChart = echarts.init(barRef.value)
  await loadChart()
})
</script>

<style scoped>
.stat-row { margin-bottom: 4px; }
.stat-card { border-radius: 12px; }
.stat-body { display: flex; align-items: center; gap: 16px; padding: 8px 0; }
.stat-icon { width: 60px; height: 60px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.stat-num { font-size: 28px; font-weight: 700; color: #333; }
.stat-label { font-size: 13px; color: #999; margin-top: 2px; }
.chart-header { display: flex; justify-content: space-between; align-items: center; }
</style>