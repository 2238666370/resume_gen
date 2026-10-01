import { client } from './client';

export interface WatermarkConfig {
  enabled: boolean;
  visibleText: string;
  visibleOpacity: number;
  visibleDensity: string;
  blindEnabled: boolean;
  blindPayload?: string;
}

/** 登录用户导出用水印配置（含盲水印负载）。 */
export function getWatermarkConfig(deviceId?: string): Promise<WatermarkConfig> {
  const params = deviceId ? { deviceId } : undefined;
  return client.get('/api/watermark/config', { params }) as Promise<WatermarkConfig>;
}

/** 匿名公开水印配置（分享页可视水印用）。 */
export function getPublicWatermarkConfig(deviceId?: string): Promise<WatermarkConfig> {
  const params = deviceId ? { deviceId } : undefined;
  return client.get('/api/public/watermark/config', { params }) as Promise<WatermarkConfig>;
}
