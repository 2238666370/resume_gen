import { useCallback, useEffect, useState, type DragEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createTemplate, listMyTemplates, updateTemplate } from '../api/templates';
import { getErrorMessage } from '../api/client';
import { CustomSchemaTemplate } from '../components/templates/CustomSchemaTemplate';
import type { SchemaSection, TemplateSchema, TemplateTheme } from '../types/schema';
import { SAMPLE_RESUME } from '../data/sampleResume';

interface SectionDef {
  id: string;
  type: SchemaSection['type'];
  title: string;
  column?: 'left' | 'right';
}

const LEFT_TYPES = ['experience', 'internship', 'projects', 'custom'];

const PALETTE: { type: SchemaSection['type']; label: string; defaultTitle: string }[] = [
  { type: 'summary', label: '个人简介', defaultTitle: '个人简介' },
  { type: 'experience', label: '工作经历', defaultTitle: '工作经历' },
  { type: 'internship', label: '实习经历', defaultTitle: '实习经历' },
  { type: 'education', label: '教育经历', defaultTitle: '教育经历' },
  { type: 'skills', label: '专业技能', defaultTitle: '专业技能' },
  { type: 'projects', label: '项目经历', defaultTitle: '项目经历' },
  { type: 'certificates', label: '证书荣誉', defaultTitle: '证书荣誉' },
  { type: 'languages', label: '语言能力', defaultTitle: '语言能力' },
];

const SAMPLE = SAMPLE_RESUME;

let seq = 0;
const nextId = () => `sec_${++seq}_${Date.now()}`;

const colOf = (s: SectionDef): 'left' | 'right' => (s.column === 'right' ? 'right' : 'left');

/** 将 id 对应板块移动到目标栏位的 index 位置（纯函数，返回新数组）。 */
function placeSection(list: SectionDef[], id: string, col: 'left' | 'right', index: number): SectionDef[] {
  const item = list.find((s) => s.id === id);
  if (!item) return list;
  const others = list.filter((s) => s.id !== id);
  const moved: SectionDef = { ...item, column: col };
  const targetItems = others.filter((s) => colOf(s) === col);
  const clamped = Math.max(0, Math.min(index, targetItems.length));
  const newTarget = [...targetItems.slice(0, clamped), moved, ...targetItems.slice(clamped)];
  const otherCol = col === 'left' ? 'right' : 'left';
  const otherItems = others.filter((s) => colOf(s) === otherCol);
  return col === 'left' ? [...newTarget, ...otherItems] : [...otherItems, ...newTarget];
}

export function TemplateBuilderPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [name, setName] = useState('我的模板');
  const [category, setCategory] = useState('');
  const [columns, setColumns] = useState<1 | 2>(1);
  const [sections, setSections] = useState<SectionDef[]>([]);
  const [theme, setTheme] = useState<TemplateTheme>({
    headerStyle: 'left', titleStyle: 'bar', sectionStyle: 'plain', showAvatar: true, showContacts: true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(!!id);
  const [drag, setDrag] = useState<{ id: string; col: 'left' | 'right'; index: number } | null>(null);
  const [over, setOver] = useState<{ col: 'left' | 'right'; index: number } | null>(null);

  const loadExisting = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const list = await listMyTemplates();
      const t = list.find((x) => x.id === id);
      if (t && t.schema) {
        const schema = JSON.parse(t.schema) as TemplateSchema;
        setName(t.name);
        setCategory(t.category ?? '');
        setColumns(schema.columns === 2 ? 2 : 1);
        setTheme({ headerStyle: 'left', titleStyle: 'bar', sectionStyle: 'plain', showAvatar: true, showContacts: true, ...(schema.theme ?? {}) });
        setSections((schema.sections ?? []).map((s) => ({ id: s.id, type: s.type, title: s.title ?? '', column: s.column })));
      }
    } catch (err) {
      setError(getErrorMessage(err, '加载模板失败'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { loadExisting(); }, [loadExisting]);

  const schema: TemplateSchema = { schemaVersion: 1, layout: 'custom', columns, theme, sections };

  const addSection = (type: SchemaSection['type'], defaultTitle: string) => {
    setSections((prev) => [...prev, { id: nextId(), type, title: defaultTitle, column: 'left' }]);
  };

  const removeSection = (id: string) => {
    setSections((prev) => prev.filter((s) => s.id !== id));
  };

  const moveSection = (id: string, col: 'left' | 'right', index: number) => {
    setSections((prev) => placeSection(prev, id, col, index));
  };

  const moveBy = (id: string, dir: -1 | 1) => {
    setSections((prev) => {
      const idx = prev.findIndex((s) => s.id === id);
      if (idx < 0) return prev;
      const col = colOf(prev[idx]);
      const colItems = prev.filter((s) => colOf(s) === col);
      const pos = colItems.findIndex((s) => s.id === id);
      const target = pos + dir;
      if (target < 0 || target >= colItems.length) return prev;
      return placeSection(prev, id, col, target);
    });
  };

  const switchColumns = (c: 1 | 2) => {
    setColumns(c);
    setSections((prev) => prev.map((s) => {
      if (c === 1) return { ...s, column: 'left' as const };
      const col = s.column ?? (LEFT_TYPES.includes(s.type) ? 'left' : 'right');
      return { ...s, column: col as 'left' | 'right' };
    }));
  };

  const handleDragStart = (id: string, col: 'left' | 'right', index: number) => setDrag({ id, col, index });
  const handleDragOver = (e: DragEvent<HTMLDivElement>, col: 'left' | 'right', index: number) => { e.preventDefault(); setOver({ col, index }); };
  const handleDrop = (col: 'left' | 'right', index: number) => {
    if (drag) moveSection(drag.id, col, index);
    setDrag(null);
    setOver(null);
  };
  const handleDragEnd = () => { setDrag(null); setOver(null); };

  const setTitle = (id: string, title: string) => {
    setSections((prev) => prev.map((s) => (s.id === id ? { ...s, title } : s)));
  };

  const handleSave = async () => {
    if (!name.trim()) {
      setError('请填写模板名称');
      return;
    }
    if (sections.length === 0) {
      setError('请至少添加一个板块');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = { name: name.trim(), category: category.trim() || undefined, schema: JSON.stringify(schema) };
      if (id) {
        await updateTemplate(id, payload);
      } else {
        await createTemplate(payload);
      }
      navigate('/?tab=templates', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, '保存失败'));
      setSaving(false);
    }
  };

  const leftItems = sections.filter((s) => colOf(s) === 'left');
  const rightItems = sections.filter((s) => colOf(s) === 'right');

  const renderItem = (s: SectionDef, col: 'left' | 'right', index: number) => {
    const isDragging = drag?.id === s.id;
    const isOver = !!over && over.col === col && over.index === index && !!drag && drag.id !== s.id;
    return (
      <div
        key={s.id}
        draggable
        onDragStart={() => handleDragStart(s.id, col, index)}
        onDragOver={(e) => handleDragOver(e, col, index)}
        onDrop={() => handleDrop(col, index)}
        onDragEnd={handleDragEnd}
        className={`border rounded-md p-2 cursor-grab transition-colors ${
          isDragging ? 'opacity-40 border-blue-300'
            : isOver ? 'border-blue-400 bg-blue-50'
              : 'border-gray-200'
        }`}
      >
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-gray-300 select-none">⠿</span>
          <input
            value={s.title}
            onChange={(e) => setTitle(s.id, e.target.value)}
            className="flex-1 min-w-0 text-sm px-2 py-1 border border-gray-100 rounded"
          />
          <button onClick={() => moveBy(s.id, -1)} className="text-xs text-gray-400 hover:text-gray-600 px-1">↑</button>
          <button onClick={() => moveBy(s.id, 1)} className="text-xs text-gray-400 hover:text-gray-600 px-1">↓</button>
          <button onClick={() => removeSection(s.id)} className="text-xs text-red-400 hover:text-red-600 px-1">✕</button>
        </div>
        <p className="text-[10px] text-gray-400 mt-1">{s.type}</p>
      </div>
    );
  };

  const emptyDrop = (col: 'left' | 'right') => (
    <div
      onDragOver={(e) => handleDragOver(e, col, 0)}
      onDrop={() => handleDrop(col, 0)}
      className={`text-xs text-gray-300 py-6 text-center border border-dashed rounded-md ${
        over && over.col === col && drag ? 'border-blue-400 bg-blue-50' : 'border-gray-200'
      }`}
    >
      拖拽板块到这里
    </div>
  );

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center text-gray-500">加载中...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="h-14 bg-white border-b border-gray-200 flex items-center px-6 gap-3 shadow-sm">
        <button onClick={() => navigate('/?tab=templates')} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md">← 返回</button>
        <span className="font-semibold text-gray-800 text-sm">模板编辑器</span>
        <div className="flex-1" />
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
        >
          {saving ? '保存中...' : '保存模板'}
        </button>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* 左：组件面板 */}
        <aside className="w-52 flex-shrink-0 bg-white border-r border-gray-200 overflow-y-auto p-3">
          <p className="text-xs text-gray-400 mb-2">点击添加板块</p>
          <div className="flex flex-col gap-1.5">
            {PALETTE.map((p) => (
              <button
                key={p.type}
                onClick={() => addSection(p.type, p.defaultTitle)}
                className="text-left px-3 py-2 text-sm text-gray-700 border border-gray-200 rounded-md hover:border-blue-400 hover:bg-blue-50 transition-colors"
              >
                {p.label}
              </button>
            ))}
          </div>
        </aside>

        {/* 中：画布（板块列表 + 预览） */}
        <main className="flex-1 overflow-y-auto p-5 flex gap-5">
          <div className="w-72 flex-shrink-0">
            <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4">
              <label className="block text-xs text-gray-500 mb-1">模板名称</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className="w-full border border-gray-200 rounded px-3 py-2 text-sm" />
              <label className="block text-xs text-gray-500 mb-1 mt-3">分类（可选）</label>
              <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="如：简约 / 商务 / 创意" className="w-full border border-gray-200 rounded px-3 py-2 text-sm" />
              <label className="block text-xs text-gray-500 mb-1 mt-3">布局</label>
              <div className="flex gap-2">
                {([1, 2] as const).map((c) => (
                  <button key={c} onClick={() => switchColumns(c)}
                    className={`flex-1 px-3 py-2 text-sm rounded-md border ${columns === c ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-600'}`}>
                    {c === 1 ? '单栏' : '双栏'}
                  </button>
                ))}
              </div>

              <label className="block text-xs text-gray-500 mb-1 mt-3">标题纹饰</label>
              <div className="grid grid-cols-4 gap-1.5">
                {([['plain', '纯文字'], ['bar', '竖条'], ['underline', '下划线'], ['capsule', '胶囊']] as const).map(([v, l]) => (
                  <button key={v} onClick={() => setTheme({ ...theme, titleStyle: v })}
                    className={`px-2 py-1.5 text-xs rounded-md border ${theme.titleStyle === v ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-600'}`}>
                    {l}
                  </button>
                ))}
              </div>

              <label className="block text-xs text-gray-500 mb-1 mt-3">头部对齐</label>
              <div className="flex gap-2">
                {([['left', '左对齐'], ['center', '居中']] as const).map(([v, l]) => (
                  <button key={v} onClick={() => setTheme({ ...theme, headerStyle: v })}
                    className={`flex-1 px-3 py-2 text-sm rounded-md border ${theme.headerStyle === v ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-600'}`}>
                    {l}
                  </button>
                ))}
              </div>

              <label className="block text-xs text-gray-500 mb-1 mt-3">板块样式</label>
              <div className="flex gap-2">
                {([['plain', '朴素'], ['card', '卡片']] as const).map(([v, l]) => (
                  <button key={v} onClick={() => setTheme({ ...theme, sectionStyle: v })}
                    className={`flex-1 px-3 py-2 text-sm rounded-md border ${theme.sectionStyle === v ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-200 text-gray-600'}`}>
                    {l}
                  </button>
                ))}
              </div>

              <div className="flex gap-4 mt-3">
                <label className="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer">
                  <input type="checkbox" checked={theme.showAvatar !== false} onChange={(e) => setTheme({ ...theme, showAvatar: e.target.checked })} />
                  头像
                </label>
                <label className="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer">
                  <input type="checkbox" checked={theme.showContacts !== false} onChange={(e) => setTheme({ ...theme, showContacts: e.target.checked })} />
                  联系方式
                </label>
              </div>
            </div>

            <div className="bg-white rounded-xl border border-gray-200 p-4">
              <p className="text-xs text-gray-400 mb-2">
                已添加板块（{sections.length}）· {columns === 1 ? '拖拽排序' : '拖拽到左/右栏'}
              </p>
              {sections.length === 0 && <p className="text-xs text-gray-300 py-4 text-center">暂无板块</p>}
              {columns === 1 ? (
                <div className="flex flex-col gap-1.5">
                  {sections.map((s, i) => renderItem(s, 'left', i))}
                </div>
              ) : (
                <div className="flex gap-2">
                  <div className="flex-1 flex flex-col gap-1.5">
                    <p className="text-[10px] text-gray-400">左栏</p>
                    {leftItems.length === 0 ? emptyDrop('left') : leftItems.map((s, i) => renderItem(s, 'left', i))}
                  </div>
                  <div className="flex-1 flex flex-col gap-1.5">
                    <p className="text-[10px] text-gray-400">右栏</p>
                    {rightItems.length === 0 ? emptyDrop('right') : rightItems.map((s, i) => renderItem(s, 'right', i))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* 右：实时预览 */}
          <div className="flex-1 flex justify-center">
            <div className="w-full max-w-[794px]">
              <div className="bg-white shadow-2xl rounded-sm overflow-hidden" style={{ minHeight: '1123px' }}>
                <CustomSchemaTemplate data={SAMPLE} schema={schema} />
              </div>
            </div>
          </div>
        </main>
      </div>

      {error && <p className="text-sm text-red-500 px-6 py-2 bg-red-50">{error}</p>}
    </div>
  );
}
