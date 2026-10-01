import { useEffect, useState } from 'react';
import { Card, Col, Empty, Row, Segmented, Spin, Statistic, message } from 'antd';
import {
  EyeOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
  RiseOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { api } from '../api';
import type { StatsOverview, TrendPoint } from '../types';

type Granularity = 'minute' | 'hour' | 'day';

function fmtTick(time: string): string {
  return time.endsWith(' 00:00:00') ? time.slice(5, 10) : time.slice(11, 16);
}

function TrendChart({ data }: { data: TrendPoint[] }) {
  if (data.length === 0) {
    return <Empty description="暂无数据" />;
  }
  const W = 800;
  const H = 300;
  const PAD = { l: 48, r: 16, t: 20, b: 28 };
  const innerW = W - PAD.l - PAD.r;
  const innerH = H - PAD.t - PAD.b;
  const max = Math.max(1, ...data.map((d) => Math.max(d.pv, d.uv)));
  const x = (i: number) => (data.length === 1 ? PAD.l + innerW / 2 : PAD.l + (i / (data.length - 1)) * innerW);
  const y = (v: number) => PAD.t + innerH - (v / max) * innerH;
  const line = (key: 'pv' | 'uv') => data.map((d, i) => `${x(i)},${y(d[key])}`).join(' ');
  const tickIdx = Array.from(new Set([0, Math.floor((data.length - 1) / 2), data.length - 1]));

  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto', display: 'block' }}>
      {[0, 0.25, 0.5, 0.75, 1].map((r) => (
        <line
          key={r}
          x1={PAD.l}
          x2={W - PAD.r}
          y1={PAD.t + innerH * (1 - r)}
          y2={PAD.t + innerH * (1 - r)}
          stroke="#eee"
        />
      ))}
      {[0, 0.5, 1].map((r) => (
        <text key={r} x={PAD.l - 6} y={PAD.t + innerH * (1 - r) + 4} textAnchor="end" fontSize="11" fill="#999">
          {Math.round(max * r)}
        </text>
      ))}
      <polyline points={line('pv')} fill="none" stroke="#1677ff" strokeWidth="2" />
      <polyline points={line('uv')} fill="none" stroke="#52c41a" strokeWidth="2" />
      {tickIdx.map((i) => (
        <text key={i} x={x(i)} y={H - 8} textAnchor="middle" fontSize="11" fill="#999">
          {fmtTick(data[i].time)}
        </text>
      ))}
      <g transform={`translate(${PAD.l}, 4)`}>
        <rect width="10" height="10" fill="#1677ff" />
        <text x="14" y="9" fontSize="11" fill="#333">PV</text>
        <rect x="60" width="10" height="10" fill="#52c41a" />
        <text x="74" y="9" fontSize="11" fill="#333">UV</text>
      </g>
    </svg>
  );
}

export default function DashboardPage() {
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [granularity, setGranularity] = useState<Granularity>('hour');
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.statsOverview()
      .then(setOverview)
      .catch((e) => message.error((e as Error).message));
  }, []);

  useEffect(() => {
    setLoading(true);
    api.statsTrend(granularity)
      .then(setTrend)
      .catch((e) => message.error((e as Error).message))
      .finally(() => setLoading(false));
  }, [granularity]);

  return (
    <div>
      <Row gutter={16}>
        <Col span={5}>
          <Card><Statistic title="用户总数" value={overview?.userCount ?? 0} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col span={5}>
          <Card><Statistic title="简历总数" value={overview?.resumeCount ?? 0} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col span={5}>
          <Card><Statistic title="今日 PV" value={overview?.todayPv ?? 0} prefix={<EyeOutlined />} /></Card>
        </Col>
        <Col span={5}>
          <Card><Statistic title="今日 UV" value={overview?.todayUv ?? 0} prefix={<RiseOutlined />} /></Card>
        </Col>
        <Col span={4}>
          <Card><Statistic title="实时在线" value={overview?.onlineUsers ?? 0} prefix={<FieldTimeOutlined />} suffix="(5min)" /></Card>
        </Col>
      </Row>

      <Card
        title="PV / UV 趋势"
        style={{ marginTop: 16 }}
        extra={
          <Segmented
            value={granularity}
            onChange={(v) => setGranularity(v as Granularity)}
            options={[
              { label: '分钟', value: 'minute' },
              { label: '小时', value: 'hour' },
              { label: '天', value: 'day' },
            ]}
          />
        }
      >
        {loading ? <Spin /> : <TrendChart data={trend} />}
      </Card>
    </div>
  );
}