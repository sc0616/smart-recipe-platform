import request from '@/utils/request'

// 账号
export const login = (data) => request.post('/login', data)
export const register = (data) => request.post('/register', data)
export const getAccountInfo = () => request.get('/getAccountInfo')
export const updatePassword = (data) => request.put('/updatePassword', data)
export const resetPassword = (data) => request.post('/resetPassword', data)

// 管理员
export const getAdminAll = () => request.get('/adminInfo')
export const getAdminPage = (name, pageNum, pageSize) => request.get(`/adminInfo/page/${name}`, { params: { pageNum, pageSize } })
export const addAdmin = (data) => request.post('/adminInfo', data)
export const updateAdmin = (data) => request.put('/adminInfo', data)
export const deleteAdmin = (id) => request.delete(`/adminInfo/${id}`)
export const getAdminById = (id) => request.get(`/adminInfo/${id}`)

// 用户
export const getUserAll = () => request.get('/userInfo')
export const getUserPage = (name, pageNum, pageSize) => request.get(`/userInfo/page/${name}`, { params: { pageNum, pageSize } })
export const addUser = (data) => request.post('/userInfo', data)
export const updateUser = (data) => request.put('/userInfo', data)
export const deleteUser = (id) => request.delete(`/userInfo/${id}`)
export const getUserById = (id) => request.get(`/userInfo/${id}`)

// 菜谱分类
export const getClassifyAll = () => request.get('/classifyInfo')
export const getClassifyPage = (name, pageNum, pageSize) => request.get(`/classifyInfo/page/${name}`, { params: { pageNum, pageSize } })
export const addClassify = (data) => request.post('/classifyInfo', data)
export const updateClassify = (data) => request.put('/classifyInfo', data)
export const deleteClassify = (id) => request.delete(`/classifyInfo/${id}`)

// 食材
export const getFoodsMaterialAll = () => request.get('/foodsMaterialInfo')
export const getFoodsMaterialPage = (name, pageNum, pageSize) => request.get(`/foodsMaterialInfo/page/${name}`, { params: { pageNum, pageSize } })
export const addFoodsMaterial = (data) => request.post('/foodsMaterialInfo', data)
export const updateFoodsMaterial = (data) => request.put('/foodsMaterialInfo', data)
export const deleteFoodsMaterial = (id) => request.delete(`/foodsMaterialInfo/${id}`)
export const getFoodsMaterialById = (id) => request.get(`/foodsMaterialInfo/${id}`)

// 菜谱
export const getFoodsMenuAll = () => request.get('/foodsMenuInfo')
export const getFoodsMenuPage = (name, classifyId, pageNum, pageSize) => request.get(`/foodsMenuInfo/page/${name}`, { params: { classifyId, pageNum, pageSize } })
export const getFoodsMenuUserPage = (name, userName, level, pageNum, pageSize) => request.get(`/foodsMenuInfo/page/user/${name}`, { params: { userName, level, pageNum, pageSize } })
export const addFoodsMenu = (data) => request.post('/foodsMenuInfo', data)
export const updateFoodsMenu = (data) => request.put('/foodsMenuInfo', data)
export const deleteFoodsMenu = (id) => request.delete(`/foodsMenuInfo/${id}`)
export const getFoodsMenuById = (id) => request.get(`/foodsMenuInfo/${id}`)

// 点赞
export const addPraise = (data) => request.post('/praiseInfo', data)
export const deletePraise = (id) => request.delete(`/praiseInfo/${id}`)
export const getPraisePage = (name, userId, pageNum, pageSize) => request.get(`/praiseInfo/page/${name}`, { params: { userId, pageNum, pageSize } })
export const checkPraised = (foodsId) => request.get('/praiseInfo/check', { params: { foodsId } })

// 收藏
export const addCollect = (data) => request.post('/collectInfo', data)
export const deleteCollect = (id) => request.delete(`/collectInfo/${id}`)
export const getCollectPage = (name, userId, pageNum, pageSize) => request.get(`/collectInfo/page/${name}`, { params: { userId, pageNum, pageSize } })
export const checkCollected = (foodsId) => request.get('/collectInfo/check', { params: { foodsId } })

// 文件
export const uploadFile = (formData) => request.post('/files/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const uploadImage = (formData) => request.post('/files/image/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const getFileUrl = (id) => `/api/files/download/${id}`
export const deleteFile = (id) => request.delete(`/files/${id}`)

// 图表
export const getEchartsData = (modelName) => request.get(`/echarts/get/${modelName}`)
export const getEchartsOptions = () => request.get('/echarts/options')