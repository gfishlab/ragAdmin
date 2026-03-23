<template>
  <div class="chat-layout-shell">
    <main class="chat-main">
      <div class="chat-route">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.chat-layout-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 100vh;
  padding: 20px;
  overflow: hidden;
}

.chat-main {
  min-width: 0;
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

.chat-route {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
