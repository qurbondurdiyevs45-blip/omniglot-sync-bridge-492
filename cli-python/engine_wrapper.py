import os
import json
import argparse
import sys
import subprocess
import logging
from typing import Dict, List, Optional
from dataclasses import dataclass

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

@dataclass
class SyncPayload:
    namespace: str
    key_values: Dict[str, str]
    ttl_seconds: int = 3600
    priority: int = 1

class OmniGlotEngineWrapper:
    """
    Python wrapper for the OmniGlot Sync-Bridge native core engine.
    This tool facilitates batch importing environment variables into the 
    P2P mesh network via the rust-based CLI binary.
    """

    def __init__(self, binary_path: Optional[str] = None):
        self.binary_path = binary_path or os.getenv("OMNIGLOT_CORE_BIN", "omniglot-core")

    def _execute_sync(self, payload: SyncPayload) -> bool:
        """Invokes the native core to broadcast changes to the mesh."""
        try:
            input_data = json.dumps({
                "action": "BATCH_UPDATE",
                "namespace": payload.namespace,
                "data": payload.key_values,
                "options": {
                    "ttl": payload.ttl_seconds,
                    "priority": payload.priority
                }
            })

            process = subprocess.Popen(
                [self.binary_path, "--pipe"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            stdout, stderr = process.communicate(input=input_data)

            if process.returncode == 0:
                logging.info(f"Successfully synchronized {len(payload.key_values)} keys to namespace '{payload.namespace}'")
                return True
            else:
                logging.error(f"Engine Error: {stderr.strip()}")
                return False

        except FileNotFoundError:
            logging.error(f"Core binary not found at: {self.binary_path}")
            return False
        except Exception as e:
            logging.error(f"Unexpected error during sync: {str(e)}")
            return False

    def import_from_env_file(self, filepath: str, namespace: str) -> bool:
        """Parses a .env file and pushes contents to the mesh."""
        if not os.path.exists(filepath):
            logging.error(f"File not found: {filepath}")
            return False

        kv_pairs = {}
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                if '=' in line:
                    key, value = line.split('=', 1)
                    kv_pairs[key.strip()] = value.strip().strip('"').strip("'")

        if not kv_pairs:
            logging.warning("No valid environment variables found in file.")
            return False

        payload = SyncPayload(namespace=namespace, key_values=kv_pairs)
        return self._execute_sync(payload)

    def import_active_env(self, prefix: str, namespace: str) -> bool:
        """Filters current process environment variables by prefix and syncs them."""
        kv_pairs = {k: v for k, v in os.environ.items() if k.startswith(prefix)}
        
        if not kv_pairs:
            logging.warning(f"No environment variables found with prefix: {prefix}")
            return False

        payload = SyncPayload(namespace=namespace, key_values=kv_pairs)
        return self._execute_sync(payload)

def main():
    parser = argparse.ArgumentParser(description="OmniGlot Sync-Bridge Python CLI Engine Wrapper")
    parser.add_argument("--file", help="Path to .env file to import")
    parser.add_argument("--prefix", help="Prefix for existing system environment variables")
    parser.add_argument("--namespace", required=True, help="Target synchronization namespace")
    parser.add_argument("--bin", help="Path to the omniglot-core binary")

    args = parser.parse_args()
    engine = OmniGlotEngineWrapper(binary_path=args.bin)

    success = False
    if args.file:
        success = engine.import_from_env_file(args.file, args.namespace)
    elif args.prefix:
        success = engine.import_active_env(args.prefix, args.namespace)
    else:
        logging.error("You must Provide either --file or --prefix argument.")
        sys.exit(1)

    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()