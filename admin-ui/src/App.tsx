import type { ReactNode } from 'react';
import { Button, ConfigProvider, Layout, Menu, Space } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { CommentOutlined, DashboardOutlined, FileTextOutlined, LayoutOutlined, LogoutOutlined, SafetyOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api } from './api';
import { useAuth } from './auth';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import UsersPage from './pages/UsersPage';
import ResumesPage from './pages/ResumesPage';
import CommunityPage from './pages/CommunityPage';
import TemplatesPage from './pages/TemplatesPage';
import WatermarkPage from './pages/WatermarkPage';

const { Header, Sider, Content } = Layout;

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuth((s) => s.token);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function Shell({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);

  const selected =
    location.pathname === '/users' ? '/users'
      : location.pathname === '/resumes' ? '/resumes'
        : location.pathname === '/community' ? '/community'
          : location.pathname === '/templates' ? '/templates'
            : location.pathname === '/watermark' ? '/watermark'
              : '/';

  const logout = async () => {
    try {
      await api.logout();
    } catch {
      // 忽略登出接口错误
    }
    clear();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible>
        <div style={{ color: '#fff', fontWeight: 600, padding: '16px 24px', fontSize: 15 }}>简历管理后台</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          onClick={(e) => navigate(e.key)}
          items={[
            { key: '/', icon: <DashboardOutlined />, label: '数据看板' },
            { key: '/users', icon: <TeamOutlined />, label: '用户管理' },
            { key: '/resumes', icon: <FileTextOutlined />, label: '简历管理' },
            { key: '/community', icon: <CommentOutlined />, label: '社区治理' },
            { key: '/templates', icon: <LayoutOutlined />, label: '模板管理' },
            { key: '/watermark', icon: <SafetyOutlined />, label: '水印溯源' },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
          <Space size="middle">
            <span style={{ color: '#555' }}><UserOutlined /> {user?.nickname || user?.username}</span>
            <Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出</Button>
          </Space>
        </Header>
        <Content style={{ margin: 24 }}>{children}</Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<RequireAuth><Shell><DashboardPage /></Shell></RequireAuth>} />
          <Route path="/users" element={<RequireAuth><Shell><UsersPage /></Shell></RequireAuth>} />
          <Route path="/resumes" element={<RequireAuth><Shell><ResumesPage /></Shell></RequireAuth>} />
          <Route path="/community" element={<RequireAuth><Shell><CommunityPage /></Shell></RequireAuth>} />
          <Route path="/templates" element={<RequireAuth><Shell><TemplatesPage /></Shell></RequireAuth>} />
          <Route path="/watermark" element={<RequireAuth><Shell><WatermarkPage /></Shell></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </HashRouter>
    </ConfigProvider>
  );
}