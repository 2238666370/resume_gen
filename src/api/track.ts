import { client } from './client';

const DEVICE_KEY = 'rg_device_id';

/** 稳定的匿名设备 ID（localStorage 持久化，用于 UV 去重）。 */
function deviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

/** 登录用户埋点（携带 token，按 userId 去重）。 */
export function track(page: string): void {
  client.post('/api/stats/track', { page, deviceId: deviceId() }).catch(() => {});
}

/** 匿名/分享页埋点（不带 token，按 deviceId 去重）。 */
export function trackPublic(page: string): void {
  client.post('/api/public/track', { page, deviceId: deviceId() }).catch(() => {});
}