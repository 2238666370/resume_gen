import { useCallback, useEffect, useState } from 'react';
import { Button, Modal, Space, Table, Tag, message } from 'antd';
import { api } from '../api';
import type { TemplateItem } from '../types';

const STATUS: Record<number, { text: string; color: string }> = {
  0: { text: '已下架', color: 'default' },
  1: { text: '已上架', color: 'green' },
  2: { text: '待审核', color: 'orange' },
  3: { text: '草稿', color: 'blue' },
};

export default function TemplatesPage() {
  const [data, setData] = useState<TemplateItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.templates(page, size);
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error((err as Error).message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [page, size]);

  useEffect(() => { load(); }, [load]);

  const audit = async (id: string, approve: boolean) => {
    let reason: string | undefined;
    if (!approve) {
      const input = prompt('请输入拒绝原因（可选）');
      reason = input ?? undefined;
    }
    try {
      await api.auditTemplate(id, approve, reason);
      message.success(approve ? '已通过' : '已拒绝');
      load();
    } catch (err) {
      message.error((err as Error).message || '操作失败');
    }
  };

  const setStatus = async (id: string, status: number) => {
    try {
      await api.setTemplateStatus(id, status);
      message.success('已更新');
      load();
    } catch (err) {
      message.error((err as Error).message || '操作失败');
    }
  };

  const remove = (id: string) => {
    Modal.confirm({
      title: '确认删除该模板？',
      onOk: async () => {
        try {
          await api.deleteTemplate(id);
          message.success('已删除');
          load();
        } catch (err) {
          message.error((err as Error).message || '删除失败');
        }
      },
    });
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '名称', dataIndex: 'name' },
    { title: '编码', dataIndex: 'code', ellipsis: true },
    {
      title: '类型', dataIndex: 'type', width: 90,
      render: (v: string) => (v === 'user' ? <Tag color="purple">用户</Tag> : <Tag>官方</Tag>),
    },
    { title: '分类', dataIndex: 'category', width: 100 },
    { title: '使用/浏览', width: 120, render: (_: unknown, r: TemplateItem) => `${r.useCount ?? 0} / ${r.viewCount ?? 0}` },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v: number) => {
        const s = STATUS[v] ?? STATUS[3];
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '作者', dataIndex: 'ownerUserId', width: 90, render: (v?: string) => v ?? '-' },
    {
      title: '操作', width: 220,
      render: (_: unknown, r: TemplateItem) => (
        <Space>
          {r.status === 2 && <Button size="small" type="primary" onClick={() => audit(r.id, true)}>通过</Button>}
          {r.status === 2 && <Button size="small" danger onClick={() => audit(r.id, false)}>拒绝</Button>}
          {r.status === 1 && <Button size="small" onClick={() => setStatus(r.id, 0)}>下架</Button>}
          {r.status === 0 && <Button size="small" onClick={() => setStatus(r.id, 1)}>上架</Button>}
          <Button size="small" danger onClick={() => remove(r.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>模板管理</h2>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: page,
          pageSize: size,
          total,
          onChange: (p, s) => { setPage(p); setSize(s); },
        }}
      />
    </div>
  );
}
