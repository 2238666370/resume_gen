import type { ReactNode } from 'react';
import type { CanvasElement, CanvasListElement } from '../../types/canvasSchema';
import { ITEM_BIND_PATHS, ROOT_BIND_PATHS } from '../../types/canvasSchema';
import { flattenElements, useCanvasEditorStore } from '../../store/canvasEditorStore';

const ARRAY_PATHS = Object.keys(ITEM_BIND_PATHS);
const SCALAR_PATHS = (ROOT_BIND_PATHS as readonly string[]).filter((p) => !ARRAY_PATHS.includes(p));

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex items-center gap-2 text-xs text-gray-500">
      <span className="w-14 flex-shrink-0">{label}</span>
      <span className="flex-1 min-w-0">{children}</span>
    </label>
  );
}

const inputCls = 'w-full border border-gray-200 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:border-blue-400';

/** 右侧属性面板（R8-A4 P1）：位置尺寸 / 样式 / 数据绑定 / 列表配置。 */
export function ElementInspector() {
  const schema = useCanvasEditorStore((s) => s.schema);
  const selectedIds = useCanvasEditorStore((s) => s.selectedIds);
  const store = useCanvasEditorStore;

  if (selectedIds.length === 0) {
    return <p className="text-xs text-gray-400">选中画布上的元素以编辑属性。</p>;
  }

  const flat = flattenElements(schema.elements);

  if (selectedIds.length > 1) {
    const align = store.getState().alignSelected;
    const distribute = store.getState().distributeSelected;
    const alignBtns: { label: string; mode: Parameters<typeof align>[0] }[] = [
      { label: '左', mode: 'left' },
      { label: '水平居中', mode: 'centerX' },
      { label: '右', mode: 'right' },
      { label: '上', mode: 'top' },
      { label: '垂直居中', mode: 'centerY' },
      { label: '下', mode: 'bottom' },
    ];
    return (
      <div className="flex flex-col gap-3">
        <p className="text-xs text-gray-500">已选中 {selectedIds.length} 个元素</p>

        <div>
          <p className="text-xs text-gray-400 mb-1.5">对齐</p>
          <div className="grid grid-cols-3 gap-1">
            {alignBtns.map((b) => (
              <button
                key={b.mode}
                onClick={() => align(b.mode)}
                className="px-1 py-1.5 text-[11px] border border-gray-200 rounded-md hover:bg-gray-50"
              >
                {b.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="text-xs text-gray-400 mb-1.5">分布（≥3 个元素）</p>
          <div className="grid grid-cols-2 gap-1">
            <button
              onClick={() => distribute('x')}
              disabled={selectedIds.length < 3}
              className="px-1 py-1.5 text-[11px] border border-gray-200 rounded-md hover:bg-gray-50 disabled:opacity-40"
            >
              水平等距
            </button>
            <button
              onClick={() => distribute('y')}
              disabled={selectedIds.length < 3}
              className="px-1 py-1.5 text-[11px] border border-gray-200 rounded-md hover:bg-gray-50 disabled:opacity-40"
            >
              垂直等距
            </button>
          </div>
        </div>

        <div className="border-t border-gray-100 pt-3 flex flex-col gap-2">
          <button onClick={() => store.getState().groupSelected()} className="px-3 py-1.5 text-xs bg-blue-600 text-white rounded-md hover:bg-blue-700">成组</button>
          <button onClick={() => store.getState().duplicateSelected()} className="px-3 py-1.5 text-xs border border-gray-200 rounded-md hover:bg-gray-50">复制</button>
          <button onClick={() => store.getState().removeSelected()} className="px-3 py-1.5 text-xs text-red-500 border border-red-200 rounded-md hover:bg-red-50">删除</button>
        </div>
      </div>
    );
  }

  const id = selectedIds[0];
  const flatEntry = flat.find((f) => f.el.id === id);
  const el = flatEntry?.el;
  if (!el) return <p className="text-xs text-gray-400">元素不存在。</p>;
  const isItemTemplate = !!flatEntry?.inListTemplate;
  const parentList = isItemTemplate ? flat.find((f) => f.el.id === flatEntry?.parentId) : undefined;

  const set = (patch: Partial<CanvasElement>) => store.getState().updateElement(id, patch);
  const setStyle = (patch: Record<string, unknown>) => store.getState().patchStyle(id, patch as never);
  const style = el.style ?? {};

  return (
    <div className="flex flex-col gap-3">
      {isItemTemplate && (
        <p className="text-[10px] text-amber-600 bg-amber-50 rounded px-2 py-1">
          位于「{parentList?.el.name ?? '列表'}」的条目模板内，坐标相对单个条目
        </p>
      )}

      <div className="flex items-center justify-between">
        <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">{el.type}</span>
        <div className="flex gap-1">
          {el.type === 'group' && (
            <button onClick={() => store.getState().ungroupSelected()} className="text-xs text-gray-500 hover:text-gray-800">解组</button>
          )}
          <button onClick={() => store.getState().duplicateSelected()} className="text-xs text-gray-500 hover:text-gray-800">复制</button>
          <button onClick={() => store.getState().removeSelected()} className="text-xs text-red-400 hover:text-red-600">删除</button>
        </div>
      </div>

      <Row label="图层名">
        <input className={inputCls} value={el.name ?? ''} onChange={(e) => set({ name: e.target.value })} />
      </Row>

      <div className="grid grid-cols-2 gap-2">
        {(['x', 'y', 'w', 'h'] as const).map((k) => (
          <Row key={k} label={`${k} (mm)`}>
            <input
              type="number" step={0.5} min={0} className={inputCls}
              value={Math.round(el[k] * 10) / 10}
              onChange={(e) => set({ [k]: Number(e.target.value) } as Partial<CanvasElement>)}
            />
          </Row>
        ))}
      </div>

      <div className="border-t border-gray-100 pt-3 flex flex-col gap-2">
        <p className="text-xs text-gray-400">样式</p>
        <Row label="字号 (pt)">
          <input type="number" step={0.5} min={6} max={72} className={inputCls}
            value={style.fontSize ?? 10.5}
            onChange={(e) => setStyle({ fontSize: Number(e.target.value) })} />
        </Row>
        <Row label="字重">
          <select className={inputCls} value={style.fontWeight ?? 400}
            onChange={(e) => setStyle({ fontWeight: Number(e.target.value) })}>
            {[300, 400, 500, 600, 700].map((w) => <option key={w} value={w}>{w}</option>)}
          </select>
        </Row>
        <Row label="颜色">
          <input type="color" className="w-full h-7 border border-gray-200 rounded"
            value={style.color ?? '#111827'}
            onChange={(e) => setStyle({ color: e.target.value })} />
        </Row>
        <Row label="对齐">
          <select className={inputCls} value={style.textAlign ?? 'left'}
            onChange={(e) => setStyle({ textAlign: e.target.value })}>
            <option value="left">左</option>
            <option value="center">中</option>
            <option value="right">右</option>
          </select>
        </Row>
        <Row label="背景">
          <input type="color" className="w-full h-7 border border-gray-200 rounded"
            value={style.background && style.background.startsWith('#') ? style.background : '#ffffff'}
            onChange={(e) => setStyle({ background: e.target.value })} />
        </Row>
      </div>

      <div className="border-t border-gray-100 pt-3 flex flex-col gap-2">
        <p className="text-xs text-gray-400">内容</p>

        {el.type === 'text' && (
          <textarea className={inputCls} rows={3} value={el.value}
            onChange={(e) => set({ value: e.target.value } as Partial<CanvasElement>)} />
        )}

        {el.type === 'heading' && (
          <>
            <Row label="标题"><input className={inputCls} value={el.value}
              onChange={(e) => set({ value: e.target.value } as Partial<CanvasElement>)} /></Row>
            <Row label="纹饰">
              <select className={inputCls} value={el.decoration ?? 'bar'}
                onChange={(e) => set({ decoration: e.target.value } as Partial<CanvasElement>)}>
                {['plain', 'bar', 'underline', 'capsule'].map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
            </Row>
          </>
        )}

        {el.type === 'field' && (
          <>
            <Row label="绑定">
              <select className={inputCls} value={el.bind.path}
                onChange={(e) => set({ bind: { ...el.bind, path: e.target.value } } as Partial<CanvasElement>)}>
                {SCALAR_PATHS.map((p) => <option key={p} value={p}>{p}</option>)}
              </select>
            </Row>
            <Row label="兜底">
              <input className={inputCls} value={el.bind.fallback ?? ''}
                onChange={(e) => set({ bind: { ...el.bind, fallback: e.target.value } } as Partial<CanvasElement>)} />
            </Row>
          </>
        )}

        {el.type === 'list' && (
          <>
            <Row label="数据源">
              <select className={inputCls} value={(el as CanvasListElement).bind.path}
                onChange={(e) => set({ bind: { path: e.target.value } } as Partial<CanvasElement>)}>
                {ARRAY_PATHS.map((p) => <option key={p} value={p}>{p}</option>)}
              </select>
            </Row>
            <Row label="条目间距">
              <input type="number" step={0.5} min={0} className={inputCls}
                value={(el as CanvasListElement).itemGap ?? 0}
                onChange={(e) => set({ itemGap: Number(e.target.value) } as Partial<CanvasElement>)} />
            </Row>
            <Row label="分隔线">
              <input type="checkbox" checked={!!(el as CanvasListElement).divider}
                onChange={(e) => set({ divider: e.target.checked } as Partial<CanvasElement>)} />
            </Row>
            <p className="text-[10px] text-gray-400">条目内元素的编辑将在 P2 提供。</p>
          </>
        )}

        {el.type === 'image' && (
          <>
            <Row label="来源">
              <select className={inputCls} value={el.source}
                onChange={(e) => set({ source: e.target.value } as Partial<CanvasElement>)}>
                <option value="avatar">头像字段</option>
                <option value="url">外链地址</option>
              </select>
            </Row>
            {el.source === 'url' && (
              <Row label="地址">
                <input className={inputCls} placeholder="https://..." value={el.url ?? ''}
                  onChange={(e) => set({ url: e.target.value } as Partial<CanvasElement>)} />
              </Row>
            )}
            <p className="text-[10px] text-gray-400">不支持本地图片上传。</p>
          </>
        )}

        {el.type === 'shape' && (
          <>
            <Row label="形状">
              <select className={inputCls} value={el.shape}
                onChange={(e) => set({ shape: e.target.value } as Partial<CanvasElement>)}>
                {['rect', 'ellipse', 'line'].map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </Row>
            <Row label="填充">
              <input type="color" className="w-full h-7 border border-gray-200 rounded"
                value={el.fill && el.fill.startsWith('#') ? el.fill : '#2563eb'}
                onChange={(e) => set({ fill: e.target.value } as Partial<CanvasElement>)} />
            </Row>
          </>
        )}
      </div>

      <div className="border-t border-gray-100 pt-3 flex gap-2">
        <button onClick={() => store.getState().bringToFront(id)} className="flex-1 px-2 py-1.5 text-xs border border-gray-200 rounded-md hover:bg-gray-50">置顶</button>
        <button onClick={() => store.getState().sendToBack(id)} className="flex-1 px-2 py-1.5 text-xs border border-gray-200 rounded-md hover:bg-gray-50">置底</button>
      </div>
    </div>
  );
}
