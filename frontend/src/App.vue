<template>
  <div class="app-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2a7 7 0 0 1 7 7c0 3-2 5.5-4.5 7L12 22l-2.5-6C7 14.5 5 12 5 9a7 7 0 0 1 7-7z"/>
            <circle cx="12" cy="9" r="2"/>
          </svg>
        </div>
        <span class="logo-text">AI 智能客服</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/chat" class="nav-item" :class="{ active: $route.path.startsWith('/chat') }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>智能对话</span>
        </router-link>
        <router-link to="/knowledge" class="nav-item" :class="{ active: $route.path.startsWith('/knowledge') }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
          </svg>
          <span>知识库</span>
        </router-link>
        <router-link to="/settings" class="nav-item" :class="{ active: $route.path.startsWith('/settings') }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
          </svg>
          <span>系统设置</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-badge">
          <div class="user-avatar">{{ store.currentUser?.charAt(0)?.toUpperCase() || 'U' }}</div>
          <div class="user-info">
            <span class="user-name">{{ store.currentUser }}</span>
            <span class="user-status">在线</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useAppStore } from './store'

const store = useAppStore()

onMounted(async () => {
  try {
    await store.loadUsers()
    await store.loadStatistics()
  } catch (e) {
    console.error('Init error:', e)
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  width: 100%;
  height: 100%;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100%;
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px 24px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-icon svg {
  width: 20px;
  height: 20px;
  color: #fff;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  color: var(--text-sidebar);
  text-decoration: none;
  transition: var(--transition);
  cursor: pointer;
  font-size: 14px;
}

.nav-item svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.nav-item:hover {
  background: var(--bg-sidebar-hover);
  color: var(--text-sidebar-active);
}

.nav-item.active {
  background: rgba(99, 102, 241, 0.15);
  color: var(--primary-light);
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
}

.user-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-sidebar-hover);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #fff;
}

.user-status {
  font-size: 11px;
  color: var(--success);
}

.main-content {
  flex: 1;
  height: 100%;
  overflow: hidden;
  background: var(--bg-body);
}
</style>
