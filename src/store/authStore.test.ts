import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from './authStore';
import type { UserVO } from '../api/types';

const user: UserVO = { id: '1', username: 'alice', role: 'USER', nickname: 'Alice' };
const state = () => useAuthStore.getState();

beforeEach(() => {
  localStorage.clear();
  state().clear();
});

describe('authStore', () => {
  it('初始（清理后）token 与 user 均为空', () => {
    expect(state().token).toBeNull();
    expect(state().user).toBeNull();
  });

  it('setAuth 同时写入状态与 localStorage', () => {
    state().setAuth('tk-123', user);

    expect(state().token).toBe('tk-123');
    expect(state().user).toEqual(user);
    expect(localStorage.getItem('token')).toBe('tk-123');
    expect(JSON.parse(localStorage.getItem('user')!)).toEqual(user);
  });

  it('setUser 仅更新用户信息，不影响 token', () => {
    state().setAuth('tk-123', user);
    state().setUser({ ...user, nickname: 'Alice2' });

    expect(state().user?.nickname).toBe('Alice2');
    expect(state().token).toBe('tk-123');
  });

  it('clear 清空状态与 localStorage', () => {
    state().setAuth('tk-123', user);
    state().clear();

    expect(state().token).toBeNull();
    expect(state().user).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });
});
