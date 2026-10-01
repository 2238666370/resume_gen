import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';

const NAV = [
  { to: '/', label: '工作台' },
  { to: '/ai', label: 'AI 助手' },
  { to: '/discover', label: '发现' },
  { to: '/me', label: '我的' },
];

/**
 * 统一应用外壳（F1）：一级导航 + 账户区 + 内容区，登录态页面共用。
 * 编辑器（/editor/:id）与分享页（/s/:key）不套此壳，保持全屏专注模式。
 */
export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const clearAuth = useAuthStore((s) => s.clear);
  const [menuOpen, setMenuOpen] = useState(false);

  const isActive = (to: string) => (to === '/' ? location.pathname === '/' : location.pathname.startsWith(to));

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // 忽略登出接口错误，本地清理即可
    }
    clearAuth();
    navigate('/login', { replace: true });
  };

  const go = (to: string) => {
    setMenuOpen(false);
    navigate(to);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="h-14 bg-white border-b border-gray-200 flex items-center px-4 sm:px-6 gap-2 sm:gap-4 shadow-sm sticky top-0 z-30">
        <button onClick={() => go('/')} className="flex items-center gap-2 shrink-0">
          <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center text-white text-xs font-bold">R</div>
          <span className="font-semibold text-gray-800 text-sm hidden sm:inline">简历生成器</span>
        </button>

        {/* 一级导航（≥ sm 内联） */}
        <nav className="hidden sm:flex items-center gap-1">
          {NAV.map((n) => (
            <button
              key={n.to}
              onClick={() => go(n.to)}
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
                isActive(n.to) ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {n.label}
            </button>
          ))}
        </nav>

        <div className="flex-1" />

        {/* 账户区 */}
        {token ? (
          <div className="hidden sm:flex items-center gap-2">
            <span className="text-sm text-gray-500 max-w-[140px] truncate">{user?.nickname || user?.username}</span>
            <button
              onClick={handleLogout}
              className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors"
            >
              退出
            </button>
          </div>
        ) : (
          <button
            onClick={() => go('/login')}
            className="hidden sm:inline px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
          >
            登录
          </button>
        )}

        {/* 窄屏菜单 */}
        <button
          onClick={() => setMenuOpen((v) => !v)}
          aria-label="菜单"
          className="sm:hidden p-2 rounded-md text-gray-600 hover:bg-gray-100"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
      </header>

      {/* 窄屏展开面板 */}
      {menuOpen && (
        <div className="sm:hidden bg-white border-b border-gray-200 px-4 py-2 flex flex-col">
          {NAV.map((n) => (
            <button
              key={n.to}
              onClick={() => go(n.to)}
              className={`text-left px-3 py-2 text-sm rounded-md ${
                isActive(n.to) ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              {n.label}
            </button>
          ))}
          <div className="h-px bg-gray-100 my-1" />
          {token ? (
            <button onClick={handleLogout} className="text-left px-3 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded-md">
              退出（{user?.nickname || user?.username}）
            </button>
          ) : (
            <button onClick={() => go('/login')} className="text-left px-3 py-2 text-sm text-blue-600 hover:bg-gray-50 rounded-md">
              登录
            </button>
          )}
        </div>
      )}

      <Outlet />
    </div>
  );
}
