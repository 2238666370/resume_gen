import { useEffect, useRef, useState } from 'react';
import { aiScore, aiScoreAsync, streamTaskResult } from '../api/ai';
import type { ResumeScoreResult } from '../api/ai';
import { AI_ASYNC_ENABLED, getErrorMessage } from '../api/client';
import { ProgressBar } from './ProgressBar';

interface Props {
  resumeId: string;
  onClose: () => void;
}

/**
 * AI 简历评分 + JD 匹配度（ATS 风格 + 可解释建议）。
 * 可选填写 JD 后重新评分，得到匹配百分比、命中技能与缺失技能。
 */
export function AiScoreModal({ resumeId, onClose }: Props) {
  const [result, setResult] = useState<ResumeScoreResult | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [jd, setJd] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const cancelRef = useRef<(() => void) | undefined>(undefined);

  // 组件卸载时断开可能仍在进行的 SSE 连接
  useEffect(() => () => cancelRef.current?.(), []);

  const run = (role?: string, jdText?: string) => {
    setLoading(true);
    setError('');
    const payload = { resumeId, targetRole: role, jd: jdText };
    if (AI_ASYNC_ENABLED) {
      aiScoreAsync(payload)
        .then((sub) => {
          cancelRef.current = streamTaskResult(
            sub.taskId,
            (task) => {
              if (task.status === 'SUCCESS' && task.result) {
                setResult(task.result as ResumeScoreResult);
              } else {
                setError(task.error || 'AI 评分失败');
              }
              setLoading(false);
            },
            () => {
              setError('结果推送中断，请重试');
              setLoading(false);
            },
          );
        })
        .catch((err) => {
          setError(getErrorMessage(err, 'AI 评分失败'));
          setLoading(false);
        });
    } else {
      aiScore(payload)
        .then((r) => {
          setResult(r);
          setLoading(false);
        })
        .catch((err) => {
          setError(getErrorMessage(err, 'AI 评分失败'));
          setLoading(false);
        });
    }
  };

  const barColor = (s: number) =>
    s >= 80 ? 'bg-green-500' : s >= 60 ? 'bg-blue-500' : s >= 40 ? 'bg-orange-400' : 'bg-red-400';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl w-[600px] max-w-[92vw] max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h3 className="font-medium text-gray-800">AI 简历评分</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-5">
          {/* JD 输入 */}
          <div className="mb-4 p-3 border border-gray-100 rounded-lg bg-gray-50">
            <p className="text-xs text-gray-500 mb-2">先填写目标岗位与 JD 原文（可留空），再开始评分</p>
            <input
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value)}
              placeholder="目标岗位（如 Java 后端工程师）"
              className="w-full mb-2 border border-gray-200 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <textarea
              value={jd}
              onChange={(e) => setJd(e.target.value)}
              placeholder="岗位 JD（技能要求 / 职责 / 经验年限等）"
              rows={3}
              className="w-full border border-gray-200 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
            <button
              onClick={() => run(targetRole.trim() || undefined, jd.trim() || undefined)}
              disabled={loading}
              className="mt-2 px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
            >
              {loading ? '评分中...' : '开始评分'}
            </button>
          </div>

          {error && <p className="text-sm text-red-500 mb-3">{error}</p>}
          {!error && loading && !result && <ProgressBar label="AI 正在评分，请稍候..." className="py-4" />}

          {result && (
            <div>
              {/* 总分 + 匹配度 */}
              <div className="flex items-center gap-4 mb-5">
                <div className="flex-1 text-center p-4 rounded-lg bg-blue-50">
                  <p className="text-3xl font-bold text-blue-600">{result.totalScore}</p>
                  <p className="text-xs text-gray-500 mt-1">综合评分（满分 100）</p>
                </div>
                {result.matchedPercent > 0 && (
                  <div className="flex-1 text-center p-4 rounded-lg bg-green-50">
                    <p className="text-3xl font-bold text-green-600">{result.matchedPercent}%</p>
                    <p className="text-xs text-gray-500 mt-1">JD 匹配度</p>
                  </div>
                )}
              </div>

              {/* 维度得分条 */}
              <p className="text-sm font-medium text-gray-700 mb-2">分维度得分</p>
              <div className="space-y-2 mb-5">
                {result.dimensions.map((d) => (
                  <div key={d.key} className="flex items-center gap-3">
                    <span className="w-20 text-xs text-gray-600 shrink-0">{d.name}</span>
                    <div className="flex-1 h-2.5 bg-gray-100 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${barColor(d.score)}`} style={{ width: `${d.score}%` }} />
                    </div>
                    <span className="w-8 text-right text-xs font-medium text-gray-700">{d.score}</span>
                  </div>
                ))}
              </div>

              {/* JD 命中 / 缺失技能 */}
              {(result.skillHits.length > 0 || result.missingSkills.length > 0) && (
                <div className="mb-4">
                  {result.skillHits.length > 0 && (
                    <div className="mb-2">
                      <p className="text-xs font-medium text-gray-700 mb-1">命中技能</p>
                      <div className="flex flex-wrap gap-1.5">
                        {result.skillHits.map((s) => (
                          <span key={s} className="text-xs px-2 py-0.5 rounded bg-green-100 text-green-700">{s}</span>
                        ))}
                      </div>
                    </div>
                  )}
                  {result.missingSkills.length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-gray-700 mb-1">缺失技能</p>
                      <div className="flex flex-wrap gap-1.5">
                        {result.missingSkills.map((s) => (
                          <span key={s} className="text-xs px-2 py-0.5 rounded bg-red-100 text-red-600">{s}</span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* 建议 */}
              {result.suggestions.length > 0 && (
                <div>
                  <p className="text-sm font-medium text-gray-700 mb-2">改进建议</p>
                  <ul className="space-y-1.5">
                    {result.suggestions.map((s, i) => (
                      <li key={i} className="text-sm text-gray-600 flex gap-2">
                        <span className="text-blue-500 shrink-0">•</span>
                        <span>{s}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              <p className="text-[11px] text-gray-400 mt-4">评分结果仅供参考，不应作为唯一录用依据。</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
