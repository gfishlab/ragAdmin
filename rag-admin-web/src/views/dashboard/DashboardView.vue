<template>
  <section class="dashboard-page">
    <header class="dashboard-head">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">
          当前概览页用于承接知识库、文档、任务与健康检查等核心管理入口。
        </p>
      </div>
    </header>

    <div class="dashboard-grid">
      <article class="metric-card soft-panel">
        <span>当前完成</span>
        <strong>基础管理台</strong>
        <p>已具备登录、知识库、文档、任务等核心页面与联调主链路。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>当前联调焦点</span>
        <strong>文档与任务链路</strong>
        <p>当前重点是继续收紧上传、解析、任务监控和文档详情体验。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>当前基线</span>
        <strong>统一导航与鉴权</strong>
        <p>页面已共享同一套路由守卫、请求封装与错误处理方式。</p>
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
