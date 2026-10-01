import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Popconfirm, Space, Table, message } from 'antd';
import type { TableProps } from 'antd';
import { api } from '../api';
import type { ResumeItem } from '../types';

export default function ResumesPage() {
  const [list, setList] = useState<ResumeItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.resumes(page, size, keyword || undefined);
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

  const del = async (r: ResumeItem) => {
    try {
      await api.deleteResume(r.id);
      message.success('已删除');
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const columns: TableProps<ResumeItem>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '标题', dataIndex: 'title', render: (v) => v || '未命名简历' },
    { title: '模板', dataIndex: 'templateId', width: 110 },
    { title: '所属用户', dataIndex: 'username', render: (v) => v || '-' },
    { title: '更新时间', dataIndex: 'updatedAt', render: (v) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, r: ResumeItem) => (
        <Popconfirm title="确定删除该简历？" onConfirm={() => del(r)}>
          <Button danger size="small">删除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="搜索简历标题"
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
    </div>
  );
}