import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createTemplate, listMyTemplates, updateTemplate } from '../api/templates';
import { getResume, listResumes } from '../api/resumes';
import type { ResumeData } from '../types/resume';
import { getErrorMessage } from '../api/client';
import { CanvasStage } from '../components/canvas/CanvasStage';
import { ComponentPalette } from '../components/canvas/ComponentPalette';
import { LayerPanel } from '../components/canvas/LayerPanel';
import { ElementInspector } from '../components/canvas/ElementInspector';
import { useCanvasEditorStore } from '../store/canvasEditorStore';
import { parseCanvasSchema } from '../types/canvasSchema';
import { parseSchema } from '../types/schema';
import { convertV1ToCanvas } from '../utils/canvasConvert';
import { SAMPLE_RESUME } from '../data/sampleResume';

/**
 * 自由画布模板设计器（R8-A4 P1）。
 *
 * 三栏：左「模板信息 + 组件库」· 中「画布」· 右「图层 + 属性」。
 * 全屏专注模式，不套 AppShell。
 */
export function CanvasBuilderPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const schema = useCanvasEditorStore((s) => s.schema);
  const zoom = useCanvasEditorStore((s) => s.zoom);
  const dirty = useCanvasEditorStore((s) => s.dirty);
  const canUndo = useCanvasEditorStore((s) => s.past.length > 0);
  const canRedo = useCanvasEditorStore((s) => s.future.length > 0);

  const [name, setName] = useState('我的模板');
  const [category, setCategory] = useState('');
  const [version, setVersion] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(!!id);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<'design' | 'runtime'>('design');
  const [converted, setConverted] = useState(false);
  const [dataSource, setDataSource] = useState<'sample' | 'mine'>('sample');
  const [myData, setMyData] = useState<ResumeData | null>(null);
  const [dataError, setDataError] = useState('');
  const lastNudge = useRef(0);
  const saveRef = useRef<() => void>(() => {});

  useEffect(() => {
    if (!id) {
      useCanvasEditorStore.getState().reset();
      setName('我的模板');
      setCategory('');
      setVersion(undefined);
      setConverted(false);
      return;
    }
    setLoading(true);
    listMyTemplates()
      .then((list) => {
        const t = list.find((x) => x.id === id);
        if (!t) {
          setError('模板不存在或无权访问');
          return;
        }
        setName(t.name);
        setCategory(t.category ?? '');
        setVersion(t.version);
        const canvas = parseCanvasSchema(t.schema);
        const v1 = canvas ? null : parseSchema(t.schema);
        if (canvas) {
          useCanvasEditorStore.getState().load(canvas);
        } else if (v1) {
          // 旧结构化模板：转换为画布初始稿，保存后升级为 v2
          useCanvasEditorStore.getState().load(convertV1ToCanvas(v1));
        } else {
          useCanvasEditorStore.getState().reset();
        }
        setConverted(!!v1);
      })
      .catch((err) => setError(getErrorMessage(err, '加载模板失败')))
      .finally(() => setLoading(false));
  }, [id]);

  // 预览数据源：切到「我的简历」时懒加载首份简历，用于验证真实内容下的推挤
  useEffect(() => {
    if (dataSource !== 'mine' || myData) return;
    listResumes(1, 1)
      .then((res) => {
        const first = res.records[0];
        if (!first) {
          setDataError('还没有简历，先用示例数据预览');
          setDataSource('sample');
          return;
        }
        return getResume(first.id).then((detail) => setMyData(detail as unknown as ResumeData));
      })
      .catch((err) => setDataError(getErrorMessage(err, '加载简历失败')));
  }, [dataSource, myData]);

  const handleSave = async () => {
    if (!name.trim()) {
      setError('请填写模板名称');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = {
        name: name.trim(),
        category: category.trim() || undefined,
        schema: JSON.stringify(schema),
        version,
      };
      if (id) {
        await updateTemplate(id, payload);
      } else {
        await createTemplate(payload);
      }
      useCanvasEditorStore.setState({ dirty: false });
      navigate('/?tab=templates', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, '保存失败'));
      setSaving(false);
    }
  };

  saveRef.current = handleSave;

  // 快捷键（P2）：撤销/重做、成组、复制、全选、保存、删除、方向键微调
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const tag = target?.tagName?.toLowerCase();
      if (tag === 'input' || tag === 'textarea' || tag === 'select' || target?.isContentEditable) return;

      const store = useCanvasEditorStore.getState();
      const mod = e.ctrlKey || e.metaKey;
      const key = e.key.toLowerCase();

      if (mod && key === 'z' && !e.shiftKey) {
        e.preventDefault();
        store.undo();
        return;
      }
      if ((mod && key === 'y') || (mod && e.shiftKey && key === 'z')) {
        e.preventDefault();
        store.redo();
        return;
      }
      if (mod && key === 'g') {
        e.preventDefault();
        if (e.shiftKey) store.ungroupSelected();
        else store.groupSelected();
        return;
      }
      if (mod && key === 'd') {
        e.preventDefault();
        store.duplicateSelected();
        return;
      }
      if (mod && key === 'a') {
        e.preventDefault();
        store.select(store.schema.elements.map((el) => el.id));
        return;
      }
      if (mod && key === 's') {
        e.preventDefault();
        saveRef.current();
        return;
      }
      if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        store.removeSelected();
        return;
      }
      if (e.key === 'Escape') {
        store.select([]);
        return;
      }
      if (e.key.startsWith('Arrow')) {
        e.preventDefault();
        const step = e.shiftKey ? 2 : 0.5;
        const dx = e.key === 'ArrowLeft' ? -step : e.key === 'ArrowRight' ? step : 0;
        const dy = e.key === 'ArrowUp' ? -step : e.key === 'ArrowDown' ? step : 0;
        const now = Date.now();
        if (now - lastNudge.current > 600) store.snapshot();
        lastNudge.current = now;
        store.nudgeSelected(dx, dy);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center text-gray-500">加载中...</div>;
  }

  const previewData = dataSource === 'mine' && myData ? myData : SAMPLE_RESUME;

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="h-14 bg-white border-b border-gray-200 flex items-center px-4 gap-3 shadow-sm shrink-0">
        <button onClick={() => navigate('/?tab=templates')} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md">
          ← 返回
        </button>
        <span className="font-semibold text-gray-800 text-sm">模板设计器</span>
        {converted && <span className="text-xs px-2 py-0.5 rounded bg-amber-50 text-amber-600">已由旧模板转换为画布，保存后升级</span>}
        {dirty && <span className="text-xs text-gray-400">未保存</span>}

        <div className="flex-1" />

        <div className="flex items-center gap-1 border border-gray-200 rounded-md">
          <button onClick={() => useCanvasEditorStore.getState().setZoom(zoom - 0.1)} className="px-2 py-1 text-sm text-gray-600 hover:bg-gray-100">−</button>
          <span className="text-xs text-gray-500 w-10 text-center">{Math.round(zoom * 100)}%</span>
          <button onClick={() => useCanvasEditorStore.getState().setZoom(zoom + 0.1)} className="px-2 py-1 text-sm text-gray-600 hover:bg-gray-100">＋</button>
        </div>

        <button
          onClick={() => useCanvasEditorStore.getState().undo()}
          disabled={!canUndo}
          className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md disabled:opacity-40"
        >
          撤销
        </button>
        <button
          onClick={() => useCanvasEditorStore.getState().redo()}
          disabled={!canRedo}
          className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md disabled:opacity-40"
        >
          重做
        </button>

        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
          {(['sample', 'mine'] as const).map((d) => (
            <button
              key={d}
              onClick={() => setDataSource(d)}
              className={`px-3 py-1 text-xs rounded-md ${dataSource === d ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500'}`}
            >
              {d === 'sample' ? '示例数据' : '我的简历'}
            </button>
          ))}
        </div>

        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
          {(['design', 'runtime'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`px-3 py-1 text-xs rounded-md ${mode === m ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500'}`}
            >
              {m === 'design' ? '设计态' : '运行态'}
            </button>
          ))}
        </div>

        <button
          onClick={handleSave}
          disabled={saving}
          className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
        >
          {saving ? '保存中...' : '保存模板'}
        </button>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="w-56 flex-shrink-0 bg-white border-r border-gray-200 overflow-y-auto p-3">
          <div className="mb-4">
            <label className="block text-xs text-gray-500 mb-1">模板名称</label>
            <input value={name} onChange={(e) => setName(e.target.value)} className="w-full border border-gray-200 rounded px-2 py-1.5 text-sm" />
            <label className="block text-xs text-gray-500 mb-1 mt-3">分类（可选）</label>
            <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="如：简约 / 商务" className="w-full border border-gray-200 rounded px-2 py-1.5 text-sm" />
          </div>
          <ComponentPalette />
          <div className="mt-4 border-t border-gray-100 pt-3">
            <p className="text-[10px] text-gray-400 leading-relaxed">
              快捷键：Ctrl+Z 撤销 · Ctrl+Shift+Z 重做 · Ctrl+G 成组 · Shift+Ctrl+G 解组 · Ctrl+D 复制 ·
              Ctrl+A 全选 · Delete 删除 · 方向键微调（Shift 加速）· Ctrl+滚轮外可框选
            </p>
          </div>
        </aside>

        <CanvasStage data={previewData} mode={mode} />

        <aside className="w-72 flex-shrink-0 bg-white border-l border-gray-200 overflow-y-auto p-3">
          <div className="mb-4">
            <LayerPanel />
          </div>
          <div className="border-t border-gray-100 pt-3">
            <ElementInspector />
          </div>
        </aside>
      </div>

      {(error || dataError) && (
        <p className="text-sm text-red-500 px-6 py-2 bg-red-50 shrink-0">{error || dataError}</p>
      )}
    </div>
  );
}
