import { describe, it, expect } from 'vitest';
import {
  anchorFlowLayout,
  buildLayoutNodes,
  collectPageBreaks,
  computeActualHeight,
  countListItems,
  estimateListHeight,
  itemTemplateHeight,
  overlapsX,
  paginateLayout,
  type LayoutNode,
} from './layoutEngine';
import type { CanvasElement, CanvasListElement, CanvasTextElement } from '../types/canvasSchema';

const text = (id: string, x: number, y: number, w: number, h: number): CanvasTextElement => ({
  id, type: 'text', x, y, w, h, zIndex: 1, value: id,
});

const list = (id: string, x: number, y: number, w: number, h: number, over: Partial<CanvasListElement> = {}): CanvasListElement => ({
  id, type: 'list', x, y, w, h, zIndex: 1, bind: { path: 'experience' }, itemGap: 4, divider: false,
  itemTemplate: [text('t1', 0, 0, w, 5), text('t2', 0, 5.5, w, 12)],
  ...over,
});

const node = (id: string, over: Partial<LayoutNode> = {}): LayoutNode => ({
  id, x: 0, y: 0, w: 100, h: 10, actualH: 10, ...over,
});

describe('overlapsX', () => {
  it('区间相交判定', () => {
    expect(overlapsX({ x: 0, w: 100 }, { x: 50, w: 100 })).toBe(true);
    expect(overlapsX({ x: 0, w: 100 }, { x: 100, w: 100 })).toBe(false);
    expect(overlapsX({ x: 0, w: 80 }, { x: 110, w: 80 })).toBe(false);
  });
});

describe('itemTemplateHeight / estimateListHeight', () => {
  it('条目模板高度取子元素底部最大值', () => {
    expect(itemTemplateHeight(list('l', 0, 0, 100, 20))).toBe(17.5);
  });

  it('按条目数与间距估算总高度', () => {
    expect(estimateListHeight(list('l', 0, 0, 100, 20), 0)).toBe(0);
    expect(estimateListHeight(list('l', 0, 0, 100, 20), 1)).toBe(17.5);
    expect(estimateListHeight(list('l', 0, 0, 100, 20), 3)).toBe(3 * 17.5 + 2 * 4);
  });
});

describe('countListItems / computeActualHeight', () => {
  it('读取绑定的数组条目数', () => {
    expect(countListItems(list('l', 0, 0, 100, 20), { experience: [1, 2, 3] })).toBe(3);
    expect(countListItems(list('l', 0, 0, 100, 20), {})).toBe(0);
    expect(countListItems(list('l', 0, 0, 100, 20), null)).toBe(0);
  });

  it('列表运行时高度取「设计高」与「估算高」的较大者', () => {
    const l = list('l', 0, 0, 100, 20);
    // 1 条 = 17.5 < 设计 20 → 保持 20
    expect(computeActualHeight(l, { experience: [1] })).toBe(20);
    // 4 条 = 4*17.5 + 3*4 = 82 → 取 82
    expect(computeActualHeight(l, { experience: [1, 2, 3, 4] })).toBe(82);
  });

  it('非列表元素运行时高度等于设计高', () => {
    expect(computeActualHeight(text('t', 0, 0, 100, 12), {})).toBe(12);
  });
});

describe('buildLayoutNodes', () => {
  it('过滤隐藏元素与分页符', () => {
    const elements: CanvasElement[] = [
      text('a', 0, 0, 10, 10),
      { ...text('b', 0, 0, 10, 10), hidden: true },
      { id: 'pb', type: 'pageBreak', x: 0, y: 100, w: 210, h: 0, zIndex: 9 },
    ];
    const nodes = buildLayoutNodes(elements, {});
    expect(nodes.map((n) => n.id)).toEqual(['a']);
  });
});

describe('anchorFlowLayout', () => {
  it('同列：列表变长，下方元素按增量下移', () => {
    const items = [
      node('list', { x: 20, y: 50, w: 80, h: 20, actualH: 60 }), // 多出 40
      node('below', { x: 20, y: 80, w: 80, h: 10, actualH: 10 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.below.shift).toBe(40);
    expect(r.byId.below.y).toBe(120);
  });

  it('跨列：左列变长不影响右列', () => {
    const items = [
      node('left', { x: 10, y: 50, w: 80, h: 20, actualH: 60 }),
      node('right', { x: 110, y: 50, w: 80, h: 20, actualH: 20 }),
      node('belowRight', { x: 110, y: 80, w: 80, h: 10, actualH: 10 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.belowRight.y).toBe(80);
    expect(r.byId.belowRight.shift).toBe(0);
  });

  it('列表为空时下方元素上移（负位移）', () => {
    const items = [
      node('list', { x: 20, y: 50, w: 80, h: 20, actualH: 0 }),
      node('below', { x: 20, y: 80, w: 80, h: 10, actualH: 10 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.below.y).toBe(60);
  });

  it('高度未变化的满宽元素不下推', () => {
    const items = [
      node('header', { x: 0, y: 0, w: 210, h: 30, actualH: 30 }),
      node('below', { x: 20, y: 80, w: 80, h: 10, actualH: 10 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.below.y).toBe(80);
  });

  it('多个上方元素的高度增量累加', () => {
    const items = [
      node('l1', { x: 20, y: 10, w: 80, h: 20, actualH: 30 }), // +10
      node('l2', { x: 20, y: 40, w: 80, h: 20, actualH: 35 }), // +15
      node('below', { x: 20, y: 70, w: 80, h: 10, actualH: 10 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.below.shift).toBe(25);
    expect(r.byId.below.y).toBe(95);
  });

  it('并排元素互不推挤', () => {
    const items = [
      node('a', { x: 10, y: 50, w: 80, h: 20, actualH: 40 }),
      node('b', { x: 110, y: 50, w: 80, h: 20, actualH: 20 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.a.y).toBe(50);
    expect(r.byId.b.y).toBe(50);
  });

  it('超出内容区底部的元素计入 overflow', () => {
    const items = [
      node('safe', { x: 0, y: 100, w: 100, h: 40, actualH: 40 }),
      node('out', { x: 0, y: 280, w: 100, h: 20, actualH: 20 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.overflowIds).toEqual(['out']);
  });

  it('分页后每页独立推挤', () => {
    const nodes = [
      node('p1a', { y: 10, h: 10, actualH: 10 }),
      node('p2list', { y: 110, h: 20, actualH: 80 }), // 变长 +60
      node('p2b', { y: 150, h: 10, actualH: 10 }),
    ];
    const pages = paginateLayout(nodes, [100], 297);
    expect(pages[0].nodes[0].y).toBe(10); // 第一页不受第二页影响
    expect(pages[1].nodes.find((n) => n.id === 'p2b')!.y).toBe(110); // 50 + 60
  });

  it('下移后才越界的元素也能被检出', () => {
    const items = [
      node('list', { x: 0, y: 10, w: 100, h: 20, actualH: 200 }),
      node('below', { x: 0, y: 200, w: 100, h: 20, actualH: 20 }),
    ];
    const r = anchorFlowLayout(items, 297);
    expect(r.byId.below.y).toBe(380);
    expect(r.overflowIds).toContain('below');
  });
});

describe('collectPageBreaks', () => {
  it('收集可见分页符并按 y 升序（忽略隐藏项）', () => {
    const elements: CanvasElement[] = [
      { id: 'p2', type: 'pageBreak', x: 0, y: 200, w: 182, h: 0, zIndex: 9 },
      { id: 'p1', type: 'pageBreak', x: 0, y: 100, w: 182, h: 0, zIndex: 9 },
      { id: 'p3', type: 'pageBreak', x: 0, y: 50, w: 182, h: 0, zIndex: 9, hidden: true },
    ];
    expect(collectPageBreaks(elements)).toEqual([100, 200]);
  });
});

describe('paginateLayout', () => {
  it('无分页符时返回单页，位置与锚点布局一致', () => {
    const pages = paginateLayout([node('a', { y: 10, h: 10, actualH: 10 })], [], 297);
    expect(pages).toHaveLength(1);
    expect(pages[0].top).toBe(0);
    expect(pages[0].nodes[0].y).toBe(10);
  });

  it('按分页符切分并换算为页内坐标', () => {
    const nodes = [
      node('a', { y: 10, h: 10, actualH: 10 }),
      node('b', { y: 120, h: 10, actualH: 10 }),
      node('c', { y: 200, h: 10, actualH: 10 }),
    ];
    const pages = paginateLayout(nodes, [100, 180], 297);
    expect(pages).toHaveLength(3);
    expect(pages[0].nodes.map((n) => n.id)).toEqual(['a']);
    expect(pages[1].nodes.map((n) => n.id)).toEqual(['b']);
    expect(pages[1].nodes[0].y).toBe(20); // 120 − 100
    expect(pages[2].nodes[0].y).toBe(20); // 200 − 180
  });

  it('y 恰好落在分页符上的元素归入下一页', () => {
    const pages = paginateLayout([node('x', { y: 100, h: 10, actualH: 10 })], [100], 297);
    expect(pages).toHaveLength(1);
    expect(pages[0].top).toBe(100);
    expect(pages[0].nodes[0].y).toBe(0);
  });

  it('连续分页符不产生空页', () => {
    const nodes = [
      node('a', { y: 10, h: 10, actualH: 10 }),
      node('b', { y: 300, h: 10, actualH: 10 }),
    ];
    const pages = paginateLayout(nodes, [100, 100], 297);
    expect(pages).toHaveLength(2);
    expect(pages.map((p) => p.nodes.map((n) => n.id))).toEqual([['a'], ['b']]);
  });

  it('每页独立报告溢出', () => {
    const nodes = [
      node('a', { y: 10, h: 10, actualH: 10 }),
      node('k', { y: 110, h: 10, actualH: 400 }),
    ];
    const pages = paginateLayout(nodes, [100], 297);
    expect(pages[0].overflowIds).toEqual([]);
    expect(pages[1].overflowIds).toEqual(['k']);
  });
});
