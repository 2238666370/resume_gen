import type { CanvasElementType } from '../../types/canvasSchema';
import { BLOCK_PRESETS, CUSTOM_BLOCK_PATH, FIXED_BLOCKS } from '../../utils/canvasPresets';
import { useCanvasEditorStore } from '../../store/canvasEditorStore';

/** 自由元素（不含数据绑定，纯排版）。 */
const FREE_ITEMS: { type: CanvasElementType; label: string; hint: string }[] = [
  { type: 'text', label: '文本', hint: '静态文字' },
  { type: 'field', label: '数据字段', hint: '绑定单个字段' },
  { type: 'heading', label: '板块标题', hint: '带纹饰的标题' },
  { type: 'image', label: '图片', hint: '头像或外链' },
  { type: 'shape', label: '形状', hint: '矩形/圆/线' },
  { type: 'pageBreak', label: '分页符', hint: '手动分页' },
];

const btnCls =
  'text-left px-3 py-2 border border-gray-200 rounded-md hover:border-blue-400 hover:bg-blue-50 transition-colors';

/**
 * 左侧组件库（R8-A4）：
 * 「固定块」与既有简历表一一对应，块内元素可自由摆放；
 * 不在固定集合内的新块一律走「自定义板块」（标题 + 正文）。
 */
export function ComponentPalette() {
  const addElement = useCanvasEditorStore((s) => s.addElement);
  const addBlock = useCanvasEditorStore((s) => s.addBlock);
  const addPreset = useCanvasEditorStore((s) => s.addPreset);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="text-xs text-gray-400 mb-1">区块预设 · 组合</p>
        <p className="text-[10px] text-gray-300 mb-2">整组落版，可解组后逐元素微调</p>
        <div className="flex flex-col gap-1.5">
          {BLOCK_PRESETS.map((p) => (
            <button key={p.id} onClick={() => addPreset(p.id)} className={btnCls}>
              <span className="block text-sm text-gray-700">{p.label}</span>
              <span className="block text-[10px] text-gray-400 mt-0.5">{p.hint}</span>
            </button>
          ))}
        </div>
      </div>

      <div>
        <p className="text-xs text-gray-400 mb-1">固定块 · 对应简历表</p>
        <p className="text-[10px] text-gray-300 mb-2">块内元素可自由拖动摆放</p>
        <div className="flex flex-col gap-1.5">
          {FIXED_BLOCKS.map((b) => (
            <button key={b.path} onClick={() => addBlock(b.path, b.label)} className={btnCls}>
              <span className="block text-sm text-gray-700">{b.label}</span>
              <span className="block text-[10px] text-gray-400 mt-0.5 font-mono">{b.path}</span>
            </button>
          ))}
        </div>
      </div>

      <div>
        <p className="text-xs text-gray-400 mb-1">自定义板块 · 新块</p>
        <p className="text-[10px] text-gray-300 mb-2">标题 + 正文，写入 resume_custom_section</p>
        <button onClick={() => addBlock(CUSTOM_BLOCK_PATH, '自定义板块')} className={`${btnCls} w-full`}>
          <span className="block text-sm text-gray-700">自定义板块</span>
          <span className="block text-[10px] text-gray-400 mt-0.5 font-mono">{CUSTOM_BLOCK_PATH}</span>
        </button>
      </div>

      <div>
        <p className="text-xs text-gray-400 mb-2">自由元素</p>
        <div className="flex flex-col gap-1.5">
          {FREE_ITEMS.map((it) => (
            <button key={it.type} onClick={() => addElement(it.type)} className={btnCls}>
              <span className="block text-sm text-gray-700">{it.label}</span>
              <span className="block text-[10px] text-gray-400 mt-0.5">{it.hint}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
