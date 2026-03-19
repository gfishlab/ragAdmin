<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  listUsers,
  updateUser,
} from '@/api/user'
import { resolveErrorMessage } from '@/api/http'
import type {
  CreateUserRequest,
  UpdateUserRequest,
  UserListItem,
} from '@/types/user'

const ROLE_OPTIONS = [
  { value: 'APP_USER', label: '问答前台用户', description: '可登录聊天前台，适合普通组织成员。' },
  { value: 'ADMIN', label: '系统管理员', description: '可登录后台，并拥有用户管理能力。' },
  { value: 'KB_ADMIN', label: '知识库管理员', description: '可登录后台，治理知识库与文档。' },
  { value: 'AUDITOR', label: '审计用户', description: '可登录后台，查看审计与运行轨迹。' },
]

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const ENABLED_STATUS_OPTIONS = STATUS_OPTIONS.filter((item) => item.value)

const loading = ref(false)
const loadError = ref('')
const rows = ref<UserListItem[]>([])
const submitting = ref(false)
const roleSubmitting = ref(false)
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const editingUserId = ref<number | null>(null)
const currentRoleTarget = ref<UserListItem | null>(null)

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const query = reactive({
  keyword: '',
  status: '',
})

const userForm = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  mobile: '',
  status: 'ENABLED',
  roleCodes: [] as string[],
})

const roleForm = reactive({
  roleCodes: [] as string[],
})

const hasData = computed(() => rows.value.length > 0)
const dialogTitle = computed(() => (userDialogMode.value === 'create' ? '新增用户' : '编辑用户'))
const dialogConfirmText = computed(() => (userDialogMode.value === 'create' ? '确认创建' : '确认保存'))
const currentPageSummary = computed(() => {
  return rows.value.reduce(
    (result, item) => {
      result.total += 1
      if (item.status === 'ENABLED') {
        result.enabled += 1
      }
      if (item.roles.includes('APP_USER') || item.roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
        result.appAccess += 1
      }
      if (item.roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
        result.adminAccess += 1
      }
      return result
    },
    {
      total: 0,
      enabled: 0,
      appAccess: 0,
      adminAccess: 0,
    },
  )
})

function statusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function roleTagType(roleCode: string): 'primary' | 'success' | 'warning' | 'info' {
  if (roleCode === 'ADMIN') {
    return 'warning'
  }
  if (roleCode === 'APP_USER') {
    return 'primary'
  }
  if (roleCode === 'KB_ADMIN') {
    return 'success'
  }
  return 'info'
}

function roleLabel(roleCode: string): string {
  return ROLE_OPTIONS.find((item) => item.value === roleCode)?.label ?? roleCode
}

function accessScopes(roles: string[]): string[] {
  const scopes: string[] = []
  if (roles.includes('APP_USER') || roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
    scopes.push('聊天前台')
  }
  if (roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
    scopes.push('后台管理')
  }
  return scopes
}

function normalizeOptionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function resetUserForm(): void {
  editingUserId.value = null
  userDialogMode.value = 'create'
  userForm.username = ''
  userForm.password = ''
  userForm.displayName = ''
  userForm.email = ''
  userForm.mobile = ''
  userForm.status = 'ENABLED'
  userForm.roleCodes = ['APP_USER']
}

function buildCreatePayload(): CreateUserRequest {
  return {
    username: userForm.username.trim(),
    password: userForm.password,
    displayName: userForm.displayName.trim(),
    email: normalizeOptionalValue(userForm.email),
    mobile: normalizeOptionalValue(userForm.mobile),
    status: userForm.status,
    roleCodes: [...userForm.roleCodes],
  }
}

function buildUpdatePayload(): UpdateUserRequest {
  return {
    displayName: userForm.displayName.trim(),
    email: normalizeOptionalValue(userForm.email),
    mobile: normalizeOptionalValue(userForm.mobile),
    status: userForm.status,
    password: userForm.password.trim() ? userForm.password : null,
  }
}

function openCreateDialog(): void {
  resetUserForm()
  userDialogVisible.value = true
}

function openEditDialog(user: UserListItem): void {
  userDialogMode.value = 'edit'
  editingUserId.value = user.id
  userForm.username = user.username
  userForm.password = ''
  userForm.displayName = user.displayName
  userForm.email = user.email ?? ''
  userForm.mobile = user.mobile ?? ''
  userForm.status = user.status
  userForm.roleCodes = [...user.roles]
  userDialogVisible.value = true
}

function openRoleDialog(user: UserListItem): void {
  currentRoleTarget.value = user
  roleForm.roleCodes = [...user.roles]
  roleDialogVisible.value = true
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listUsers({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
    })
    rows.value = response.list
    pagination.total = response.total
  } catch (error) {
    rows.value = []
    pagination.total = 0
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  pagination.pageNo = 1
  await loadData()
}

async function handleReset(): Promise<void> {
  query.keyword = ''
  query.status = ''
  pagination.pageNo = 1
  await loadData()
}

async function handleRefresh(): Promise<void> {
  await loadData()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadData()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadData()
}

async function handleSaveUser(): Promise<void> {
  if (!userForm.displayName.trim()) {
    ElMessage.warning('请输入用户名称')
    return
  }
  if (userDialogMode.value === 'create' && (!userForm.username.trim() || !userForm.password.trim())) {
    ElMessage.warning('新增用户时必须填写账号和密码')
    return
  }

  submitting.value = true
  try {
    if (userDialogMode.value === 'create') {
      await createUser(buildCreatePayload())
      ElMessage.success('用户创建成功')
    } else if (editingUserId.value) {
      await updateUser(editingUserId.value, buildUpdatePayload())
      ElMessage.success('用户资料已更新')
    }
    userDialogVisible.value = false
    resetUserForm()
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function handleSaveRoles(): Promise<void> {
  if (!currentRoleTarget.value) {
    return
  }
  if (roleForm.roleCodes.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }

  roleSubmitting.value = true
  try {
    await assignUserRoles(currentRoleTarget.value.id, {
      roleCodes: [...roleForm.roleCodes],
    })
    roleDialogVisible.value = false
    currentRoleTarget.value = null
    ElMessage.success('用户角色已更新')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    roleSubmitting.value = false
  }
}

onMounted(async () => {
  resetUserForm()
  await loadData()
})
</script>

<template>
  <section class="user-page">
    <header class="user-head soft-panel">
      <div>
        <p class="user-eyebrow">治理</p>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">
          后台统一维护组织用户。`APP_USER` 用于聊天前台白名单，`ADMIN`、`KB_ADMIN`、`AUDITOR` 用于后台治理入口。
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="handleRefresh">刷新列表</el-button>
        <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-card soft-panel">
        <span>当前页用户</span>
        <strong>{{ currentPageSummary.total }}</strong>
        <p>当前分页结果中的用户数量</p>
      </article>
      <article class="summary-card soft-panel">
        <span>启用账号</span>
        <strong>{{ currentPageSummary.enabled }}</strong>
        <p>可以参与登录鉴权的已启用账号</p>
      </article>
      <article class="summary-card soft-panel is-primary">
        <span>前台白名单</span>
        <strong>{{ currentPageSummary.appAccess }}</strong>
        <p>具备聊天前台登录能力的账号数</p>
      </article>
      <article class="summary-card soft-panel is-warm">
        <span>后台治理</span>
        <strong>{{ currentPageSummary.adminAccess }}</strong>
        <p>具备后台管理入口权限的账号数</p>
      </article>
    </section>

    <section class="role-guide soft-panel">
      <article v-for="role in ROLE_OPTIONS" :key="role.value" class="role-guide-item">
        <div class="role-guide-head">
          <el-tag :type="roleTagType(role.value)" effect="plain">{{ role.value }}</el-tag>
          <strong>{{ role.label }}</strong>
        </div>
        <p>{{ role.description }}</p>
      </article>
    </section>

    <section class="filter-panel soft-panel">
      <div class="filter-grid">
        <el-input
          v-model="query.keyword"
          placeholder="搜索账号、用户名、手机号"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.status" placeholder="账号状态">
          <el-option
            v-for="item in STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button @click="handleRefresh">刷新</el-button>
        <el-button type="primary" @click="handleSearch">查询用户</el-button>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <section v-if="loadError" class="table-error">
        <p class="error-text">{{ loadError }}</p>
      </section>

      <el-table v-else :data="rows" v-loading="loading" empty-text="当前暂无用户数据" stripe>
        <el-table-column prop="username" label="登录账号" min-width="160" />
        <el-table-column prop="displayName" label="用户名称" min-width="160" />
        <el-table-column label="联系方式" min-width="220">
          <template #default="{ row }">
            <div class="contact-cell">
              <span>{{ row.mobile || '未配置手机号' }}</span>
              <small>{{ row.email || '未配置邮箱' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="260">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="roleCode in row.roles"
                :key="`${row.id}-${roleCode}`"
                :type="roleTagType(roleCode)"
                effect="plain"
              >
                {{ roleLabel(roleCode) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可进入系统" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="scope in accessScopes(row.roles)"
                :key="`${row.id}-${scope}`"
                effect="plain"
              >
                {{ scope }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-links">
              <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="primary" @click="openRoleDialog(row)">配置角色</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="hasData || pagination.total > 0" class="table-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="userDialogVisible" :title="dialogTitle" width="620px" @closed="resetUserForm">
      <el-form label-position="top">
        <div v-if="userDialogMode === 'create'" class="form-grid">
          <el-form-item label="登录账号">
            <el-input v-model="userForm.username" placeholder="请输入唯一账号" />
          </el-form-item>
          <el-form-item label="初始密码">
            <el-input v-model="userForm.password" type="password" show-password placeholder="请输入初始密码" />
          </el-form-item>
        </div>

        <div v-else class="reset-tip">
          当前编辑账号：<strong>{{ userForm.username }}</strong>
          <span>如需重置密码，直接在下方填写新密码即可。</span>
        </div>

        <div class="form-grid">
          <el-form-item label="用户名称">
            <el-input v-model="userForm.displayName" placeholder="请输入用户名称" />
          </el-form-item>
          <el-form-item label="账号状态">
            <el-select v-model="userForm.status">
              <el-option
                v-for="item in ENABLED_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="手机号">
            <el-input v-model="userForm.mobile" placeholder="可为空" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="userForm.email" placeholder="可为空" />
          </el-form-item>
        </div>

        <el-form-item v-if="userDialogMode === 'edit'" label="新密码">
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            placeholder="留空表示不修改密码"
          />
        </el-form-item>

        <el-form-item v-if="userDialogMode === 'create'" label="初始角色">
          <el-checkbox-group v-model="userForm.roleCodes">
            <el-checkbox v-for="role in ROLE_OPTIONS" :key="role.value" :value="role.value">
              {{ role.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSaveUser">
            {{ dialogConfirmText }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="配置用户角色" width="560px">
      <div class="role-dialog-head">
        <strong>{{ currentRoleTarget?.displayName || currentRoleTarget?.username }}</strong>
        <span>角色变化会直接影响前后台登录入口。</span>
      </div>
      <el-checkbox-group v-model="roleForm.roleCodes" class="role-checkbox-list">
        <label v-for="role in ROLE_OPTIONS" :key="role.value" class="role-checkbox-item">
          <el-checkbox :value="role.value">
            {{ role.label }}
          </el-checkbox>
          <p>{{ role.description }}</p>
        </label>
      </el-checkbox-group>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="roleDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="roleSubmitting" @click="handleSaveRoles">
            保存角色
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.user-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 32px;
  background:
    radial-gradient(circle at right top, rgba(198, 107, 34, 0.12), transparent 32%),
    linear-gradient(180deg, rgba(255, 251, 246, 0.96), rgba(255, 248, 241, 0.9));
}

.user-eyebrow,
.summary-card span {
  margin: 0 0 10px;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.head-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  padding: 22px;
}

.summary-card strong {
  display: block;
  margin-top: 12px;
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  font-size: 30px;
}

.summary-card p {
  margin: 12px 0 0;
  color: #6d5948;
  line-height: 1.7;
}

.summary-card.is-primary {
  background: linear-gradient(180deg, rgba(244, 248, 255, 0.96), rgba(250, 252, 255, 0.92));
}

.summary-card.is-warm {
  background: linear-gradient(180deg, rgba(255, 248, 238, 0.96), rgba(255, 252, 247, 0.9));
}

.role-guide,
.filter-panel,
.table-panel {
  padding: 24px;
}

.role-guide {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.role-guide-item {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 251, 245, 0.74);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.role-guide-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-guide-item p {
  margin: 12px 0 0;
  color: #6d5948;
  line-height: 1.7;
}

.filter-grid,
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.filter-actions,
.dialog-footer,
.table-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.filter-actions,
.table-footer {
  margin-top: 18px;
}

.contact-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.contact-cell small,
.error-text,
.reset-tip span,
.role-dialog-head span {
  color: #8f7159;
}

.tag-list,
.action-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-error {
  padding: 12px 0;
}

.error-text {
  margin: 0;
  line-height: 1.7;
}

.reset-tip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 249, 241, 0.82);
}

.role-dialog-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.role-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-checkbox-item {
  display: block;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 251, 245, 0.82);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.role-checkbox-item p {
  margin: 10px 0 0 24px;
  color: #6d5948;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .summary-grid,
  .role-guide {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .user-head,
  .filter-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-grid,
  .form-grid,
  .summary-grid,
  .role-guide {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .head-actions,
  .filter-actions,
  .dialog-footer {
    flex-direction: column;
    width: 100%;
  }
}
</style>
