import { client } from './client';
import type { CaptchaVO, LoginResponse, UserVO } from './types';

export function getCaptcha(): Promise<CaptchaVO> {
  return client.get('/api/auth/captcha') as Promise<CaptchaVO>;
}

export function register(username: string, password: string, captchaId: string, captchaCode: string, email?: string): Promise<LoginResponse> {
  return client.post('/api/auth/register', { username, password, email, captchaId, captchaCode }) as Promise<LoginResponse>;
}

export function login(username: string, password: string, captchaId: string, captchaCode: string): Promise<LoginResponse> {
  return client.post('/api/auth/login', { username, password, captchaId, captchaCode }) as Promise<LoginResponse>;
}

export function logout(): Promise<void> {
  return client.post('/api/auth/logout') as Promise<void>;
}

export function me(): Promise<UserVO> {
  return client.get('/api/auth/me') as Promise<UserVO>;
}