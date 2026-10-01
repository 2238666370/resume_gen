import { useCallback, useEffect, useRef, useState } from 'react';
import { listResumes } from '../api/resumes';
import { deleteInterviewSet, generateInterview, generateInterviewAsync, listInterviewSets, renameInterviewSet, streamTaskResult } from '../api/ai';
import type { InterviewSet } from '../api/ai';
import { AI_ASYNC_ENABLED, getErrorMessage } from '../api/client';
import type { ResumeListItem } from '../api/types';
import { ProgressBar } from './ProgressBar';

/**
 * 面试准备面板（F1）：从 InterviewPage 抽取，供「AI 助手 · 面试准备」Tab 复用，不含页面壳。
 */
export function InterviewPanel() {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [resumeId, setResumeId] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [jd, setJd] = useState('');
  const [sets, setSets] = useState<InterviewSet[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftTitle, setDraftTitle] = useState('');
  const [error, setError] = useState('');

  const cancelStream = useRef<(() => void) | null>(null);
  useEffect(() => () => cancelStream.current?.(), []);

  const loadResumes = useCallback(async () => {
    try {
      const r = await listResumes(1, 100);
      setResumes(r.records);
      if (r.records.length > 0) {
        setResumeId((cur) => (cur ? cur : r.records[0].id));
      }
    } catch (err) {
      console.error('加载简历列表失败', err);
    }
  }, []);

  const loadSets = useCallback(async () => {
    setLoading(true);
    try {
      setSets(await listInterviewSets());
    } catch (err) {
      setError(getErrorMessage(err, '加载题集失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadResumes(); }, [loadResumes]);
  useEffect(() => { loadSets(); }, [loadSets]);

  const handleGenerate = async () => {
    if (!resumeId) {
      setError('请先选择简历');
      return;
    }
    setGenerating(true);
    setError('');

    if (AI_ASYNC_ENABLED) {
      try {
        const sub = await generateInterviewAsync({ resumeId, targetRole, jd });
        cancelStream.current = streamTaskResult(
          sub.taskId,
          (task) => {
            setGenerating(false);
            if (task.status === 'SUCCESS' && task.result) {
              const s = task.result as InterviewSet;
              setSets((prev) => [s, ...prev]);
              setExpanded(s.id);
            } else {
              setError(task.error || '生成失败');
            }
          },
          () => {
            setGenerating(false);
            setError('结果推送中断，请稍后刷新题集');
          },
        );
      } catch (err) {
        setGenerating(false);
        setError(getErrorMessage(err, '生成失败'));
      }
      return;
    }

    try {
      const s = await generateInterview({ resumeId, targetRole, jd });
      setSets((prev) => [s, ...prev]);
      setExpanded(s.id);
    } catch (err) {
      setError(getErrorMessage(err, '生成失败'));
    } finally {
      setGenerating(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('确定删除该题集？')) return;
    try {
      await deleteInterviewSet(id);
      setSets((prev) => prev.filter((s) => s.id !== id));
    } catch (err) {
      setError(getErrorMessage(err, '删除失败'));
    }
  };

  const startRename = (s: InterviewSet) => {
    setEditingId(s.id);
    setDraftTitle(s.title);
  };

  const saveRename = async (id: string) => {
    const title = draftTitle.trim();
    if (!title) {
      setEditingId(null);
      return;
    }
    try {
      const updated = await renameInterviewSet(id, title);
      setSets((prev) => prev.map((s) => (s.id === id ? updated : s)));
    } catch (err) {
      setError(getErrorMessage(err, '重命名失败'));
    } finally {
      setEditingId(null);
    }
  };

  return (
    <>
      <div className="bg-white rounded-xl border border-gray-200 p-5 mb-6">
        <h2 className="font-medium text-gray-800 mb-4">定向生成面试题库</h2>
        <div className="grid grid-cols-1 gap-3 mb-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">选择简历</label>
            <select
              value={resumeId}
              onChange={(e) => setResumeId(e.target.value)}
              className="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
            >
              {resumes.length === 0 && <option value="">暂无简历，请先到工作台创建</option>}
              {resumes.map((r) => (
                <option key={r.id} value={r.id}>{r.title}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">目标岗位（可选）</label>
            <input
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value)}
              placeholder="如：Java 后端工程师"
              className="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">岗位 JD（可选）</label>
            <textarea
              value={jd}
              onChange={(e) => setJd(e.target.value)}
              rows={3}
              placeholder="粘贴岗位 JD，出题更定向"
              className="w-full border border-gray-200 rounded px-3 py-2 text-sm resize-none focus:outline-none focus:border-blue-400"
            />
          </div>
        </div>
        {error && <p className="text-sm text-red-500 mb-3">{error}</p>}
        <button
          onClick={handleGenerate}
          disabled={generating || !resumeId}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
        >
          {generating ? 'AI 生成中...' : '生成题库'}
        </button>
        {generating && <ProgressBar className="mt-3" label="AI 正在生成题库，请稍候..." />}
      </div>

      <h2 className="font-medium text-gray-800 mb-3">我的题集</h2>
      {loading ? (
        <div className="text-center text-gray-400 py-10">加载中...</div>
      ) : sets.length === 0 ? (
        <div className="text-center text-gray-400 py-10">暂无题集，选择简历生成一份吧</div>
      ) : (
        <div className="flex flex-col gap-3">
          {sets.map((s) => (
            <div key={s.id} className="bg-white rounded-xl border border-gray-200 p-5">
              <div className="flex items-center justify-between gap-3">
                {editingId === s.id ? (
                  <div className="flex-1 min-w-0 flex items-center gap-2">
                    <input
                      autoFocus
                      value={draftTitle}
                      onChange={(e) => setDraftTitle(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') saveRename(s.id);
                        else if (e.key === 'Escape') setEditingId(null);
                      }}
                      className="flex-1 min-w-0 border border-blue-300 rounded px-2 py-1 text-sm font-medium focus:outline-none"
                    />
                    <button onClick={() => saveRename(s.id)} className="text-xs text-blue-600 flex-shrink-0">保存</button>
                    <button onClick={() => setEditingId(null)} className="text-xs text-gray-400 flex-shrink-0">取消</button>
                  </div>
                ) : (
                  <button
                    onClick={() => setExpanded(expanded === s.id ? null : s.id)}
                    className="text-left min-w-0 flex-1"
                  >
                    <p className="font-medium text-gray-800 truncate">{s.title}</p>
                    <p className="text-xs text-gray-400 mt-1">
                      {s.createdAt ? `${s.createdAt} · ` : ''}{s.questions.length} 题
                    </p>
                  </button>
                )}
                {editingId !== s.id && (
                  <button
                    onClick={() => startRename(s)}
                    className="text-xs text-gray-400 hover:text-blue-600 flex-shrink-0"
                  >
                    重命名
                  </button>
                )}
                <button
                  onClick={() => handleDelete(s.id)}
                  className="text-xs text-red-400 hover:text-red-600 flex-shrink-0"
                >
                  删除
                </button>
              </div>
              {expanded === s.id && (
                <div className="mt-4 flex flex-col gap-3">
                  {s.questions.map((q, i) => (
                    <div key={i} className="border border-gray-100 rounded-lg p-3">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">{q.category}</span>
                        <span className="text-sm font-medium text-gray-800">Q{i + 1}. {q.question}</span>
                      </div>
                      <p className="text-xs text-gray-600 mt-1">
                        <span className="font-medium">参考答案：</span>{q.answer || '—'}
                      </p>
                      <p className="text-xs text-gray-400 mt-1">
                        <span className="font-medium">考察点：</span>{q.tips || '—'}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  );
}
