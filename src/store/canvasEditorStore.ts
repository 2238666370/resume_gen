import { create } from 'zustand';
import type {
  CanvasElement, CanvasElementType, CanvasGroupElement,
  CanvasSchema, CanvasStyle,
} from '../types/canvasSchema';
import { createDefaultCanvasSchema, DEFAULT_PAGE, DEFAULT_THEME, nextElementId } from '../types/canvasSchema';
import { buildPreset, createListElement } from '../utils/canvasPresets';

const HISTORY_LIMIT = 50;

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

const primary = (): string => DEFAULT_THEME.palette?.primary ?? '#2563eb';

/* ───────────── 树操作辅助（就地修改已 clone 的 schema） ───────────── */

function mutateElement(elements: CanvasElement[], id: string, fn: (el: CanvasElement) => void): boolean {
  for (const el of elements) {
    if (el.id === id) {
      fn(el);
      return true;
    }
    if (el.type === 'group' && mutateElement(el.children, id, fn)) return true;
    if (el.type === 'list' && mutateElement(el.itemTemplate, id, fn)) return true;
  }
  return false;
}

function removeByIds(elements: CanvasElement[], ids: Set<string>): CanvasElement[] {
  return elements
    .filter((el) => !ids.has(el.id))
    .map((el) => {
      if (el.type === 'group') return { ...el, children: removeByIds(el.children, ids) };
      if (el.type === 'list') return { ...el, itemTemplate: removeByIds(el.itemTemplate, ids) };
      return el;
    });
}

/** 扁平化顶层元素与成组子元素（含绝对坐标），供画布选中层与图层面板使用。 */
export interface FlatElement {
  el: CanvasElement;
  /** 绝对坐标（mm）：成组子元素为组内相对坐标 + 组偏移。 */
  absX: number;
  absY: number;
  depth: number;
  /** 所属组 / 列表 id（子元素才有）。 */
  parentId?: string;
  /** 是否位于列表的条目模板内（编辑时坐标相对条目，且不可与外层元素成组）。 */
  inListTemplate?: boolean;
}

export function flattenElements(
  elements: CanvasElement[],
  offsetX = 0,
  offsetY = 0,
  depth = 0,
  parentId?: string,
  inListTemplate = false,
): FlatElement[] {
  const out: FlatElement[] = [];
  for (const el of elements) {
    const absX = offsetX + el.x;
    const absY = offsetY + el.y;
    out.push({ el, absX, absY, depth, parentId, inListTemplate });
    if (el.type === 'group') {
      out.push(...flattenElements(el.children, absX, absY, depth + 1, el.id, inListTemplate));
    }
    if (el.type === 'list') {
      // 条目模板元素按「首个条目」的位置展示，便于直接编辑模板
      out.push(...flattenElements(el.itemTemplate, absX, absY, depth + 1, el.id, true));
    }
  }
  return out;
}

function findElement(elements: CanvasElement[], id: string): CanvasElement | undefined {
  for (const el of elements) {
    if (el.id === id) return el;
    if (el.type === 'group') {
      const hit = findElement(el.children, id);
      if (hit) return hit;
    }
  }
  return undefined;
}

interface BoxedElement {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** 取选中元素的「可变引用」几何信息（用于批量对齐/分布，就地修改已 clone 的 schema）。 */
function selectedBoxes(schema: CanvasSchema, ids: string[]): BoxedElement[] {
  return ids
    .map((id) => findElement(schema.elements, id))
    .filter((el): el is CanvasElement => !!el) as unknown as BoxedElement[];
}

/** 按类型生成默认元素（落在画布可放置区，随添加次数轻微错位避免完全重叠）。 */
function createElement(type: CanvasElementType, index: number): CanvasElement {
  const base = {
    id: nextElementId(type),
    x: 20,
    y: 20 + (index % 12) * 4,
    w: 100,
    h: 8,
    zIndex: index + 1,
  };
  switch (type) {
    case 'text':
      return { ...base, type: 'text', name: '文本', value: '文本内容', style: { fontSize: 11 } };
    case 'field':
      return {
        ...base, type: 'field', name: '字段', w: 70, h: 6,
        bind: { path: 'personal.name', fallback: '姓名' },
        style: { fontSize: 12, fontWeight: 600 },
      };
    case 'heading':
      return {
        ...base, type: 'heading', name: '标题', w: 120, h: 7,
        value: '板块标题', decoration: 'bar',
        style: { fontSize: 11, fontWeight: 700, color: primary() },
      };
    case 'list':
      // 默认给「工作经历」固定块；组件库中的固定块走 addBlock 指定绑定
      return createListElement({
        path: 'experience', name: '工作经历', x: base.x, y: base.y, w: 170, z: base.zIndex,
      });
    case 'image':
      return { ...base, type: 'image', name: '图片', w: 30, h: 30, source: 'url', url: '', fit: 'cover' };
    case 'shape':
      return { ...base, type: 'shape', name: '形状', w: 60, h: 20, shape: 'rect', fill: primary() };
    case 'pageBreak':
      // 宽度对齐内容区，使选中框与设计态分页标记线一致
      return {
        ...base, type: 'pageBreak', name: '分页符', x: 0, y: 120, h: 0, zIndex: 900,
        w: DEFAULT_PAGE.width - DEFAULT_PAGE.margin.left - DEFAULT_PAGE.margin.right,
      };
    default:
      return { ...base, type: 'text', name: '文本', value: '' };
  }
}

/* ───────────── Store ───────────── */

export interface CanvasEditorState {
  schema: CanvasSchema;
  selectedIds: string[];
  zoom: number;
  past: CanvasSchema[];
  future: CanvasSchema[];
  dirty: boolean;

  load: (schema: CanvasSchema) => void;
  reset: () => void;
  setZoom: (zoom: number) => void;
  select: (ids: string[]) => void;
  toggleSelect: (id: string) => void;

  /** 记录一次「操作前」快照；拖拽等连续操作在开始时调用一次即可。 */
  snapshot: () => void;
  addElement: (type: CanvasElementType) => void;
  /** 添加「固定块 / 自定义板块」（绑定到既有简历表的列表块）。 */
  addBlock: (path: string, label: string) => void;
  /** 添加「区块预设」（组合元素，落版为一个可整体移动的 group）。 */
  addPreset: (presetId: string) => void;
  updateElement: (id: string, patch: Partial<CanvasElement>) => void;
  patchStyle: (id: string, style: Partial<CanvasStyle>) => void;
  removeSelected: () => void;
  nudgeSelected: (dx: number, dy: number) => void;
  setBox: (id: string, box: { x: number; y: number; w: number; h: number }) => void;
  duplicateSelected: () => void;
  groupSelected: () => void;
  ungroupSelected: () => void;
  alignSelected: (mode: 'left' | 'centerX' | 'right' | 'top' | 'centerY' | 'bottom') => void;
  distributeSelected: (axis: 'x' | 'y') => void;
  bringToFront: (id: string) => void;
  sendToBack: (id: string) => void;
  undo: () => void;
  redo: () => void;
}

export const useCanvasEditorStore = create<CanvasEditorState>()((set, get) => {
  /** 统一变更入口：克隆 → 就地改 → 入历史。 */
  const apply = (mutator: (schema: CanvasSchema) => void, withHistory = true) => {
    const { schema, past } = get();
    const next = clone(schema);
    mutator(next);
    set({
      schema: next,
      past: withHistory ? [...past, schema].slice(-HISTORY_LIMIT) : past,
      future: [],
      dirty: true,
    });
  };

  const applyToSelected = (fn: (el: CanvasElement) => void, withHistory = true) => {
    const ids = get().selectedIds;
    if (ids.length === 0) return;
    apply((schema) => {
      for (const id of ids) mutateElement(schema.elements, id, fn);
    }, withHistory);
  };

  return {
    schema: createDefaultCanvasSchema(),
    selectedIds: [],
    zoom: 1,
    past: [],
    future: [],
    dirty: false,

    load: (schema) => set({ schema, selectedIds: [], past: [], future: [], dirty: false }),
    reset: () => set({ schema: createDefaultCanvasSchema(), selectedIds: [], past: [], future: [], dirty: false }),
    setZoom: (zoom) => set({ zoom: Math.min(2, Math.max(0.5, zoom)) }),
    select: (ids) => set({ selectedIds: ids }),
    toggleSelect: (id) => {
      const cur = get().selectedIds;
      set({ selectedIds: cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id] });
    },

    snapshot: () => {
      const { schema, past } = get();
      set({ past: [...past, clone(schema)].slice(-HISTORY_LIMIT), future: [] });
    },

    addElement: (type) => {
      const el = createElement(type, get().schema.elements.length);
      apply((schema) => {
        schema.elements = [...schema.elements, el];
      });
      set({ selectedIds: [el.id] });
    },

    addBlock: (path, label) => {
      const index = get().schema.elements.length;
      const el = createListElement({
        path,
        name: label,
        x: 20,
        y: 20 + (index % 12) * 4,
        w: 170,
        z: index + 1,
      });
      apply((schema) => {
        schema.elements = [...schema.elements, el];
      });
      set({ selectedIds: [el.id] });
    },

    addPreset: (presetId) => {
      const index = get().schema.elements.length;
      const el = buildPreset(presetId, { x: 20, y: 20 + (index % 12) * 4, w: 170, z: index + 1 });
      if (!el) return;
      apply((schema) => {
        schema.elements = [...schema.elements, el];
      });
      set({ selectedIds: [el.id] });
    },

    updateElement: (id, patch) => {
      apply((schema) => {
        mutateElement(schema.elements, id, (el) => Object.assign(el, patch));
      });
      set({ dirty: true });
    },

    patchStyle: (id, style) => {
      apply((schema) => {
        mutateElement(schema.elements, id, (el) => {
          el.style = { ...(el.style ?? {}), ...style };
        });
      });
    },

    removeSelected: () => {
      const ids = new Set(get().selectedIds);
      if (ids.size === 0) return;
      apply((schema) => {
        schema.elements = removeByIds(schema.elements, ids);
      });
      set({ selectedIds: [] });
    },

    nudgeSelected: (dx, dy) => {
      applyToSelected((el) => {
        el.x = Math.round((el.x + dx) * 10) / 10;
        el.y = Math.round((el.y + dy) * 10) / 10;
      }, false);
    },

    setBox: (id, box) => {
      apply((schema) => {
        mutateElement(schema.elements, id, (el) => {
          el.x = Math.max(0, Math.round(box.x * 10) / 10);
          el.y = Math.max(0, Math.round(box.y * 10) / 10);
          el.w = Math.max(2, Math.round(box.w * 10) / 10);
          el.h = Math.max(0, Math.round(box.h * 10) / 10);
        });
      }, false);
    },

    duplicateSelected: () => {
      const ids = new Set(get().selectedIds);
      if (ids.size === 0) return;
      const created: string[] = [];
      apply((schema) => {
        const copies = schema.elements
          .filter((el) => ids.has(el.id))
          .map((el) => {
            const copy = clone(el);
            copy.id = nextElementId(el.type);
            copy.x += 4;
            copy.y += 4;
            copy.zIndex += 1000;
            created.push(copy.id);
            return copy;
          });
        schema.elements = [...schema.elements, ...copies];
      });
      set({ selectedIds: created });
    },

    groupSelected: () => {
      const { schema, selectedIds, past } = get();
      const picked = schema.elements.filter((el) => selectedIds.includes(el.id));
      if (picked.length < 2) return;
      const minX = Math.min(...picked.map((e) => e.x));
      const minY = Math.min(...picked.map((e) => e.y));
      const maxX = Math.max(...picked.map((e) => e.x + e.w));
      const maxY = Math.max(...picked.map((e) => e.y + e.h));
      const groupId = nextElementId('g');
      const group: CanvasGroupElement = {
        id: groupId,
        type: 'group',
        name: '组合',
        x: minX,
        y: minY,
        w: maxX - minX,
        h: maxY - minY,
        zIndex: Math.max(...picked.map((e) => e.zIndex)) + 1,
        children: picked.map((e) => ({ ...clone(e), x: e.x - minX, y: e.y - minY })),
      };
      const next = clone(schema);
      const ids = new Set(selectedIds);
      next.elements = [...next.elements.filter((el) => !ids.has(el.id)), group];
      set({
        schema: next,
        selectedIds: [groupId],
        past: [...past, schema].slice(-HISTORY_LIMIT),
        future: [],
        dirty: true,
      });
    },

    ungroupSelected: () => {
      const { schema, selectedIds, past } = get();
      const groups = schema.elements.filter(
        (el) => selectedIds.includes(el.id) && el.type === 'group',
      ) as CanvasGroupElement[];
      if (groups.length === 0) return;
      const next = clone(schema);
      const created: string[] = [];
      for (const g of groups) {
        const idx = next.elements.findIndex((el) => el.id === g.id);
        if (idx < 0) continue;
        // 保留子元素原 id（不重新生成），保证解组后身份与选中语义连续
        const children = g.children.map((child) => {
          created.push(child.id);
          return { ...clone(child), x: child.x + g.x, y: child.y + g.y } as CanvasElement;
        });
        next.elements.splice(idx, 1, ...children);
      }
      set({
        schema: next,
        selectedIds: created,
        past: [...past, schema].slice(-HISTORY_LIMIT),
        future: [],
        dirty: true,
      });
    },

    alignSelected: (mode) => {
      const ids = get().selectedIds;
      if (ids.length < 2) return;
      apply((schema) => {
        const els = selectedBoxes(schema, ids);
        if (els.length < 2) return;
        const left = Math.min(...els.map((e) => e.x));
        const right = Math.max(...els.map((e) => e.x + e.w));
        const top = Math.min(...els.map((e) => e.y));
        const bottom = Math.max(...els.map((e) => e.y + e.h));
        const cx = (left + right) / 2;
        const cy = (top + bottom) / 2;
        for (const e of els) {
          if (mode === 'left') e.x = left;
          else if (mode === 'right') e.x = right - e.w;
          else if (mode === 'centerX') e.x = cx - e.w / 2;
          else if (mode === 'top') e.y = top;
          else if (mode === 'bottom') e.y = bottom - e.h;
          else if (mode === 'centerY') e.y = cy - e.h / 2;
        }
      });
    },

    distributeSelected: (axis) => {
      const ids = get().selectedIds;
      if (ids.length < 3) return;
      apply((schema) => {
        const els = selectedBoxes(schema, ids);
        if (els.length < 3) return;
        const primary = axis === 'x' ? 'x' : 'y';
        const size = axis === 'x' ? 'w' : 'h';
        const sorted = [...els].sort((a, b) => a[primary] - b[primary]);
        const first = sorted[0];
        const last = sorted[sorted.length - 1];
        const span = last[primary] + last[size] - first[primary];
        const used = sorted.reduce((sum, e) => sum + e[size], 0);
        const gap = (span - used) / (sorted.length - 1);
        let cursor = first[primary];
        for (const e of sorted) {
          e[primary] = Math.round(cursor * 10) / 10;
          cursor += e[size] + gap;
        }
      });
    },

    bringToFront: (id) => {
      const maxZ = Math.max(0, ...get().schema.elements.map((e) => e.zIndex));
      apply((schema) => mutateElement(schema.elements, id, (el) => { el.zIndex = maxZ + 1; }));
    },

    sendToBack: (id) => {
      const minZ = Math.min(0, ...get().schema.elements.map((e) => e.zIndex));
      apply((schema) => mutateElement(schema.elements, id, (el) => { el.zIndex = minZ - 1; }));
    },

    undo: () => {
      const { past, future, schema } = get();
      if (past.length === 0) return;
      const prev = past[past.length - 1];
      set({
        schema: prev,
        past: past.slice(0, -1),
        future: [schema, ...future].slice(0, HISTORY_LIMIT),
        dirty: true,
      });
    },

    redo: () => {
      const { past, future, schema } = get();
      if (future.length === 0) return;
      const next = future[0];
      set({
        schema: next,
        past: [...past, schema].slice(-HISTORY_LIMIT),
        future: future.slice(1),
        dirty: true,
      });
    },
  };
});

/** 元素选择辅助：判断元素是否可选（隐藏 / 锁定的元素不可拖拽）。 */
export function isSelectable(el: CanvasElement): boolean {
  return !el.hidden;
}

export function isEditable(el: CanvasElement): boolean {
  return !el.hidden && !el.locked;
}

export { findElement };
