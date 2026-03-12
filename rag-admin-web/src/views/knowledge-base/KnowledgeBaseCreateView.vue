<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import KnowledgeBaseForm from '@/components/knowledge-base/KnowledgeBaseForm.vue'
import { createKnowledgeBase, listModels } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { KnowledgeBaseUpsertRequest, ModelDefinition } from '@/types/knowledge-base'

const router = useRouter()
const submitting = ref(false)
const modelLoading = ref(false)
const modelFallback = ref(false)
const chatModelOptions = ref<ModelDefinition[]>([])
const embeddingModelOptions = ref<ModelDefinition[]>([])

const form = reactive<KnowledgeBaseUpsertRequest>({
  kbCode: '',
  kbName: '',
  description: null,
  embeddingModelId: null,
  chatModelId: null,
  retrieveTopK: 5,
  rerankEnabled: true,
  status: 'ENABLED',
})

function capabilitySet(model: ModelDefinition): Set<string> {
  return new Set(
    [...(model.capabilityTypes ?? []), model.capabilityType]
      .filter((item): item is string => Boolean(item))
      .map((item) => item.toUpperCase()),
  )
}

function isChatModel(model: ModelDefinition): boolean {
  const capabilities = capabilitySet(model)
  return model.modelType === 'CHAT' || capabilities.has('TEXT_GENERATION')
}

function isEmbeddingModel(model: ModelDefinition): boolean {
  const capabilities = capabilitySet(model)
  return model.modelType === 'EMBEDDING'
    || capabilities.has('TEXT_EMBEDDING')
    || capabilities.has('EMBEDDING')
  }

function normalizeForm(): KnowledgeBaseUpsertRequest {
  return {
    kbCode: form.kbCode.trim(),
    kbName: form.kbName.trim(),
    description: form.description?.trim() ? form.description.trim() : null,
    embeddingModelId: form.embeddingModelId ?? null,
    chatModelId: form.chatModelId ?? null,
    retrieveTopK: Number(form.retrieveTopK),
    rerankEnabled: form.rerankEnabled,
    status: form.status,
  }
}

async function loadModelOptions(): Promise<void> {
  modelLoading.value = true
  modelFallback.value = false
  try {
    const response = await listModels({
      pageNo: 1,
      pageSize: 100,
      status: 'ENABLED',
    })
    const enabledModels = response.list.filter((item) => item.status === 'ENABLED' || !item.status)
    chatModelOptions.value = enabledModels.filter(isChatModel)
    embeddingModelOptions.value = enabledModels.filter(isEmbeddingModel)
  } catch {
    modelFallback.value = true
    chatModelOptions.value = []
    embeddingModelOptions.value = []
  } finally {
    modelLoading.value = false
  }
}

async function handleSubmit(): Promise<void> {
  submitting.value = true
  try {
    await createKnowledgeBase(normalizeForm())
    await router.replace({
      path: '/knowledge-bases',
      query: {
        created: '1',
      },
    })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function handleCancel(): Promise<void> {
  await router.push('/knowledge-bases')
}

onMounted(async () => {
  await loadModelOptions()
})
</script>

<template>
  <section class="create-page">
    <KnowledgeBaseForm
      v-model="form"
      :chat-model-options="chatModelOptions"
      :embedding-model-options="embeddingModelOptions"
      :model-fallback="modelFallback"
      :model-loading="modelLoading"
      :submitting="submitting"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </section>
</template>

<style scoped>
.create-page {
  display: flex;
  flex-direction: column;
}
</style>
