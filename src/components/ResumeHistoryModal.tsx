import { useEffect, useRef, useState } from 'react';
import { listHistory, getHistory, rollbackResume } from '../api/resumes';
import type { ResumeSnapshot } from '../api/resumes';
import { diffResume } from '../utils/resumeDiff';
import type { ResumeDiffItem } from '../utils/resumeDiff';
import { useResumeStore } from '../store/resumeStore';
import { getErrorMessage } from '../api/client';
import { ProgressBar } from './ProgressBar';

interface Props {
  resumeId: string;
  onClose: () => void;
}

const SOURCE_LABELS: Record<string, string> = {
  manual: '手动保存',
  ai_apply: 'AI 应用',
  rollback: '回滚',
};

/**
 * 简历版本历史 / 时光机：时间轴列出快照，支持对比当前与一键回滚。
 */
export function ResumeHistoryModal({ resumeId, onClose }: Props) {
  const currentData = useResumeStore((s) => s.data);
  const [items, setItems] = useState<ResumeSnapshot[] | null>(null);
  const [error, setError] = useState('');
  const [diffItems, setDiffItems] = useState<ResumeDiffItem[] | null>(null);
  const [diffTitle, setDiffTitle] = useState('');
  const [rollingBack, setRollingBack] = useState<string | null>(null);
  const started = useRef(false);

  const load = () => {
    listHistory(resumeId)
      .then(setItems)
      .catch((err) => setError(getErrorMessage(err, '加载历史失败')));
  };

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId]);

  const compare = async (snap: ResumeSnapshot) => {
    try {
      const full = await getHistory(resumeId, snap.id);
      const oldData = full.content ?? currentData;
      setDiffItems(diffResume(oldData, currentData));
      setDiffTitle(`版本 ${snap.version} 对比当前`);
    } catch (err) {
      setError(getErrorMessage(err, '对比失败'));
    }
  };

  const rollback = async (snap: ResumeSnapshot) => {
    if (!window.confirm(`确定回滚到版本 ${snap.version} 吗？当前内容将被覆盖（可再次回滚）。`)) return;
    setRollingBack(snap.id);
    try {
      await rollbackResume(resumeId, snap.id);
      // 回滚已写回后端（乐观锁 version 递增），重载页面以刷新编辑器与版本号
      window.location.reload();
    } catch (err) {
      setError(getErrorMessage(err, '回滚失败'));
      setRollingBack(null);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-xl w-[640px] max-w-[92vw] max-h-[82vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h3 className="font-medium text-gray-800">历史版本</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-5">
          {error && <p className="text-sm text-red-500 mb-3">{error}</p>}
          {!error && items === null && <ProgressBar label="正在加载历史版本..." className="py-4" />}
          {items && items.length === 0 && <p className="text-sm text-gray-400">暂无历史版本</p>}

          {items && items.length > 0 && (
            <ol className="relative border-l border-gray-200 ml-2 space-y-4">
              {items.map((s) => (
                <li key={s.id} className="ml-5">
                  <span className="absolute -left-1.5 w-3 h-3 rounded-full bg-blue-500 mt-1.5" />
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm font-medium text-gray-700">版本 {s.version}</span>
                    <span className="text-xs px-1.5 py-0.5 rounded bg-gray-100 text-gray-500">
                      {SOURCE_LABELS[s.source] ?? s.source}
                    </span>
                    <span className="text-xs text-gray-400">{s.createdAt}</span>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => compare(s)}
                      className="text-xs px-2.5 py-1 rounded border border-gray-200 text-gray-600 hover:bg-gray-50"
                    >
                      对比当前
                    </button>
                    <button
                      onClick={() => rollback(s)}
                      disabled={rollingBack === s.id}
                      className="text-xs px-2.5 py-1 rounded border border-red-200 text-red-600 hover:bg-red-50 disabled:opacity-60"
                    >
                      {rollingBack === s.id ? '回滚中...' : '回滚到此版本'}
                    </button>
                  </div>
                </li>
              ))}
            </ol>
          )}

          {diffItems && (
            <div className="mt-5 border-t border-gray-100 pt-4">
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium text-gray-700">{diffTitle}</p>
                <button onClick={() => setDiffItems(null)} className="text-xs text-gray-400 hover:text-gray-600">关闭</button>
              </div>
              {diffItems.length === 0 && <p className="text-xs text-gray-400">与当前内容一致</p>}
              <ul className="space-y-2 max-h-60 overflow-y-auto">
                {diffItems.map((d) => (
                  <li key={d.key} className="text-xs border border-gray-100 rounded p-2">
                    <p className="font-medium text-gray-600 mb-1">{d.label}</p>
                    <p className="text-red-500 line-through mb-0.5">- {d.oldText || '（空）'}</p>
                    <p className="text-green-600">+ {d.revisedText || '（空）'}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
