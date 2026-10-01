import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { track, trackPublic } from './api/track';

// 路由级代码分割（R10-O8）：各页面按需加载，减小首屏体积
const LoginPage = lazy(() => import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const WorkbenchPage = lazy(() => import('./pages/WorkbenchPage').then((m) => ({ default: m.WorkbenchPage })));
const AiHubPage = lazy(() => import('./pages/AiHubPage').then((m) => ({ default: m.AiHubPage })));
const DiscoverPage = lazy(() => import('./pages/DiscoverPage').then((m) => ({ default: m.DiscoverPage })));
const MePage = lazy(() => import('./pages/MePage').then((m) => ({ default: m.MePage })));
const EditorPage = lazy(() => import('./pages/EditorPage').then((m) => ({ default: m.EditorPage })));
const ShareViewPage = lazy(() => import('./pages/ShareViewPage').then((m) => ({ default: m.ShareViewPage })));
const CommunityDetailPage = lazy(() => import('./pages/CommunityDetailPage').then((m) => ({ default: m.CommunityDetailPage })));
const TemplateBuilderPage = lazy(() => import('./pages/TemplateBuilderPage').then((m) => ({ default: m.TemplateBuilderPage })));
const AppShell = lazy(() => import('./components/layout/AppShell').then((m) => ({ default: m.AppShell })));

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

/** 旧路径（仅重定向用），不参与埋点，避免重定向导致重复计数。 */
const LEGACY_PATHS = new Set([
  '/interview',
  '/ai/history',
  '/stats/my',
  '/community',
  '/templates/market',
  '/templates/my',
]);

/** 路由切换时上报页面访问（PV/UV 埋点），按一级路径上报。 */
function RouteTracker() {
  const location = useLocation();
  useEffect(() => {
    const p = location.pathname;
    if (p === '/login' || LEGACY_PATHS.has(p)) return;
    if (p.startsWith('/s/')) trackPublic(p);
    else track(p);
  }, [location.pathname]);
  return null;
}

function PageLoading() {
  return <div className="min-h-screen flex items-center justify-center text-gray-500">加载中...</div>;
}

function App() {
  return (
    <HashRouter>
      <RouteTracker />
      <Suspense fallback={<PageLoading />}>
        <Routes>
          {/* 匿名独立页 */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/s/:key" element={<ShareViewPage />} />

          {/* 登录态 · 统一外壳（一级导航 + 账户区） */}
          <Route element={<RequireAuth><AppShell /></RequireAuth>}>
            <Route path="/" element={<WorkbenchPage />} />
            <Route path="/ai" element={<AiHubPage />} />
            <Route path="/me" element={<MePage />} />
          </Route>

          {/* 匿名可访问 · 统一外壳（账户区显示登录） */}
          <Route element={<AppShell />}>
            <Route path="/discover" element={<DiscoverPage />} />
          </Route>

          {/* 全屏专注页（不套外壳） */}
          <Route path="/editor/:id" element={<RequireAuth><EditorPage /></RequireAuth>} />
          <Route path="/community/:id" element={<CommunityDetailPage />} />
          <Route path="/templates/builder" element={<RequireAuth><TemplateBuilderPage /></RequireAuth>} />
          <Route path="/templates/builder/:id" element={<RequireAuth><TemplateBuilderPage /></RequireAuth>} />

          {/* 旧路由重定向（保持书签可用，落到正确 Tab） */}
          <Route path="/interview" element={<Navigate to="/ai" replace />} />
          <Route path="/ai/history" element={<Navigate to="/ai?tab=history" replace />} />
          <Route path="/stats/my" element={<Navigate to="/me" replace />} />
          <Route path="/community" element={<Navigate to="/discover" replace />} />
          <Route path="/templates/market" element={<Navigate to="/discover?tab=market" replace />} />
          <Route path="/templates/my" element={<Navigate to="/?tab=templates" replace />} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </HashRouter>
  );
}

export default App;
