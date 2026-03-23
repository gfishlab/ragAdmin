<script setup lang="ts">
import { computed } from 'vue'
import type { ModelBatchDeleteResult } from '@/types/model'

const props = defineProps<{
  result: ModelBatchDeleteResult | null
}>()

const visible = defineModel<boolean>({ required: true })

const hasFailures = computed(() => (props.result?.failedCount ?? 0) > 0)
const hasDeletedItems = computed(() => (props.result?.successCount ?? 0) > 0)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="批量删除结果"
    width="760px"
    destroy-on-close
  >
    <template v-if="result">
      <section class="result-hero" :class="{ 'is-warning': hasFailures }">
        <div class="result-main">
          <small>本次执行结果</small>
          <strong>
            {{ hasFailures ? '部分删除成功' : '批量删除已完成' }}
          </strong>
          <p>
            共请求删除 {{ result.requestedCount }} 个模型，成功 {{ result.successCount }} 个，失败 {{ result.failedCount }} 个。
          </p>
        </div>

        <div class="result-metrics">
          <article>
            <span>成功</span>
            <strong>{{ result.successCount }}</strong>
          </article>
          <article class="is-danger">
            <span>失败</span>
            <strong>{{ result.failedCount }}</strong>
          </article>
        </div>
      </section>

      <section v-if="hasDeletedItems" class="result-section">
        <div class="section-header">
          <strong>已删除模型</strong>
          <el-tag type="success" effect="plain">{{ result.deletedIds.length }} 项</el-tag>
        </div>
        <div class="chip-list">
          <span
            v-for="modelId in result.deletedIds"
            :key="`deleted-${modelId}`"
            class="chip chip-success"
          >
            #{{ modelId }}
          </span>
        </div>
      </section>

      <section v-if="hasFailures" class="result-section">
        <div class="section-header">
          <strong>失败明细</strong>
          <el-tag type="danger" effect="dark">{{ result.failedItems.length }} 项</el-tag>
        </div>

        <div class="failure-list">
          <article
            v-for="item in result.failedItems"
            :key="`failed-${item.modelId}`"
            class="failure-card"
          >
            <div class="failure-head">
              <div>
                <strong>{{ item.modelName }}</strong>
                <small>模型 ID：#{{ item.modelId }}</small>
              </div>
              <el-tag type="danger" effect="plain">删除失败</el-tag>
            </div>
            <p>{{ item.message }}</p>
          </article>
        </div>
      </section>

      <section v-else class="result-section">
        <div class="success-state">
          <strong>全部模型删除成功</strong>
          <p>当前这批模型已完成清理，没有需要人工处理的失败项。</p>
        </div>
      </section>
    </template>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="visible = false">我知道了</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.result-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(47, 143, 91, 0.95), rgba(104, 176, 126, 0.88));
  color: #f6fff8;
}

.result-hero.is-warning {
  background:
    linear-gradient(135deg, rgba(141, 69, 16, 0.96), rgba(198, 107, 34, 0.92));
}

.result-main small {
  display: block;
  color: rgba(246, 255, 248, 0.72);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.result-main strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.1;
}

.result-main p {
  margin: 10px 0 0;
  line-height: 1.7;
}

.result-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(110px, 1fr));
  gap: 12px;
  align-self: stretch;
}

.result-metrics article {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 112px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.14);
}

.result-metrics article.is-danger {
  background: rgba(255, 244, 240, 0.18);
}

.result-metrics span {
  color: rgba(255, 248, 240, 0.78);
  font-size: 13px;
}

.result-metrics strong {
  margin-top: 12px;
  font-size: 30px;
  line-height: 1;
}

.result-section {
  margin-top: 18px;
  padding: 18px 20px;
  border: 1px solid rgba(141, 69, 16, 0.1);
  border-radius: 20px;
  background: rgba(255, 251, 246, 0.8);
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.chip-success {
  background: rgba(47, 143, 91, 0.12);
  color: #2f8f5b;
}

.failure-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.failure-card {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 246, 243, 0.9);
  border: 1px solid rgba(181, 77, 61, 0.12);
}

.failure-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.failure-head strong {
  display: block;
  font-size: 16px;
}

.failure-head small {
  display: block;
  margin-top: 6px;
  color: #8b7260;
}

.failure-card p {
  margin: 12px 0 0;
  color: #71463a;
  line-height: 1.7;
}

.success-state {
  text-align: center;
}

.success-state strong {
  display: block;
  font-size: 18px;
}

.success-state p {
  margin: 10px 0 0;
  color: #6c5a4c;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .result-hero,
  .failure-head {
    flex-direction: column;
  }

  .result-metrics {
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }
}
</style>
