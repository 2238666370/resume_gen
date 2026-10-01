import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';

export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [captchaId, setCaptchaId] = useState('');
  const [captchaImg, setCaptchaImg] = useState('');

  const refreshCaptcha = useCallback(async () => {
    try {
      const c = await api.getCaptcha();
      setCaptchaId(c.captchaId);
      setCaptchaImg(c.imageBase64);
    } catch {
      // 忽略验证码加载失败
    }
  }, []);

  useEffect(() => {
    refreshCaptcha();
  }, [refreshCaptcha]);

  const onFinish = async (values: { username: string; password: string; captchaCode: string }) => {
    setLoading(true);
    try {
      const res = await api.login(values.username, values.password, captchaId, values.captchaCode);
      if (res.user.role !== 'ADMIN') {
        message.error('该账号无管理员权限');
        refreshCaptcha();
        return;
      }
      setAuth(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      message.error((err as Error).message || '登录失败');
      refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card title="简历生成器 · 管理后台" style={{ width: 380 }}>
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="管理员账号" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" />
          </Form.Item>
          <Form.Item name="captchaCode" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Input placeholder="输入右侧字符" style={{ flex: 1 }} />
              <img
                src={captchaImg}
                alt="验证码"
                onClick={refreshCaptcha}
                style={{ height: 40, width: 100, border: '1px solid #d9d9d9', borderRadius: 4, cursor: 'pointer', objectFit: 'cover' }}
                title="点击刷新"
              />
            </div>
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>登录</Button>
        </Form>
      </Card>
    </div>
  );
}