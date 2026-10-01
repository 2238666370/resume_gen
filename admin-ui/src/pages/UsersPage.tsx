import { useCallback, useEffect, useState } from 'react';
import { Button, Drawer, Input, Modal, Select, Space, Switch, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import { api } from '../api';
import type { ResumeItem, UserVO } from '../types';

export default function UsersPage() {
  const [list, setList] = useState<UserVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  const [drawerUser, setDrawerUser] = useState<UserVO | null>(null);
  const [resumes, setResumes] = useState<ResumeItem[]>([]);
  const [resumeTotal, setResumeTotal] = useState(0);
  const [resumePage, setResumePage] = useState(1);
  const [resumeLoading, setResumeLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.users(page, size, keyword || undefined);
      setList(res.records);
      setTotal(res.total);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword]);

  useEffect(() => {
    load();
  }, [load]);

  const changeRole = (u: UserVO, role: string) => {
    if (u.role === role) return;
    Modal.confirm({
      title: '修改角色',
      content: `确定将用户「${u.username}」的角色改为 ${role === 'ADMIN' ? '管理员' : '普通用户'}？`,
      onOk: async () => {
        try {
          await api.setUserRole(u.id, role);
          message.success('角色已更新');
          load();
        } catch (e) {
          message.error((e as Error).message);
        }
      },
    });
  };

  const toggle = async (u: UserVO, checked: boolean) => {
    try {
      await api.setUserStatus(u.id, checked ? 1 : 0);
      message.success(checked ? '已启用' : '已禁用并强制下线');
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const openResumeDrawer = (u: UserVO) => {
    setDrawerUser(u);
    setResumePage(1);
  };

  const loadResumes = useCallback(async () => {
    if (!drawerUser) return;
    setResumeLoading(true);
    try {
      const res = await api.userResumes(drawerUser.id, resumePage, 10);
      setResumes(res.records);
      setResumeTotal(res.total);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setResumeLoading(false);
    }
  }, [drawerUser, resumePage]);

  useEffect(() => {
    if (drawerUser) loadResumes();
  }, [loadResumes]);

  const delResume = async (r: ResumeItem) => {
    try {
      await api.deleteResume(r.id);
      message.success('已删除');
      loadResumes();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const columns: TableProps<UserVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '昵称', dataIndex: 'nickname', render: (v) => v || '-' },
    { title: '邮箱', dataIndex: 'email', render: (v) => v || '-' },
    {
      title: '角色',
      dataIndex: 'role',
      width: 120,
      render: (r: string, u: UserVO) => (
        <Select
          value={r}
          style={{ width: 100 }}
          size="small"
          onChange={(v) => changeRole(u, v)}
          options={[
            { value: 'USER', label: 'USER' },
            { value: 'ADMIN', label: 'ADMIN' },
          ]}
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_: unknown, u: UserVO) => (
        <Switch checked={u.status === 1} onChange={(c) => toggle(u, c)} />
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', render: (v) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, u: UserVO) => (
        <Button size="small" onClick={() => openResumeDrawer(u)}>查看简历</Button>
      ),
    },
  ];

  const resumeColumns: TableProps<ResumeItem>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '标题', dataIndex: 'title', render: (v) => v || '未命名简历' },
    { title: '模板', dataIndex: 'templateId', width: 110 },
    { title: '更新时间', dataIndex: 'updatedAt', render: (v) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, r: ResumeItem) => (
        <a onClick={() => delResume(r)} className="text-red-500">删除</a>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="搜索用户名/昵称"
          allowClear
          onSearch={(v) => { setKeyword(v); setPage(1); }}
          style={{ width: 260 }}
        />
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={list}
        loading={loading}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => { setPage(p); setSize(ps); },
        }}
      />

      <Drawer
        title={`${drawerUser?.nickname || drawerUser?.username} 的简历`}
        width={640}
        open={!!drawerUser}
        onClose={() => setDrawerUser(null)}
      >
        <Table
          rowKey="id"
          columns={resumeColumns}
          dataSource={resumes}
          loading={resumeLoading}
          pagination={{
            current: resumePage,
            pageSize: 10,
            total: resumeTotal,
            onChange: (p) => setResumePage(p),
          }}
        />
      </Drawer>
    </div>
  );
}