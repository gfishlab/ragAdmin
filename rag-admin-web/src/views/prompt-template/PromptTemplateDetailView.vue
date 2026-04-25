<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getPromptTemplate, updatePromptTemplate } from '@/api/prompt-template'
import { resolveErrorMessage } from '@/api/http'

const route = useRoute()
const router = useRouter()

const templateId = computed(() => Number(route.params.id))
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const template = ref<{
  id: number
  templateCode: string
  templateName: string
  capabilityType: string | null
  promptContent: string
  versionNo: number
  status: string
  description: string | null
  createdAt: string
  updatedAt: string
} | null>(null)

const editForm = ref({
  templateName: '',
  promptContent: '',
  description: '',
  status: 'ENABLED',
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getPromptTemplate(templateId.value)
    template.value = res
    editForm.value = {
      templateName: res.templateName,
      promptContent: res.promptContent,
      description: res.description ?? '',
      status: res.status,
    }
  } catch (e) {
    loadError.value = resolveErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!editForm.value.templateName.trim()) {
    ElMessage.warning('模板名称不能为空')
    return
  }
  if (!editForm.value.promptContent.trim()) {
    ElMessage.warning('模板内容不能为空')
    return
  }

  saving.value = true
  try {
    const updated = await updatePromptTemplate(templateId.value, {
      templateName: editForm.value.templateName,
      promptContent: editForm.value.promptContent,
      description: editForm.value.description || undefined,
      status: editForm.value.status,
    })
    ElMessage.success('保存成功，新版本 ' + updated.versionNo)
    await loadDetail()
  } catch (e) {
    ElMessage.error(resolveErrorMessage(e))
  } finally {
    saving.value = false
  }
}

function handleBack() {
  router.push('/prompt-templates')
}

function capabilityLabel(type: string | null): string {
  if (type === 'CHAT') return '对话'
  if (type === 'RETRIEVAL') return '检索'
  return type ?? '-'
}

function formatTime(value: string): string {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadDetail)
</script>

<template>
  <section class="pt-detail-page">
    <div v-if="loading" class="soft-panel loading-panel">
      <el-skeleton :rows="6" animated />
    </div>

    <el-empty v-else-if="loadError" :description="loadError">
      <el-button type="primary" @click="loadDetail">重试</el-button>
    </el-empty>

    <template v-else-if="template">
      <header class="detail-head soft-panel">
        <div class="head-leading">
          <el-button :icon="ArrowLeft" @click="handleBack" circle />
          <div>
            <p class="head-label">{{ template.templateCode }}</p>
            <h2>{{ template.templateName }}</h2>
          </div>
        </div>
        <div class="head-meta">
          <el-tag size="small">{{ capabilityLabel(template.capabilityType) }}</el-tag>
          <span class="meta-text">版本 <strong>{{ template.versionNo }}</strong></span>
          <span class="meta-text">更新于 {{ formatTime(template.updatedAt) }}</span>
        </div>
      </header>

      <section class="edit-panel soft-panel">
        <div class="edit-header">
          <h3>编辑模板</h3>
          <el-select v-model="editForm.status" style="width: 130px">
            <el-option label="已启用" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </div>

        <div class="form-group">
          <label class="form-label">模板名称</label>
          <el-input v-model="editForm.templateName" placeholder="模板名称" />
        </div>

        <div class="form-group">
          <label class="form-label">描述</label>
          <el-input v-model="editForm.description" placeholder="模板用途说明（可选）" />
        </div>

        <div class="form-group form-group-grow">
          <label class="form-label">模板内容</label>
          <el-input
            v-model="editForm.promptContent"
            type="textarea"
            :autosize="{ minRows: 16, maxRows: 40 }"
            placeholder="Prompt 模板内容，支持 ${variableName} 变量占位符"
          />
        </div>

        <div class="form-actions">
          <el-button @click="handleBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">
            保存（创建新版本）
          </el-button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.pt-detail-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.loading-panel {
  padding: 32px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
}

.head-leading {
  display: flex;
  align-items: center;
  gap: 14px;
}

.head-label {
  margin: 0 0 4px;
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.12em;
}

.head-leading h2 {
  margin: 0;
  font-family: var(--ember-font-heading);
  font-size: 20px;
  font-weight: 700;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-text {
  color: var(--ember-text-secondary);
  font-size: 13px;
}

.edit-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.edit-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group-grow {
  flex: 1;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ember-text-secondary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--ember-border);
}

@media (max-width: 640px) {
  .detail-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .head-meta {
    flex-wrap: wrap;
  }
}
</style>
