<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { KnowledgeBaseUpsertRequest, ModelDefinition } from '@/types/knowledge-base'

const formModel = defineModel<KnowledgeBaseUpsertRequest>({ required: true })

const props = defineProps<{
  embeddingModelOptions: ModelDefinition[]
  modelFallback: boolean
  modelLoading: boolean
  submitting: boolean
  title: string
  description: string
  submitText: string
  eyebrow?: string
}>()

const emit = defineEmits<{
  submit: []
  cancel: []
}>()

const formRef = ref<FormInstance>()

const hasEmbeddingModelOptions = computed(() => props.embeddingModelOptions.length > 0)

const rules: FormRules<KnowledgeBaseUpsertRequest> = {
  kbCode: [
    { required: true, message: '请输入知识库编码', trigger: 'blur' },
    {
      pattern: /^[a-z0-9_-]+$/,
      message: '知识库编码仅支持小写字母、数字、中划线和下划线',
      trigger: 'blur',
    },
  ],
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  embeddingModelId: [{ required: true, message: '请选择向量模型', trigger: 'change' }],
  retrieveTopK: [
    { required: true, message: '请输入检索数量', trigger: 'change' },
    {
      validator: (_rule, value: number, callback) => {
        if (!Number.isInteger(value) || value < 1 || value > 20) {
          callback(new Error('检索数量需为 1 到 20 的整数'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  emit('submit')
}
</script>

<template>
  <section class="form-panel soft-panel">
    <div class="form-head">
      <div>
        <p class="form-eyebrow">{{ eyebrow || '知识库配置' }}</p>
        <h2>{{ title }}</h2>
        <p class="form-description">{{ description }}</p>
      </div>
    </div>

    <el-alert
      v-if="modelFallback"
      type="warning"
      :closable="false"
      show-icon
      :title="hasEmbeddingModelOptions
        ? '模型列表加载失败，当前仅保留已绑定的向量模型；如需切换其他向量模型，请先恢复模型列表。'
        : '模型列表加载失败，知识库必须绑定一个向量模型，请先恢复模型列表后再提交。'"
    />

    <el-form
      ref="formRef"
      :model="formModel"
      :rules="rules"
      label-position="top"
      class="kb-form"
    >
      <div class="form-grid two-columns">
        <el-form-item label="知识库编码" prop="kbCode">
          <el-input
            v-model="formModel.kbCode"
            maxlength="64"
            placeholder="例如 company_policy"
          />
        </el-form-item>
        <el-form-item label="知识库名称" prop="kbName">
          <el-input
            v-model="formModel.kbName"
            maxlength="128"
            placeholder="例如 公司制度库"
          />
        </el-form-item>
      </div>

      <el-form-item label="知识库说明" prop="description">
        <el-input
          v-model="formModel.description"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="请输入知识库用途、覆盖文档范围或适用业务场景"
        />
      </el-form-item>

      <div class="form-grid two-columns">
        <el-form-item label="向量模型" prop="embeddingModelId">
          <el-select
            v-model="formModel.embeddingModelId"
            filterable
            :loading="modelLoading"
            placeholder="请选择知识库使用的向量模型"
          >
            <el-option
              v-for="item in embeddingModelOptions"
              :key="item.id"
              :label="item.modelName"
              :value="item.id"
            >
              <div class="option-row">
                <span>{{ item.modelName }}</span>
                <small>{{ item.modelCode }}</small>
              </div>
            </el-option>
          </el-select>
          <p class="field-tip">
            {{ hasEmbeddingModelOptions ? '知识库创建后将使用该向量模型执行切片向量化与检索。' : '当前无可用向量模型，请先到模型管理中维护至少一个启用中的向量模型。' }}
          </p>
        </el-form-item>

        <el-form-item label="回答模型策略">
          <el-input
            model-value="平台默认聊天模型"
            readonly
            placeholder="平台默认聊天模型"
          />
          <p class="field-tip">
            知识库不再单独配置聊天模型；问答生成统一使用模型管理中设置的默认聊天模型，前台会话或请求仍可显式覆盖。
          </p>
        </el-form-item>
      </div>

      <div class="form-grid three-columns">
        <el-form-item label="检索数量" prop="retrieveTopK">
          <el-input-number v-model="formModel.retrieveTopK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="重排开关">
          <el-switch
            v-model="formModel.rerankEnabled"
            inline-prompt
            active-text="开"
            inactive-text="关"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formModel.status" placeholder="请选择状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>

      <div class="form-actions">
        <el-button @click="emit('cancel')">取消返回</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ submitText }}
        </el-button>
      </div>
    </el-form>
  </section>
</template>

<style scoped>
.form-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 28px;
}

.form-eyebrow {
  margin: 0 0 8px;
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.form-head h2 {
  margin: 0;
  font-family: var(--ember-font-heading);
  font-size: 32px;
}

.form-description {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--ember-text-secondary);
}

.kb-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-grid {
  display: grid;
  gap: 18px;
}

.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-tip {
  margin: 8px 0 0;
  color: var(--ember-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.option-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.option-row small {
  color: var(--ember-text-muted);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 6px;
}

@media (max-width: 960px) {
  .two-columns,
  .three-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .form-panel {
    padding: 20px;
  }

  .form-actions {
    flex-direction: column-reverse;
  }
}
</style>
