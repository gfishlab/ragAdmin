<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Collection,
  Connection,
  DataAnalysis,
  Fold,
  Histogram,
  List,
  SwitchButton,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/api/http'
import { hasPermission } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mainPanelRef = ref<HTMLElement>()
const isMobileSidebarOpen = ref(false)
const hasTopbarShadow = ref(false)
const isCompactViewport = ref(false)

const activeMenu = computed(() => route.path)

const menuItems = computed(() => {
  const items = [
    { index: '/dashboard', label: '概览', icon: Histogram, permission: 'DASHBOARD_VIEW' },
    { index: '/knowledge-bases', label: '知识库管理', icon: Collection, permission: 'KB_MANAGE' },
    { index: '/models', label: '模型管理', icon: Connection, permission: 'MODEL_MANAGE' },
    { index: '/users', label: '用户管理', icon: User, permission: 'USER_MANAGE' },
    { index: '/vector-indexes', label: '向量索引', icon: DataAnalysis, permission: 'STATISTICS_VIEW' },
    { index: '/tasks', label: '任务监控', icon: List, permission: 'TASK_VIEW' },
    { index: '/audit-logs', label: '审计日志', icon: Tickets, permission: 'AUDIT_VIEW' },
  ]
  return items.filter((item) => hasPermission(authStore.currentUser, item.permission))
})

function syncViewportState(): void {
  isCompactViewport.value = window.innerWidth <= 960
  if (!isCompactViewport.value) {
    isMobileSidebarOpen.value = false
  }
}

function handleMainPanelScroll(): void {
  hasTopbarShadow.value = (mainPanelRef.value?.scrollTop ?? 0) > 6
}

function toggleMobileSidebar(): void {
  isMobileSidebarOpen.value = !isMobileSidebarOpen.value
}

function closeMobileSidebar(): void {
  isMobileSidebarOpen.value = false
}

async function handleLogout(): Promise<void> {
  try {
    await authStore.logout()
    ElMessage.success('已退出登录')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    await router.replace('/login')
  }
}

onMounted(() => {
  syncViewportState()
  handleMainPanelScroll()
  window.addEventListener('resize', syncViewportState)
  mainPanelRef.value?.addEventListener('scroll', handleMainPanelScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportState)
  mainPanelRef.value?.removeEventListener('scroll', handleMainPanelScroll)
})

watch(() => route.fullPath, () => {
  closeMobileSidebar()
})
</script>

<template>
  <div class="admin-shell">
    <div
      v-if="isCompactViewport && isMobileSidebarOpen"
      class="mobile-sidebar-mask"
      @click="closeMobileSidebar"
    />

    <aside
      class="side-panel soft-panel"
      :class="{ 'is-mobile-open': isMobileSidebarOpen, 'is-compact': isCompactViewport }"
    >
      <div class="brand-block">
        <div class="brand-sign">RA</div>
        <div>
          <p class="brand-caption">RAG ADMIN</p>
          <h1>知识工坊控制台</h1>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="nav-menu"
        router
        background-color="transparent"
        text-color="#57534E"
        active-text-color="#C2410C"
        @select="closeMobileSidebar"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="content-shell">
      <header class="topbar soft-panel" :class="{ 'is-elevated': hasTopbarShadow }">
        <div class="topbar-leading">
          <el-button
            v-if="isCompactViewport"
            class="menu-toggle"
            :icon="Fold"
            circle
            @click="toggleMobileSidebar"
          />
          <div>
            <p class="topbar-label">知识工坊</p>
            <strong>{{ route.meta.title || '管理台' }}</strong>
          </div>
        </div>
        <div class="topbar-actions">
          <div class="user-badge">
            <span class="user-badge-label">当前用户</span>
            <strong>{{ authStore.displayName }}</strong>
          </div>
          <el-button :icon="SwitchButton" text @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </header>

      <main ref="mainPanelRef" class="main-panel">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  position: relative;
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  gap: 20px;
  height: 100vh;
  padding: 20px;
  overflow: hidden;
}

.side-panel {
  position: relative;
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 0;
  padding: 28px 22px;
  overflow-y: auto;
}

.brand-block {
  display: flex;
  gap: 16px;
  align-items: center;
}

.brand-sign {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--ember-radius-lg);
  background: linear-gradient(160deg, var(--ember-primary) 0%, var(--ember-primary-hover) 100%);
  color: #fff;
  font-family: var(--ember-font-heading);
  font-size: 22px;
  font-weight: 700;
  box-shadow: var(--ember-shadow-glow);
}

.brand-caption {
  margin: 0 0 6px;
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.28em;
}

.brand-block h1 {
  margin: 0;
  font-family: var(--ember-font-heading);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.nav-menu {
  border-right: none;
}

:deep(.nav-menu .el-menu-item) {
  height: 44px;
  margin-bottom: 4px;
  border-radius: var(--ember-radius-md);
}

:deep(.nav-menu .el-menu-item.is-active) {
  background: var(--ember-primary-light);
}

.content-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.topbar {
  position: relative;
  z-index: 3;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 24px;
  transition:
    box-shadow 180ms ease,
    background-color 180ms ease;
}

.topbar.is-elevated {
  background: rgba(245, 245, 244, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: var(--ember-shadow-md);
}

.topbar-leading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-toggle {
  flex-shrink: 0;
  border: 1px solid var(--ember-border);
  background: var(--ember-surface);
}

.topbar-label {
  margin: 0 0 4px;
  color: var(--ember-neutral);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.topbar-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.user-badge {
  min-width: 140px;
  padding: 10px 14px;
  border-radius: var(--ember-radius-md);
  background: var(--ember-surface);
}

.user-badge-label {
  display: block;
  margin-bottom: 4px;
  color: var(--ember-neutral);
  font-size: 12px;
}

.main-panel {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
  padding-right: 6px;
}

.main-panel::-webkit-scrollbar,
.side-panel::-webkit-scrollbar {
  width: 8px;
}

.main-panel::-webkit-scrollbar-thumb,
.side-panel::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: var(--ember-radius-pill);
  background: var(--ember-surface-raised);
  background-clip: padding-box;
}

.main-panel::-webkit-scrollbar-track,
.side-panel::-webkit-scrollbar-track {
  background: transparent;
}

.mobile-sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 3;
  background: rgba(28, 25, 23, 0.18);
  backdrop-filter: blur(4px);
}

@media (max-width: 960px) {
  .admin-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .side-panel {
    position: fixed;
    top: 14px;
    left: 14px;
    bottom: 14px;
    width: min(320px, calc(100vw - 28px));
    gap: 18px;
    overflow-y: auto;
    transform: translateX(calc(-100% - 20px));
    transition: transform 220ms ease;
  }

  .side-panel.is-mobile-open {
    transform: translateX(0);
  }

  .content-shell,
  .main-panel {
    overflow: visible;
  }

  .topbar {
    position: sticky;
    top: 0;
  }
}

@media (max-width: 640px) {
  .admin-shell {
    padding: 14px;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar-leading {
    width: 100%;
  }

  .topbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .user-badge {
    min-width: 0;
  }
}
</style>
