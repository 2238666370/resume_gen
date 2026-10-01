import { flattenElements, useCanvasEditorStore } from '../../store/canvasEditorStore';

/** 图层树（R8-A4 P1）：选择、显隐、锁定、层级、删除。 */
export function LayerPanel() {
  const schema = useCanvasEditorStore((s) => s.schema);
  const selectedIds = useCanvasEditorStore((s) => s.selectedIds);
  const store = useCanvasEditorStore;

  const flat = [...flattenElements(schema.elements)].sort((a, b) => b.el.zIndex - a.el.zIndex);

  return (
    <div>
      <p className="text-xs text-gray-400 mb-2">图层（{flat.length}）</p>
      {flat.length === 0 && <p className="text-xs text-gray-300 py-4 text-center">暂无元素</p>}
      <div className="flex flex-col gap-1">
        {flat.map((f) => {
          const selected = selectedIds.includes(f.el.id);
          return (
            <div
              key={f.el.id}
              onClick={() => store.getState().select([f.el.id])}
              className={`flex items-center gap-1 px-2 py-1.5 rounded-md cursor-pointer text-xs ${
                selected ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-50'
              }`}
              style={{ paddingLeft: 8 + f.depth * 12 }}
            >
              <span className="flex-1 min-w-0 truncate">{f.el.name || f.el.type}</span>
              <button
                title={f.el.hidden ? '显示' : '隐藏'}
                onClick={(e) => { e.stopPropagation(); store.getState().updateElement(f.el.id, { hidden: !f.el.hidden }); }}
                className="px-1 text-gray-400 hover:text-gray-700"
              >
                {f.el.hidden ? '◌' : '◉'}
              </button>
              <button
                title={f.el.locked ? '解锁' : '锁定'}
                onClick={(e) => { e.stopPropagation(); store.getState().updateElement(f.el.id, { locked: !f.el.locked }); }}
                className="px-1 text-gray-400 hover:text-gray-700"
              >
                {f.el.locked ? '🔒' : '🔓'}
              </button>
              <button
                title="置顶"
                onClick={(e) => { e.stopPropagation(); store.getState().bringToFront(f.el.id); }}
                className="px-1 text-gray-400 hover:text-gray-700"
              >
                ↑
              </button>
              <button
                title="置底"
                onClick={(e) => { e.stopPropagation(); store.getState().sendToBack(f.el.id); }}
                className="px-1 text-gray-400 hover:text-gray-700"
              >
                ↓
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
