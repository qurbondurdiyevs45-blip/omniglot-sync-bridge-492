<template>
  <div class="status-dashboard">
    <header class="dashboard-header">
      <h1>OmniGlot Sync-Bridge</h1>
      <div class="connection-pill" :class="{ connected: isConnected }">
        <span class="dot"></span>
        {{ isConnected ? 'Mesh Active' : 'Connecting...' }}
      </div>
    </header>

    <div class="stats-grid">
      <div class="stat-card">
        <label>Active Nodes</label>
        <span class="value">{{ nodes.length }}</span>
      </div>
      <div class="stat-card">
        <label>Sync Latency</label>
        <span class="value">{{ avgLatency }}ms</span>
      </div>
      <div class="stat-card">
        <label>Last Global Update</label>
        <span class="value">{{ lastSyncTime }}</span>
      </div>
    </div>

    <section class="node-list">
      <h2>Connected Peer Mesh</h2>
      <table>
        <thead>
          <tr>
            <th>Node ID</th>
            <th>Platform</th>
            <th>Runtime</th>
            <th>Status</th>
            <th>Version</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.id">
            <td class="node-id">{{ node.id.substring(0, 8) }}...</td>
            <td>
              <span class="platform-icon">{{ getPlatformIcon(node.platform) }}</span>
              {{ node.platform }}
            </td>
            <td><code>{{ node.runtime }}</code></td>
            <td>
              <span class="status-tag" :class="node.status.toLowerCase()">
                {{ node.status }}
              </span>
            </td>
            <td>v{{ node.version }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';

interface SyncNode {
  id: string;
  platform: 'Mobile' | 'Desktop' | 'Web' | 'Server';
  runtime: string;
  status: 'Online' | 'Syncing' | 'Idle' | 'Offline';
  version: string;
  latency: number;
}

const isConnected = ref(true);
const lastSyncTime = ref('--:--:--');
const nodes = ref<SyncNode[]>([
  { id: '1a2b3c4d5e6f', platform: 'Desktop', runtime: 'Rust/C++', status: 'Online', version: '1.0.4', latency: 12 },
  { id: '9z8y7x6w5v4u', platform: 'Mobile', runtime: 'Kotlin/Swift', status: 'Syncing', version: '1.0.4', latency: 45 },
  { id: 'm0n1p2q3r4s5', platform: 'Web', runtime: 'Vue/TS', status: 'Idle', version: '1.0.4', latency: 8 },
  { id: 'k9j8h7g6f5d4', platform: 'Server', runtime: 'Python/Go', status: 'Online', version: '1.0.3', latency: 2 }
]);

const avgLatency = computed(() => {
  if (nodes.value.length === 0) return 0;
  const sum = nodes.value.reduce((acc, node) => acc + node.latency, 0);
  return Math.round(sum / nodes.value.length);
});

const getPlatformIcon = (platform: string) => {
  switch (platform) {
    case 'Mobile': return '📱';
    case 'Desktop': return '💻';
    case 'Web': return '🌐';
    case 'Server': return '☁️';
    default: return '❓';
  }
};

let pollInterval: number;

onMounted(() => {
  pollInterval = window.setInterval(() => {
    const now = new Date();
    lastSyncTime.value = now.toLocaleTimeString();
    
    // Simulate real-time latency jitter
    nodes.value = nodes.value.map(node => ({
      ...node,
      latency: Math.max(2, node.latency + (Math.floor(Math.random() * 5) - 2))
    }));
  }, 3000);
});

onUnmounted(() => {
  clearInterval(pollInterval);
});
</script>

<style scoped>
.status-dashboard {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #2c3e50;
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  border-bottom: 2px solid #eee;
  padding-bottom: 1rem;
}

.connection-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  background: #fff1f0;
  color: #cf1322;
  font-weight: 600;
  font-size: 0.9rem;
}

.connection-pill.connected {
  background: #f6ffed;
  color: #389e0d;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1.5rem;
  margin-bottom: 3rem;
}

.stat-card {
  background: #f8f9fa;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.stat-card label {
  display: block;
  font-size: 0.85rem;
  color: #666;
  margin-bottom: 0.5rem;
}

.stat-card .value {
  font-size: 1.8rem;
  font-weight: 700;
  color: #007bff;
}

.node-list table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}

th {
  text-align: left;
  background: #f1f3f5;
  padding: 1rem;
  font-weight: 600;
}

td {
  padding: 1rem;
  border-bottom: 1px solid #eee;
}

.node-id {
  font-family: monospace;
  color: #666;
}

.status-tag {
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  font-size: 0.8rem;
  text-transform: uppercase;
  font-weight: bold;
}

.status-tag.online { background: #e6f7ff; color: #1890ff; }
.status-tag.syncing { background: #fff7e6; color: #faad14; }
.status-tag.idle { background: #f5f5f5; color: #8c8c8c; }

code {
  background: #f0f0f0;
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
}
</style>