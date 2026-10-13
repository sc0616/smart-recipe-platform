<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="header-left">
        <span class="logo">🍜 美食菜谱</span>
        <el-menu mode="horizontal" :default-active="activeMenu" router class="nav-menu"
          background-color="transparent" text-color="#333" active-text-color="#f5a623">
          <el-menu-item index="/user/home">首页</el-menu-item>
          <el-menu-item index="/user/foodsMenu">菜谱大全</el-menu-item>
          <el-menu-item index="/user/myFoods">我的菜谱</el-menu-item>
          <el-menu-item index="/user/myCollect">我的收藏</el-menu-item>
          <el-menu-item index="/user/ai">🤖 AI 助手</el-menu-item>
        </el-menu>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar size="small" :style="{ backgroundColor: '#f5a623' }">
              {{ userStore.userInfo?.nickName?.[0] || 'U' }}
            </el-avatar>
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
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path.startsWith('/user/foodsMenu') ? '/user/foodsMenu' : route.path)

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
      userStore.logout()
      router.push('/login')
    })
  } else if (cmd === 'password') {
    router.push('/user/updatePassword')
  }
}
</script>

<style scoped>
.layout-container { min-height: 100vh; background: #f8f8f8; }
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  padding: 0 40px;
  height: 64px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.header-left { display: flex; align-items: center; gap: 24px; }
.logo { font-size: 20px; font-weight: 700; color: #e74c3c; white-space: nowrap; }
.nav-menu { border-bottom: none; flex: 1; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.username { font-size: 14px; color: #333; }
.main { padding: 24px 40px; }
</style>