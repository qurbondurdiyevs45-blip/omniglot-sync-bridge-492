import { useState, useEffect, useCallback, useRef } from 'react';

/**
 * Interface representing the structure of the shared configuration mesh.
 */
export interface SyncState {
  env: Record<string, string>;
  theme: {
    mode: 'light' | 'dark';
    primaryColor: string;
    accentColor: string;
    fontSize: number;
  };
  metadata: {
    lastUpdated: number;
    updatedBy: string;
    version: number;
  };
}

/**
 * Event detail for the custom bridge event emitted by the native/P2P layer.
 */
interface SyncUpdateEvent extends CustomEvent {
  detail: Partial<SyncState>;
}

/**
 * Custom hook for real-time state subscription to the OmniGlot Sync-Bridge mesh.
 * This hook maintains a local state that synchronizes with a global P2P mesh
 * via a native bridge or a WebSocket-based event emitter.
 */
export const useSync = () => {
  const [state, setState] = useState<SyncState>({
    env: {},
    theme: {
      mode: 'light',
      primaryColor: '#3b82f6',
      accentColor: '#10b981',
      fontSize: 16,
    },
    metadata: {
      lastUpdated: Date.now(),
      updatedBy: 'local-init',
      version: 0,
    },
  });

  const [isConnected, setIsConnected] = useState<boolean>(false);
  const eventSource = useRef<string>('OmniGlotBridge');

  /**
   * Dispatches an update to the mesh.
   * In a real-world scenario, this communicates with the Rust/C++ bridge core.
   */
  const updateMesh = useCallback((patch: Partial<SyncState>) => {
    const updatePayload = {
      ...patch,
      metadata: {
        lastUpdated: Date.now(),
        updatedBy: 'web-react-client',
        version: state.metadata.version + 1,
      },
    };

    // Dispatch to the native layer
    if (typeof window !== 'undefined' && (window as any).OmniGlotNative) {
      (window as any).OmniGlotNative.postMessage(JSON.stringify({
        type: 'SYNC_UPDATE',
        payload: updatePayload,
      }));
    }

    // Optimistic update locally
    setState((prev) => ({
      ...prev,
      ...updatePayload,
      theme: { ...prev.theme, ...updatePayload.theme },
      env: { ...prev.env, ...updatePayload.env },
    }));
  }, [state.metadata.version]);

  useEffect(() => {
    const handleSyncUpdate = (event: Event) => {
      const customEvent = event as SyncUpdateEvent;
      if (customEvent.detail) {
        setState((current) => ({
          ...current,
          ...customEvent.detail,
          // Deep merge protection for nested objects
          theme: { ...current.theme, ...customEvent.detail.theme },
          env: { ...current.env, ...customEvent.detail.env },
        }));
      }
    };

    const handleConnectionStatus = (event: Event) => {
      const statusEvent = event as CustomEvent<{ connected: boolean }>;
      setIsConnected(statusEvent.detail.connected);
    };

    // Attach listeners to the custom bridge event system
    window.addEventListener('omniglot_sync_update', handleSyncUpdate);
    window.addEventListener('omniglot_status_change', handleConnectionStatus);

    // Initial handshake with the mesh provider
    if ((window as any).OmniGlotNative) {
      (window as any).OmniGlotNative.postMessage(JSON.stringify({ type: 'SYNC_READY' }));
      setIsConnected(true);
    }

    return () => {
      window.removeEventListener('omniglot_sync_update', handleSyncUpdate);
      window.removeEventListener('omniglot_status_change', handleConnectionStatus);
    };
  }, []);

  return {
    state,
    isConnected,
    updateMesh,
    // Helper to specifically update environment variables
    setEnv: (key: string, value: string) => {
      updateMesh({ env: { ...state.env, [key]: value } });
    },
    // Helper to specifically update theme properties
    setTheme: (themePatch: Partial<SyncState['theme']>) => {
      updateMesh({ theme: { ...state.theme, ...themePatch } });
    }
  };
};

export default useSync;