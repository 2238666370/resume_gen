import { useEffect, useState } from 'react';
import {
  getMyStatsOverview,
  getMyResumeStats,
  getResumeTrend,
  getResumeComments,
} from '../api/stats';
import type { MyStatsOverview, MyResumeStat, TrendPoint } from '../api/stats';
import type { CommunityComment } from '../api/types';
import { getErrorMessage } from '../api/client';

/** 简单 SVG 折线趋势图（PV/UV）。 */
function TrendChart({ data }: { data: TrendPoint[] }) {
  if (!data || data.length === 0) {
    return <p className="text-xs text-gray-400 py-6 text-center">暂无观阅数据</p>;
  }
  const W = 560;
  const H = 160;
  const pad = 30;
  const max = Math.max(1, ...data.map((d) => Math.max(d.pv, d.uv)));
  const stepX = data.length > 1 ? (W - pad * 2) / (data.length - 1) : 0;
  const x = (i: number) => pad + i * stepX;
  const y = (v: number) => H - pad - (v / max) * (H - pad * 2);

  const line = (key: 'pv' | 'uv') =>
    data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(d[key])}`).join(' ');

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} className="block">
      <line x1={pad} y1={H - pad} x2={W - pad} y2={H - pad} stroke="#e5e7eb" />
      <line x1={pad} y1={pad} x2={pad} y2={H - pad} stroke="#e5e7eb" />
      <path d={line('pv')} fill="none" stroke="#2563eb" strokeWidth="2" />
      <path d={line('uv')} fill="none" stroke="#16a34a" strokeWidth="2" />
      {data.map((d, i) => (
        <circle key={i} cx={x(i)} cy={y(d.pv)} r="2.5" fill="#2563eb" />
      ))}
    </svg>
  );
}

/**
 * 数据中心面板（F1）：从 MyStatsPage 抽取，供「我的 · 数据中心」Tab 复用，不含页面壳。
 */
export function MyStatsPanel() {
  const [overview, setOverview] = useState<MyStatsOverview | null>(null);
  const [resumes, setResumes] = useState<MyResumeStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<MyResumeStat | null>(null);
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    Promise.all([getMyStatsOverview(), getMyResumeStats()])
      .then(([o, r]) => {
        setOverview(o);
        setResumes(r);
        setLoading(false);
      })
      .catch((err) => {
        setError(getErrorMessage(err, '加载数据中心失败'));
        setLoading(false);
      });
  }, []);

  const openDetail = async (r: MyResumeStat) => {
    setSelected(r);
    setDetailLoading(true);
    try {
      const [t, c] = await Promise.all([getResumeTrend(r.id), getResumeComments(r.id)]);
      setTrend(t);
      setComments(c);
    } catch (err) {
      setError(getErrorMessage(err, '加载详情失败'));
    } finally {
      setDetailLoading(false);
    }
  };

  const cards: { label: string; value: number; color: string }[] = overview
    ? [
        { label: '简历总数', value: overview.resumeCount, color: 'text-blue-600' },
        { label: '总观阅 PV', value: overview.pv, color: 'text-blue-600' },
        { label: '总观阅 UV', value: overview.uv, color: 'text-green-600' },
        { label: '获赞', value: overview.likeCount, color: 'text-pink-600' },
        { label: '被收藏', value: overview.collectCount, color: 'text-orange-600' },
        { label: '评论', value: overview.commentCount, color: 'text-purple-600' },
        { label: '分享访问', value: overview.shareViews, color: 'text-cyan-600' },
      ]
    : [];

  return (
    <>
      {error && <p className="text-sm text-red-500 mb-4">{error}</p>}

      {loading ? (
        <div className="text-center text-gray-400 py-20">加载中...</div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
            {cards.map((c) => (
              <div key={c.label} className="bg-white rounded-xl border border-gray-200 p-4">
                <p className={`text-2xl font-bold ${c.color}`}>{c.value}</p>
                <p className="text-xs text-gray-400 mt-1">{c.label}</p>
              </div>
            ))}
          </div>

          <h2 className="text-sm font-medium text-gray-700 mb-3">按简历查看</h2>
          {resumes.length === 0 ? (
            <p className="text-sm text-gray-400 py-10 text-center">暂无简历数据</p>
          ) : (
            <div className="flex flex-col gap-2 mb-8">
              {resumes.map((r) => (
                <div
                  key={r.id}
                  onClick={() => openDetail(r)}
                  className={`bg-white rounded-xl border p-4 cursor-pointer transition-colors ${
                    selected?.id === r.id ? 'border-blue-400 bg-blue-50/40' : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-gray-800">{r.title || '未命名简历'}</span>
                    <span className="text-xs text-gray-400">{r.updatedAt ? `更新于 ${r.updatedAt}` : ''}</span>
                  </div>
                  <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-gray-500">
                    <span>PV {r.pv}</span>
                    <span>UV {r.uv}</span>
                    <span>赞 {r.likeCount}</span>
                    <span>藏 {r.collectCount}</span>
                    <span>评 {r.commentCount}</span>
                    <span>分享浏览 {r.shareViews}</span>
                  </div>
                </div>
              ))}
            </div>
          )}

          {selected && (
            <div className="bg-white rounded-xl border border-gray-200 p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-medium text-gray-800">{selected.title || '未命名简历'}</h3>
                <button onClick={() => setSelected(null)} className="text-xs text-gray-400 hover:text-gray-600">收起</button>
              </div>
              {detailLoading ? (
                <div className="text-center text-gray-400 py-10">加载中...</div>
              ) : (
                <>
                  <p className="text-xs font-medium text-gray-600 mb-2">近 24 小时观阅趋势（蓝=PV 绿=UV）</p>
                  <TrendChart data={trend} />
                  <p className="text-xs font-medium text-gray-600 mt-6 mb-2">收到的评论</p>
                  {comments.length === 0 ? (
                    <p className="text-xs text-gray-400">暂无评论</p>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {comments.map((c) => (
                        <div key={c.id} className="border border-gray-100 rounded-lg p-3">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-xs font-medium text-gray-700">{c.userNickname || '匿名'}</span>
                            <span className="text-xs text-gray-400">{c.createdAt}</span>
                          </div>
                          <p className="text-sm text-gray-600">{c.content}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </>
      )}
    </>
  );
}
