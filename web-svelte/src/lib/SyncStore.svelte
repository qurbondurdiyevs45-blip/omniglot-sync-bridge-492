<script context="module">
  import { writable } from 'svelte/store';

  /**
   * SyncStore manages reactive state for the OmniGlot Sync-Bridge.
   * It bridges the gap between the P2P mesh network (via WebSocket or Native Bridge) 
   * and the Svelte UI components.
   */
  function createSyncStore() {
    const { subscribe, set, update } = writable({
      connected: false,
      peerCount: 0,
      config: {
        environment: {},
        theme: {
          mode: 'dark',
          primaryColor: '#6366f1',
          fontSize: 14
        }
      },
      lastSync: null
    });

    // Internal reference to the event source
    let socket;

    return {
      subscribe,
      
      /**
       * Connects to the local bridge client (Rust-based core)
       */
      connect: (url = 'ws://localhost:8080/ws') => {
        socket = new WebSocket(url);

        socket.onopen = () => {
          update(s => ({ ...s, connected: true }));
          console.log('[OmniGlot] Connected to Sync-Bridge Mesh');
        };

        socket.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            
            if (data.type === 'SYNC_UPDATE') {
              update(s => ({
                ...s,
                config: { ...s.config, ...data.payload },
                lastSync: new Date().toISOString()
              }));
            } else if (data.type === 'PEER_CHANGE') {
              update(s => ({ ...s, peerCount: data.count }));
            }
          } catch (err) {
            console.error('[OmniGlot] Failed to parse mesh message', err);
          }
        };

        socket.onclose = () => {
          update(s => ({ ...s, connected: false }));
          // Attempt reconnection after 3 seconds
          setTimeout(() => createSyncStore().connect(url), 3000);
        };
      },

      /**
       * Broadcasts a configuration change to all peers in the mesh
       */
      updateConfig: (category, key, value) => {
        update(s => {
          const newConfig = { ...s.config };
          if (!newConfig[category]) newConfig[category] = {};
          newConfig[category][key] = value;

          if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({
              type: 'BROADCAST_UPDATE',
              payload: { [category]: newConfig[category] }
            }));
          }

          return { ...s, config: newConfig };
        });
      },

      /**
       * Specifically for environment variables
       */
      setEnv: (key, value) => {
        update(s => {
          const newEnv = { ...s.config.environment, [key]: value };
          
          if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({
              type: 'BROADCAST_UPDATE',
              payload: { environment: newEnv }
            }));
          }
          
          return {
            ...s,
            config: { ...s.config, environment: newEnv }
          };
        });
      }
    };
  }

  export const syncStore = createSyncStore();
</script>