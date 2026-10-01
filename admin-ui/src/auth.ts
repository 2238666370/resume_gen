import { create } from 'zustand';
import type { UserVO } from './types';

interface AuthState {
  token: string | null;
  user: UserVO | null;
  setAuth: (token: string, user: UserVO) => void;
  clear: () => void;
}

let initialUser: UserVO | null = null;
try {
  initialUser = JSON.parse(localStorage.getItem('user') || 'null') as UserVO | null;
} catch {
  initialUser = null;
}

export const useAuth = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  user: initialUser,
  setAuth: (token, user) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    set({ token, user });
  },
  clear: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ token: null, user: null });
  },
}));