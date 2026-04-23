<script setup lang="ts">
import { computed } from 'vue'
import type { ChatContentType } from '@/types/chat'
import { renderChatContent } from '@/utils/chat-markdown'

interface Props {
  content: string
  contentType?: ChatContentType
}

const props = defineProps<Props>()

const renderedHtml = computed(() => renderChatContent(props.content, props.contentType ?? 'text/markdown'))
</script>

<template>
  <div class="chat-markdown" v-html="renderedHtml" />
</template>

<style scoped>
.chat-markdown {
  color: inherit;
  line-height: 1.75;
  word-break: break-word;
}

.chat-markdown :deep(> :first-child) {
  margin-top: 0;
}

.chat-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.chat-markdown :deep(p),
.chat-markdown :deep(ul),
.chat-markdown :deep(ol),
.chat-markdown :deep(pre),
.chat-markdown :deep(blockquote) {
  margin: 0 0 12px;
}

.chat-markdown :deep(h1),
.chat-markdown :deep(h2),
.chat-markdown :deep(h3) {
  margin: 0 0 10px;
  color: var(--genesis-text-primary);
  font-family: "General Sans", sans-serif;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.3;
}

.chat-markdown :deep(h1) {
  font-size: 22px;
}

.chat-markdown :deep(h2) {
  font-size: 18px;
}

.chat-markdown :deep(h3) {
  font-size: 16px;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
  padding-left: 20px;
}

.chat-markdown :deep(li + li) {
  margin-top: 4px;
}

.chat-markdown :deep(blockquote) {
  padding: 10px 14px;
  border-left: 3px solid var(--genesis-primary-medium);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: rgba(99, 102, 241, 0.04);
  color: var(--genesis-text-secondary);
}

.chat-markdown :deep(code) {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: rgba(99, 102, 241, 0.06);
  color: var(--genesis-primary-hover);
  font-family: "JetBrains Mono", monospace;
  font-size: 0.9em;
}

.chat-markdown :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  background: var(--genesis-text-primary);
  color: #f0f0f0;
}

.chat-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.chat-markdown :deep(a) {
  color: var(--genesis-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
