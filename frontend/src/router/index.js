import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue')
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requireAuth: true, level: 1 },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue') },
      { path: 'adminInfo', name: 'AdminInfo', component: () => import('@/views/admin/AdminInfo.vue') },
      { path: 'userInfo', name: 'UserInfo', component: () => import('@/views/admin/UserInfo.vue') },
      { path: 'classifyInfo', name: 'ClassifyInfo', component: () => import('@/views/admin/ClassifyInfo.vue') },
      { path: 'foodsMaterialInfo', name: 'AdminFoodsMaterial', component: () => import('@/views/admin/FoodsMaterialInfo.vue') },
      { path: 'foodsMenuInfo', name: 'AdminFoodsMenu', component: () => import('@/views/admin/FoodsMenuInfo.vue') },
      { path: 'praiseInfo', name: 'AdminPraise', component: () => import('@/views/admin/PraiseInfo.vue') },
      { path: 'collectInfo', name: 'AdminCollect', component: () => import('@/views/admin/CollectInfo.vue') },
      { path: 'ai', name: 'AdminAi', component: () => import('@/views/assistant/AiChat.vue') },
      { path: 'updatePassword', name: 'AdminUpdatePwd', component: () => import('@/views/UpdatePassword.vue') }
    ]
  },
  {
    path: '/user',
    component: () => import('@/views/user/UserLayout.vue'),
    meta: { requireAuth: true, level: 2 },
    children: [
      { path: '', redirect: '/user/home' },
      { path: 'home', name: 'UserHome', component: () => import('@/views/user/Home.vue') },
      { path: 'foodsMenu', name: 'UserFoodsMenu', component: () => import('@/views/user/FoodsMenu.vue') },
      { path: 'foodsMenu/:id', name: 'FoodsMenuDetail', component: () => import('@/views/user/FoodsMenuDetail.vue') },
      { path: 'myFoods', name: 'MyFoods', component: () => import('@/views/user/MyFoods.vue') },
      { path: 'myCollect', name: 'MyCollect', component: () => import('@/views/user/MyCollect.vue') },
      { path: 'ai', name: 'UserAi', component: () => import('@/views/assistant/AiChat.vue') },
      { path: 'updatePassword', name: 'UserUpdatePwd', component: () => import('@/views/UpdatePassword.vue') }
    ]
  },
  { path: '/', redirect: '/login' },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')

  if (to.meta.requireAuth) {
    if (!token) {
      return next('/login')
    }
    if (to.meta.level && userInfo?.level !== to.meta.level) {
      return next('/login')
    }
  }
  next()
})

export default router