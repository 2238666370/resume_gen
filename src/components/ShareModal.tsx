import { useEffect, useState } from 'react';
import { createShare, listShares, revokeShare } from '../api/shares';
import type { ShareVO } from '../api/types';

interface Props {
  resumeId: string;
  onClose: () => void;
}

export function ShareModal({ resumeId, onClose }: Props) {
  const [tab, setTab] = useState<'create' | 'list'>('create');
  const [expireDays, setExpireDays] = useState(30);
  const [password, setPassword] = useState('');
  const [showContact, setShowContact] = useState(false);
  const [creating, setCreating] = useState(false);
  const [result, setResult] = useState<ShareVO | null>(null);
  const [shares, setShares] = useState<ShareVO[]>([]);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const refreshList = () => {
    listShares()
      .then(setShares)
      .catch((err) => setError((err as Error)?.message || '加载失败'));
  };

  useEffect(() => {
    refreshList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCreate = () => {
    setCreating(true);
    setError('');
    createShare({ resumeId, expireDays, password: password || undefined, showContact })
      .then((vo) => {
        setResult(vo);
        setCreating(false);
        refreshList();
      })
      .catch((err) => {
        setError((err as Error)?.message || '生成失败');
        setCreating(false);
      });
  };

  const handleRevoke = (key: string) => {
    revokeShare(key)
      .then(refreshList)
      .catch((err) => setError((err as Error)?.message || '撤销失败'));
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError('复制失败，请手动复制');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-2xl w-[480px] max-w-[92vw] max-h-[85vh] overflow-auto p-5"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-gray-800">分享简历</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <div className="flex gap-2 mb-4">
          <button
            onClick={() => setTab('create')}
            className={`px-3 py-1.5 text-sm rounded-md ${tab === 'create' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
          >
            生成分享
          </button>
          <button
            onClick={() => setTab('list')}
            className={`px-3 py-1.5 text-sm rounded-md ${tab === 'list' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
          >
            我的分享
          </button>
        </div>

        {error && <p className="text-sm text-red-500 mb-3">{error}</p>}

        {tab === 'create' && (
          <div className="flex flex-col gap-3">
            <label className="flex items-center justify-between text-sm text-gray-600">
              有效期
              <select
                value={expireDays}
                onChange={(e) => setExpireDays(Number(e.target.value))}
                className="border border-gray-300 rounded-md px-2 py-1.5 text-sm"
              >
                <option value={0}>永久</option>
                <option value={7}>7 天</option>
                <option value={30}>30 天</option>
              </select>
            </label>

            <label className="flex items-center justify-between text-sm text-gray-600">
              访问密码（可选）
              <input
                type="text"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="留空则无需密码"
                className="border border-gray-300 rounded-md px-2 py-1.5 text-sm w-44"
              />
            </label>

            <label className="flex items-center gap-2 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={showContact}
                onChange={(e) => setShowContact(e.target.checked)}
              />
              展示联系方式（手机/邮箱）
            </label>

            <button
              onClick={handleCreate}
              disabled={creating}
              className="bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700 disabled:opacity-60"
            >
              {creating ? '生成中...' : '生成分享链接'}
            </button>

            {result && (
              <div className="border border-gray-200 rounded-lg p-3 bg-gray-50">
                <p className="text-xs text-gray-500 mb-2">分享已生成：</p>
                <div className="flex items-center gap-2">
                  <input
                    readOnly
                    value={result.url || result.shareKey}
                    className="flex-1 border border-gray-300 rounded-md px-2 py-1.5 text-xs font-mono bg-white"
                  />
                  <button
                    onClick={() => handleCopy(result.url || result.shareKey)}
                    className="px-3 py-1.5 text-xs bg-gray-700 text-white rounded-md hover:bg-gray-800"
                  >
                    {copied ? '已复制' : '复制'}
                  </button>
                </div>
                <p className="text-[11px] text-gray-400 mt-1.5">key: {result.shareKey}</p>
              </div>
            )}
          </div>
        )}

        {tab === 'list' && (
          <div className="flex flex-col gap-2">
            {shares.length === 0 && <p className="text-sm text-gray-400 text-center py-6">暂无分享记录</p>}
            {shares.map((s) => (
              <div key={s.id} className="border border-gray-200 rounded-lg p-3 flex items-center gap-2">
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-700 font-mono truncate">{s.shareKey}</p>
                  <p className="text-[11px] text-gray-400">
                    浏览 {s.viewCount} · {s.expireAt ? `至 ${s.expireAt}` : '永久'} · {s.status === 1 ? '有效' : '已撤销'}
                  </p>
                </div>
                {s.url && (
                  <button onClick={() => handleCopy(s.url || s.shareKey)} className="px-2 py-1 text-xs text-blue-600 hover:bg-blue-50 rounded">复制</button>
                )}
                {s.status === 1 && (
                  <button onClick={() => handleRevoke(s.shareKey)} className="px-2 py-1 text-xs text-red-500 hover:bg-red-50 rounded">撤销</button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}