use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncValue {
    pub key: String,
    pub value: String,
    pub version: u64,
    pub timestamp: u64,
    pub source_id: String,
}

pub struct Engine {
    state: Arc<RwLock<HashMap<String, SyncValue>>>,
    local_id: String,
}

impl Engine {
    pub fn new(local_id: &str) -> Self {
        Self {
            state: Arc::new(RwLock::new(HashMap::new())),
            local_id: local_id.to_string(),
        }
    }

    pub fn set_property(&self, key: &str, value: &str) -> SyncValue {
        let mut store = self.state.write().unwrap();
        
        let current_version = store.get(key).map(|v| v.version).unwrap_or(0);
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis() as u64;

        let new_value = SyncValue {
            key: key.to_string(),
            value: value.to_string(),
            version: current_version + 1,
            timestamp,
            source_id: self.local_id.clone(),
        };

        store.insert(key.to_string(), new_value.clone());
        new_value
    }

    pub fn get_property(&self, key: &str) -> Option<SyncValue> {
        let store = self.state.read().unwrap();
        store.get(key).cloned()
    }

    pub fn apply_patch(&self, patch: SyncValue) -> bool {
        let mut store = self.state.write().unwrap();
        
        let should_update = match store.get(&patch.key) {
            Some(existing) => {
                if patch.version > existing.version {
                    true
                } else if patch.version == existing.version {
                    patch.timestamp > existing.timestamp
                } else {
                    false
                }
            }
            None => true,
        };

        if should_update {
            store.insert(patch.key.clone(), patch);
            true
        } else {
            false
        }
    }

    pub fn get_snapshot(&self) -> Vec<SyncValue> {
        let store = self.state.read().unwrap();
        store.values().cloned().collect()
    }

    pub fn resolve_conflict(&self, remote_values: Vec<SyncValue>) -> Vec<SyncValue> {
        let mut updates = Vec::new();
        for val in remote_values {
            if self.apply_patch(val.clone()) {
                updates.push(val);
            }
        }
        updates
    }

    pub fn clear(&self) {
        let mut store = self.state.write().unwrap();
        store.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_logic() {
        let engine = Engine::new("node-1");
        let patch = engine.set_property("ui.theme", "dark");
        
        assert_eq!(patch.value, "dark");
        assert_eq!(patch.version, 1);

        let remote_patch = SyncValue {
            key: "ui.theme".to_string(),
            value: "light".to_string(),
            version: 2,
            timestamp: patch.timestamp + 100,
            source_id: "node-2".to_string(),
        };

        assert!(engine.apply_patch(remote_patch));
        assert_eq!(engine.get_property("ui.theme").unwrap().value, "light");
    }

    #[test]
    fn test_stale_update_rejection() {
        let engine = Engine::new("node-1");
        engine.set_property("env.api_url", "https://api.v1.com");

        let stale_patch = SyncValue {
            key: "env.api_url".to_string(),
            value: "https://api.v0.com".to_string(),
            version: 0,
            timestamp: 0,
            source_id: "node-2".to_string(),
        };

        assert!(!engine.apply_patch(stale_patch));
        assert_eq!(engine.get_property("env.api_url").unwrap().version, 1);
    }
}