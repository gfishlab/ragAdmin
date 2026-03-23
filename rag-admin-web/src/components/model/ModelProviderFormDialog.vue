<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ModelProviderCreateRequest } from '@/types/model'
import {
  createEmptyProviderForm,
  providerCreateStatusOptions,
} from '@/utils/model-management'

const props = defineProps<{
  submitting: boolean
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  submit: [payload: ModelProviderCreateRequest]
}>()

const providerForm = reactive<ModelProviderCreateRequest>(createEmptyProviderForm())

const dialogTitle = computed(() => '新增模型提供方')

function resetForm(): void {
  Object.assign(providerForm, createEmptyProviderForm())
}

function handleSubmit(): void {
  emit('submit', {
    ...providerForm,
    providerCode: providerForm.providerCode.trim(),
    providerName: providerForm.providerName.trim(),
    baseUrl: providerForm.baseUrl?.trim() || null,
    apiKeySecretRef: providerForm.apiKeySecretRef?.trim() || null,
  })
}

watch(
  () => visible.value,
  (isVisible) => {
    if (isVisible) {
      resetForm()
    }
  },
)

watch(
  () => props.submitting,
  (submitting, previous) => {
    if (previous && !submitting && !visible.value) {
      resetForm()
    }
  },
)
</script>

<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="560px" @closed="resetForm">
    <el-form label-position="top">
      <el-form-item label="提供方编码">
        <el-input v-model="providerForm.providerCode" placeholder="例如 BAILIAN / OLLAMA / OPENAI" />
      </el-form-item>
      <el-form-item label="提供方名称">
        <el-input v-model="providerForm.providerName" placeholder="请输入提供方名称" />
      </el-form-item>
      <el-form-item label="接入地址">
        <el-input v-model="providerForm.baseUrl" placeholder="可为空，例如 https://dashscope.aliyuncs.com" />
      </el-form-item>
      <el-form-item label="密钥引用">
        <el-input v-model="providerForm.apiKeySecretRef" placeholder="可为空，例如 secret/bailian/api-key" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="providerForm.status">
          <el-radio
            v-for="item in providerCreateStatusOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确认创建
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
