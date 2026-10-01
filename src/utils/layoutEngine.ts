import type { CanvasElement, CanvasListElement } from '../types/canvasSchema';
import { resolvePath } from '../types/canvasSchema';
import { filterVisibleItems } from './sectionVisibility';

/**
 * 锚点流式布局引擎（R8-A4 §3.2）。
 *
 * 画布是绝对定位，但简历内容天然变长（工作经历 2 条 vs 8 条）。
 * 策略：**设计时高度 ≠ 运行时高度**，把「多出来的高度」按列（lane）向下传导。
 *
 * 传导规则（一次计算，非迭代，结果确定）：
 *   E.y = E.设计Y + Σ (P.运行时高 − P.设计高)
 *   其中 P 满足：① 设计上完全位于 E 上方（P.y + P.h ≤ E.y）；② 与 E 在水平方向有重叠。
 *
 * 这样同列元素保持相对关系、跨列互不干扰，满宽元素只有真正变高时才会下推。
 */

export interface LayoutNode {
  id: string;
  /** 设计坐标 / 尺寸（mm）。 */
  x: number;
  y: number;
  w: number;
  h: number;
  /** 运行时高度（mm），≥ h（内容变多）/ < h（内容为空）。 */
  actualH: number;
}

export interface PlacedNode {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
  /** 相对设计位置的位移（mm），可为负。 */
  shift: number;
}

export interface LayoutResult {
  nodes: PlacedNode[];
  byId: Record<string, PlacedNode>;
  /** 超出内容区底部的元素 id。 */
  overflowIds: string[];
}

/** 两个元素在水平方向是否重叠。 */
export function overlapsX(a: { x: number; w: number }, b: { x: number; w: number }): boolean {
  return a.x < b.x + b.w && b.x < a.x + a.w;
}

/** 列表条目模板的设计高度（取子元素底部最大值）。 */
export function itemTemplateHeight(list: CanvasListElement): number {
  return list.itemTemplate.reduce((max, child) => Math.max(max, child.y + child.h), 0);
}

/** 列表按条目数估算的运行时高度。 */
export function estimateListHeight(list: CanvasListElement, count: number): number {
  if (count <= 0) return 0;
  const h = itemTemplateHeight(list);
  const gap = list.itemGap ?? 0;
  return count * h + (count - 1) * gap;
}

/**
 * 列表绑定的实际条目数（已扣除被简历隐藏的板块）。
 * 与渲染器使用同一判定，保证「推挤高度」与「实际渲染」一致。
 */
export function countListItems(list: CanvasListElement, data: unknown): number {
  const value = resolvePath(data, list.bind.path);
  if (!Array.isArray(value)) return 0;
  return filterVisibleItems(data, list.bind.path, value).length;
}

/**
 * 单元素的运行时高度：
 * - 列表：无可见条目时塌缩为 0（如板块被隐藏 / 数据为空），下方元素随之上移；
 *   有条目时取「设计高」与「估算高」的较大者，避免条目少时布局跳动。
 * - 其余元素等于设计高。
 */
export function computeActualHeight(element: CanvasElement, data: unknown): number {
  if (element.type === 'list') {
    const count = countListItems(element, data);
    if (count === 0) return 0;
    return Math.max(element.h, estimateListHeight(element, count));
  }
  return element.h;
}

/** 由画布元素与简历数据构建布局输入（隐藏元素与分页符不参与）。 */
export function buildLayoutNodes(elements: CanvasElement[], data: unknown): LayoutNode[] {
  return elements
    .filter((el) => !el.hidden && el.type !== 'pageBreak')
    .map((el) => ({
      id: el.id,
      x: el.x,
      y: el.y,
      w: el.w,
      h: el.h,
      actualH: computeActualHeight(el, data),
    }));
}

export interface PaginatedPage {
  /** 0 起的页序。 */
  index: number;
  /** 该页在连续坐标系中的顶部位置（mm，内容区坐标）。 */
  top: number;
  nodes: PlacedNode[];
  /** 本页超出内容区底部的元素 id（本期不做自动切分，仅提示）。 */
  overflowIds: string[];
}

/** 收集分页符位置（按 y 升序）。 */
export function collectPageBreaks(elements: CanvasElement[]): number[] {
  return elements
    .filter((el) => el.type === 'pageBreak' && !el.hidden)
    .map((el) => el.y)
    .sort((a, b) => a - b);
}

/**
 * 按分页符把元素切分到多页，每页独立做锚点流式布局。
 * 无分页符时返回单页，与未分页时的行为完全一致。
 *
 * 边界规则：y 恰好落在分页符上的元素归入**下一页**；连续分页符产生的空页会被跳过。
 */
export function paginateLayout(
  nodes: LayoutNode[],
  breaks: number[],
  contentHeight: number,
): PaginatedPage[] {
  if (breaks.length === 0) {
    const single = anchorFlowLayout(nodes, contentHeight);
    return [{ index: 0, top: 0, nodes: single.nodes, overflowIds: single.overflowIds }];
  }

  const pageCount = breaks.length + 1;
  const pages: PaginatedPage[] = [];
  for (let i = 0; i < pageCount; i++) {
    const top = i === 0 ? 0 : breaks[i - 1];
    const bottom = i === pageCount - 1 ? Number.POSITIVE_INFINITY : breaks[i];
    const pageNodes = nodes
      .filter((n) => n.y >= top && n.y < bottom)
      .map((n) => ({ ...n, y: n.y - top }));
    if (pageNodes.length === 0) continue;
    const laid = anchorFlowLayout(pageNodes, contentHeight);
    pages.push({ index: pages.length, top, nodes: laid.nodes, overflowIds: laid.overflowIds });
  }

  if (pages.length === 0) {
    const empty = anchorFlowLayout([], contentHeight);
    pages.push({ index: 0, top: 0, nodes: empty.nodes, overflowIds: empty.overflowIds });
  }
  return pages;
}

/**
 * 锚点流式布局。
 *
 * @param items         设计节点（见 {@link buildLayoutNodes}）
 * @param contentHeight 内容区高度（mm），一般 = 页面高 − 上下边距
 */
export function anchorFlowLayout(items: LayoutNode[], contentHeight: number): LayoutResult {
  const nodes: PlacedNode[] = items.map((e) => {
    let shift = 0;
    for (const p of items) {
      if (p.id === e.id) continue;
      if (p.y + p.h > e.y) continue; // P 设计上不在 E 上方
      if (!overlapsX(p, e)) continue; // 分属不同列，互不影响
      shift += p.actualH - p.h;
    }
    return { id: e.id, x: e.x, y: e.y + shift, w: e.w, h: e.actualH, shift };
  });

  const byId: Record<string, PlacedNode> = {};
  const overflowIds: string[] = [];
  for (const n of nodes) {
    byId[n.id] = n;
    if (n.y + n.h > contentHeight + 0.5) overflowIds.push(n.id);
  }
  return { nodes, byId, overflowIds };
}
