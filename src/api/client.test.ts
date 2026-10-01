import { describe, it, expect } from 'vitest';
import { getErrorMessage } from './client';

/** 构造带 code 的错误，模拟拦截器抛出的 ApiError。 */
function apiError(code: number | undefined, message = 'boom'): Error {
  const e = new Error(message) as Error & { code?: number };
  if (code !== undefined) e.code = code;
  return e;
}

describe('getErrorMessage', () => {
  it('400 返回固定文案', () => {
    expect(getErrorMessage(apiError(400))).toBe('请求参数有误，请检查后重试');
  });

  it('401 返回登录失效文案', () => {
    expect(getErrorMessage(apiError(401))).toBe('登录已失效，请重新登录');
  });

  it('403 返回无权限文案', () => {
    expect(getErrorMessage(apiError(403))).toBe('没有权限执行此操作');
  });

  it('404 返回资源不存在文案', () => {
    expect(getErrorMessage(apiError(404))).toBe('请求的资源不存在');
  });

  it('409 返回冲突文案', () => {
    expect(getErrorMessage(apiError(409))).toBe('数据冲突，请刷新后重试');
  });

  it('429 透传后端已本地化的 message', () => {
    expect(getErrorMessage(apiError(429, '操作过于频繁，请稍后再试'))).toBe('操作过于频繁，请稍后再试');
  });

  it('500 透传后端 message', () => {
    expect(getErrorMessage(apiError(500, 'AI 服务繁忙'))).toBe('AI 服务繁忙');
  });

  it('无 code 时回退到错误自身 message', () => {
    expect(getErrorMessage(apiError(undefined, '网络错误'))).toBe('网络错误');
  });

  it('非 Error 且无 message 时使用兜底文案', () => {
    expect(getErrorMessage({}, '请求失败')).toBe('请求失败');
  });
});
