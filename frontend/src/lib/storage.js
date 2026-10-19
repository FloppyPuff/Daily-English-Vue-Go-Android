import { STORAGE_KEY } from './core.js';
export function persistProgress(storage, state, recoveryRaw = null) {
  if (recoveryRaw !== null) storage.setItem(`${STORAGE_KEY}.recovery`, recoveryRaw);
  storage.setItem(STORAGE_KEY, JSON.stringify(state));
}
