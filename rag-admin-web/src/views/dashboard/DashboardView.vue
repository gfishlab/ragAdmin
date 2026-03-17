<template>
  <section class="dashboard-page">
    <header class="dashboard-head">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">
          汇总展示知识库、模型、文档与任务等核心管理入口。
        </p>
      </div>
    </header>

    <div class="dashboard-grid">
      <article class="metric-card soft-panel">
        <span>知识库管理</span>
        <strong>统一入口</strong>
        <p>支持从列表进入创建、编辑和详情页面，集中管理知识库配置。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>文档与任务</span>
        <strong>链路可追踪</strong>
        <p>文档上传、解析、切片和任务监控都可在管理端直接查看。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>模型与配置</span>
        <strong>集中维护</strong>
        <p>模型配置、探活结果和提供方接入信息已收敛到统一入口。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.metric-card {
  min-height: 220px;
  padding: 24px;
}

.metric-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.metric-card strong {
  display: block;
  margin-top: 14px;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
  line-height: 1.2;
}

.metric-card p {
  margin: 16px 0 0;
  color: #6d5948;
}

@media (max-width: 960px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
