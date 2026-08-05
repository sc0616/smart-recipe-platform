<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span>🍜 美食菜谱</span>
        <small>管理后台</small>
      </div>
      <el-menu :default-active="activeMenu" router background-color="#1d2033" text-color="#bfc8d5" active-text-color="#fff">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon><span>数据概览</span>
        </el-menu-item>
        <el-sub-menu index="user">
          <template #title><el-icon><UserFilled /></el-icon><span>用户管理</span></template>
          <el-menu-item index="/admin/adminInfo">管理员管理</el-menu-item>
          <el-menu-item index="/admin/userInfo">普通用户</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/admin/classifyInfo">
          <el-icon><Grid /></el-icon><span>菜谱分类</span>
        </el-menu-item>
        <el-menu-item index="/admin/foodsMaterialInfo">
          <el-icon><Bowl /></el-icon><span>食材管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/foodsMenuInfo">
          <el-icon><KnifeFork /></el-icon><span>菜谱管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/praiseInfo">
          <el-icon><Star /></el-icon><span>点赞记录</span>
        </el-menu-item>
        <el-menu-item index="/admin/collectInfo">
          <el-icon><Collection /></el-icon><span>收藏记录</span>
        </el-menu-item>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar size="small" :style="{ backgroundColor: '#f5a623' }">{{ userStore.userInfo?.nickName?.[0] || 'A' }}</el-avatar>
              <span class="username">{{ userStore.userInfo?.nickName || userStore.userInfo?.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const titleMap = {
  '/admin/dashboard': '数据概览',
  '/admin/adminInfo': '管理员管理',
  '/admin/userInfo': '普通用户',
  '/admin/classifyInfo': '菜谱分类',
  '/admin/foodsMaterialInfo': '食材管理',
  '/admin/foodsMenuInfo': '菜谱管理',
  '/admin/praiseInfo': '点赞记录',
  '/admin/collectInfo': '收藏记录',
  '/admin/updatePassword': '修改密码'
}
const currentTitle = computed(() => titleMap[route.path] || '')

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' }).then(() => {
      userStore.logout()
      router.push('/login')
    })
  } else if (cmd === 'password') {
    router.push('/admin/updatePassword')
  }
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.aside { background: #1d2033; overflow-y: auto; }
.logo {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid #2d3252;
  margin-bottom: 8px;
}
.logo span { color: #fff; font-size: 18px; font-weight: 700; }
.logo small { color: #888; font-size: 12px; margin-top: 4px; }
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
  padding: 0 24px;
}
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.username { font-size: 14px; color: #333; }
.main { background: #f0f2f5; padding: 20px; }
</style>