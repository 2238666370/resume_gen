import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getResume, updateResume } from '../api/resumes';
import { Toolbar } from '../components/Toolbar';
import { SectionsPanel } from '../components/editor/SectionsPanel';
import { ResumeTemplate } from '../components/templates/ResumeTemplate';
import { useResumeStore } from '../store/resumeStore';

export function EditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const resumeRef = useRef<HTMLDivElement>(null);
  const { data, loadData } = useResumeStore();

  const versionRef = useRef(0);
  const hydratedRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const idRef = useRef(id ?? '');
  idRef.current = id ?? '';

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    hydratedRef.current = false;
    getResume(id)
      .then((d) => {
        if (cancelled) return;
        versionRef.current = d.version;
        const { id: _id, version: _version, updatedAt: _updatedAt, ...content } = d;
        loadData(content);
        hydratedRef.current = true;
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        if ((err as { code?: number })?.code === 404 || (err as { code?: number })?.code === 401) {
          navigate('/', { replace: true });
          return;
        }
        setError((err as Error)?.message || '加载失败');
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, loadData, navigate]);

  useEffect(() => {
    const unsub = useResumeStore.subscribe((state, prev) => {
      if (!hydratedRef.current) return;
      if (state.data === prev.data) return;
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        updateResume(idRef.current, useResumeStore.getState().data, versionRef.current)
          .then((u) => {
            versionRef.current = u.version;
          })
          .catch((err) => console.error('自动保存失败', err));
      }, 800);
    });
    return () => {
      unsub();
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
        updateResume(idRef.current, useResumeStore.getState().data, versionRef.current)
          .catch((err) => console.error('保存失败', err));
      }
    };
  }, []);

  const renderTemplate = () => <ResumeTemplate data={data} />;

  if (loading) {
    return <div className="h-screen flex items-center justify-center text-gray-500">加载中...</div>;
  }

  if (error) {
    return (
      <div className="h-screen flex flex-col items-center justify-center gap-4 text-gray-600">
        <p>{error}</p>
        <button onClick={() => navigate('/')} className="px-4 py-2 bg-blue-600 text-white rounded-md">返回主页</button>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50 overflow-hidden">
      <Toolbar resumeRef={resumeRef} />

      <div className="flex flex-1 overflow-hidden">
        <aside className="w-80 flex-shrink-0 bg-white border-r border-gray-200 overflow-y-auto">
          <div className="p-3">
            <p className="text-xs text-gray-400 mb-3 text-center">拖拽左侧 ⠿ 可调整模块顺序，开关控制显示</p>
            <SectionsPanel />
          </div>
        </aside>

        <main className="flex-1 overflow-auto bg-gray-100 flex justify-center py-8 px-4">
          <div className="w-full max-w-[794px]">
            <div
              className="bg-white shadow-2xl rounded-sm"
              style={{ minHeight: '1123px' }}
              ref={resumeRef}
            >
              {renderTemplate()}
            </div>
            <p className="text-center text-xs text-gray-400 mt-4">
              内容实时预览 · 自动保存至云端
            </p>
          </div>
        </main>
      </div>
    </div>
  );
}