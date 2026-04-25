import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { hasAnyPermission } from '@/utils/permission'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: {
        public: true,
        title: '登录',
      },
    },
    {
      path: '/',
      component: AdminLayout,
      meta: {
        title: '管理台',
      },
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: {
            title: '概览',
            requiredPermissions: ['DASHBOARD_VIEW'],
          },
        },
        {
          path: 'chat',
          redirect: '/dashboard',
        },
        {
          path: 'knowledge-bases',
          name: 'knowledge-bases',
          component: () => import('@/views/knowledge-base/KnowledgeBaseListView.vue'),
          meta: {
            title: '知识库管理',
            requiredPermissions: ['KB_MANAGE'],
          },
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/model/ModelManagementView.vue'),
          meta: {
            title: '模型管理',
            requiredPermissions: ['MODEL_MANAGE'],
          },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/user/UserManagementView.vue'),
          meta: {
            title: '用户管理',
            requiredPermissions: ['USER_MANAGE'],
          },
        },
        {
          path: 'knowledge-bases/create',
          name: 'knowledge-base-create',
          component: () => import('@/views/knowledge-base/KnowledgeBaseCreateView.vue'),
          meta: {
            title: '新建知识库',
            requiredPermissions: ['KB_MANAGE'],
          },
        },
        {
          path: 'knowledge-bases/:id/edit',
          name: 'knowledge-base-edit',
          component: () => import('@/views/knowledge-base/KnowledgeBaseEditView.vue'),
          meta: {
            title: '编辑知识库',
            requiredPermissions: ['KB_MANAGE'],
          },
        },
        {
          path: 'knowledge-bases/:id',
          name: 'knowledge-base-detail',
          component: () => import('@/views/knowledge-base/KnowledgeBaseDetailView.vue'),
          meta: {
            title: '知识库详情',
            requiredPermissions: ['KB_MANAGE'],
          },
        },
        {
          path: 'documents/:id',
          name: 'document-detail',
          component: () => import('@/views/document/DocumentDetailView.vue'),
          meta: {
            title: '文档详情',
            requiredPermissions: ['KB_MANAGE'],
          },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/task/TaskMonitorView.vue'),
          meta: {
            title: '任务监控',
            requiredPermissions: ['TASK_VIEW'],
          },
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('@/views/audit/AuditLogView.vue'),
          meta: {
            title: '审计日志',
            requiredPermissions: ['AUDIT_VIEW'],
          },
        },
        {
          path: 'vector-indexes',
          name: 'vector-indexes',
          component: () => import('@/views/statistics/VectorIndexOverviewView.vue'),
          meta: {
            title: '向量索引',
            requiredPermissions: ['STATISTICS_VIEW'],
          },
        },
        {
          path: 'feedback',
          name: 'feedback',
          component: () => import('@/views/feedback/FeedbackView.vue'),
          meta: {
            title: '反馈管理',
            requiredPermissions: ['CHAT_FEEDBACK_VIEW'],
          },
        },
        {
          path: 'tasks/:id',
          name: 'task-detail',
          component: () => import('@/views/task/TaskDetailView.vue'),
          meta: {
            title: '任务详情',
            requiredPermissions: ['TASK_VIEW'],
          },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const isPublic = Boolean(to.meta.public)

  if (!isPublic && !authStore.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return '/dashboard'
  }

  if (!isPublic && !authStore.bootstrapFinished) {
    await authStore.hydrateCurrentUser()
    if (!authStore.isAuthenticated) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  const requiredPermissions = Array.isArray(to.meta.requiredPermissions)
    ? (to.meta.requiredPermissions as string[])
    : []
  if (!isPublic && requiredPermissions.length > 0 && !hasAnyPermission(authStore.currentUser, requiredPermissions)) {
    return '/dashboard'
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '管理台'
  document.title = `${title} | ragAdmin`
})

export default router
