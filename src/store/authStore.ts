import { create } from 'zustand';
import type { UserVO } from '../api/types';

interface AuthState {
  token: string | null;
  user: UserVO | null;
  setAuth: (token: string, user: UserVO) => void;
  setUser: (user: UserVO) => void;
  clear: () => void;
}

const storedToken = localStorage.getItem('token');
const storedUser = localStorage.getItem('user');
let initialUser: UserVO | null = null;
try {
  initialUser = storedUser ? (JSON.parse(storedUser) as UserVO) : null;
} catch {
  initialUser = null;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: storedToken,
  user: initialUser,
  setAuth: (token, user) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    set({ token, user });
  },
  setUser: (user) => set({ user }),
  clear: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ token: null, user: null });
  },
}));