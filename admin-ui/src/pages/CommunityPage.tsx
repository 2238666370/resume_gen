import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import { api } from '../api';
import type { AdminPostItem, AdminReportItem } from '../types';

const STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: '待审核', color: 'default' },
  1: { text: '已上线', color: 'green' },
  2: { text: '未通过', color: 'red' },
  3: { text: '复审中', color: 'orange' },
  4: { text: '已下架', color: 'gray' },
};

function StatusTag({ status }: { status: number }) {
  const s = STATUS_MAP[status] ?? { text: '未知', color: 'default' };
  return <Tag color={s.color}>{s.text}</Tag>;
}

export default function CommunityPage() {
  const [active, setActive] = useState('posts');

  // 帖子管理
  const [list, setList] = useState<AdminPostItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [status, setStatus] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  // 审核弹窗
  const [auditTarget, setAuditTarget] = useState<AdminPostItem | null>(null);
  const [auditStatus, setAuditStatus] = useState(1);
  const [auditReason, setAuditReason] = useState('');
  const [auditing, setAuditing] = useState(false);

  // 举报处理
  const [reports, setReports] = useState<AdminReportItem[]>([]);
  const [reportTotal, setReportTotal] = useState(0);
  const [reportPage, setReportPage] = useState(1);
  const [reportLoading, setReportLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.communityPosts(page, size, status);
      setList(res.records);
      setTotal(res.total);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page, size, status]);

  useEffect(() => {
    load();
  }, [load]);

  const loadReports = useCallback(async () => {
    setReportLoading(true);
    try {
      const res = await api.communityReports(reportPage, 10);
      setReports(res.records);
      setReportTotal(res.total);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setReportLoading(false);
    }
  }, [reportPage]);

  useEffect(() => {
    if (active === 'reports') loadReports();
  }, [loadReports, active]);

  const openAudit = (p: AdminPostItem) => {
    setAuditTarget(p);
    setAuditStatus(p.auditStatus === 1 ? 1 : p.auditStatus);
    setAuditReason('');
  };

  const submitAudit = async () => {
    if (!auditTarget) return;
    setAuditing(true);
    try {
      await api.auditPost(auditTarget.id, auditStatus, auditReason || undefined);
      message.success('审核已提交');
      setAuditTarget(null);
      load();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setAuditing(false);
    }
  };

  const delPost = async (p: AdminPostItem) => {
    try {
      await api.deletePost(p.id);
      message.success('已删除');
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const resolve = async (r: AdminReportItem) => {
    try {
      await api.resolveReport(r.id);
      message.success('已标记处理');
      loadReports();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const postColumns: TableProps<AdminPostItem>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '标题', dataIndex: 'title', render: (v) => v || '-' },
    { title: '作者', dataIndex: 'authorNickname', width: 120, render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'auditStatus',
      width: 100,
      render: (s: number) => <StatusTag status={s} />,
    },
    { title: '审核理由', dataIndex: 'auditReason', width: 140, render: (v) => v || '-' },
    {
      title: '热度',
      key: 'heat',
      width: 200,
      render: (_: unknown, p: AdminPostItem) => (
        <span className="text-xs text-gray-500">
          👍{p.likeCount} · ⭐{p.collectCount} · 💬{p.commentCount} · 👁{p.viewCount} · ⚠{p.reportCount}
        </span>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 150, render: (v) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, p: AdminPostItem) => (
        <Space size={4}>
          <Button size="small" onClick={() => openAudit(p)}>审核</Button>
          <Popconfirm title="确定删除该帖子？" onConfirm={() => delPost(p)}>
            <Button danger size="small">删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const reportColumns: TableProps<AdminReportItem>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '帖子', dataIndex: 'postTitle', render: (v) => v || '-' },
    { title: '帖子ID', dataIndex: 'postId', width: 90 },
    { title: '举报人', dataIndex: 'reporterNickname', width: 120, render: (v) => v || '-' },
    { title: '理由', dataIndex: 'reason', render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: number) => (s === 1 ? <Tag color="green">已处理</Tag> : <Tag color="orange">待处理</Tag>),
    },
    { title: '时间', dataIndex: 'createdAt', width: 150, render: (v) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_: unknown, r: AdminReportItem) =>
        r.status === 1 ? (
          <span className="text-gray-400">—</span>
        ) : (
          <Button size="small" onClick={() => resolve(r)}>标记已处理</Button>
        ),
    },
  ];

  return (
    <div>
      <Tabs
        activeKey={active}
        onChange={setActive}
        items={[
          {
            key: 'posts',
            label: '帖子管理',
            children: (
              <div>
                <Space style={{ marginBottom: 16 }}>
                  <Select
                    allowClear
                    placeholder="按状态筛选"
                    style={{ width: 140 }}
                    value={status}
                    onChange={(v) => { setStatus(v); setPage(1); }}
                    options={[
                      { value: 0, label: '待审核' },
                      { value: 1, label: '已上线' },
                      { value: 2, label: '未通过' },
                      { value: 3, label: '复审中' },
                      { value: 4, label: '已下架' },
                    ]}
                  />
                </Space>
                <Table
                  rowKey="id"
                  columns={postColumns}
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
              </div>
            ),
          },
          {
            key: 'reports',
            label: '举报处理',
            children: (
              <Table
                rowKey="id"
                columns={reportColumns}
                dataSource={reports}
                loading={reportLoading}
                pagination={{
                  current: reportPage,
                  pageSize: 10,
                  total: reportTotal,
                  onChange: (p) => setReportPage(p),
                }}
              />
            ),
          },
        ]}
      />

      <Modal
        title={`审核帖子「${auditTarget?.title ?? ''}」`}
        open={!!auditTarget}
        onCancel={() => setAuditTarget(null)}
        onOk={submitAudit}
        confirmLoading={auditing}
        destroyOnClose
      >
        <div className="mb-3">
          <div className="mb-1 text-sm text-gray-500">审核结果</div>
          <Select
            value={auditStatus}
            onChange={setAuditStatus}
            style={{ width: '100%' }}
            options={[
              { value: 1, label: '上线' },
              { value: 2, label: '拒绝' },
              { value: 4, label: '下架' },
            ]}
          />
        </div>
        <div>
          <div className="mb-1 text-sm text-gray-500">审核理由（可选）</div>
          <Input.TextArea
            value={auditReason}
            onChange={(e) => setAuditReason(e.target.value)}
            maxLength={255}
            rows={3}
            placeholder="拒绝或下架时填写作废理由"
          />
        </div>
      </Modal>
    </div>
  );
}