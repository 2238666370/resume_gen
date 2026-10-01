import { describe, it, expect, beforeEach } from 'vitest';
import { flattenElements, useCanvasEditorStore } from './canvasEditorStore';
import { createDefaultCanvasSchema, type CanvasElement } from '../types/canvasSchema';

const store = () => useCanvasEditorStore;
const state = () => store().getState();
const byId = (id: string): CanvasElement => {
  const el = state().schema.elements.find((e) => e.id === id);
  if (!el) throw new Error(`element not found: ${id}`);
  return el;
};

beforeEach(() => {
  // 以空画布起测，避免默认 schema 自带元素干扰断言
  state().load({ ...createDefaultCanvasSchema(), elements: [] });
});

/** 连续添加两个元素并返回它们的 id（后添加的会被自动选中）。 */
function addTwo(): [string, string] {
  state().addElement('text');
  const first = state().selectedIds[0];
  state().addElement('shape');
  const second = state().selectedIds[0];
  return [first, second];
}

describe('canvasEditorStore 基础操作', () => {
  it('添加元素后自动选中，并可删除', () => {
    state().addElement('text');
    expect(state().schema.elements).toHaveLength(1);
    expect(state().selectedIds).toHaveLength(1);
    expect(state().dirty).toBe(true);

    state().removeSelected();
    expect(state().schema.elements).toHaveLength(0);
    expect(state().selectedIds).toHaveLength(0);
  });

  it('nudgeSelected 按增量平移并保留一位小数', () => {
    state().addElement('text');
    const id = state().selectedIds[0];
    const start = byId(id);

    state().nudgeSelected(1.26, -0.44);
    const moved = byId(id);
    expect(moved.x).toBeCloseTo(start.x + 1.3, 5);
    expect(moved.y).toBeCloseTo(start.y - 0.4, 5);
  });

  it('撤销 / 重做回放操作', () => {
    const len = state().schema.elements.length;
    state().addElement('text');
    expect(state().schema.elements).toHaveLength(len + 1);

    state().undo();
    expect(state().schema.elements).toHaveLength(len);

    state().redo();
    expect(state().schema.elements).toHaveLength(len + 1);
  });

  it('复制选中元素生成新 id', () => {
    state().addElement('text');
    const id = state().selectedIds[0];
    state().duplicateSelected();

    const copyId = state().selectedIds[0];
    expect(copyId).not.toBe(id);
    expect(state().schema.elements).toHaveLength(2);
  });
});

describe('canvasEditorStore 成组 / 解组', () => {
  it('成组把子元素转为组内相对坐标，解组还原绝对坐标且保留 id', () => {
    const [a, b] = addTwo();
    state().updateElement(a, { x: 10, y: 20 });
    state().updateElement(b, { x: 60, y: 50 });
    const before = [byId(a), byId(b)].map((e) => ({ id: e.id, x: e.x, y: e.y }));

    state().select([a, b]);
    state().groupSelected();

    expect(state().schema.elements).toHaveLength(1);
    const group = state().schema.elements[0];
    expect(group.type).toBe('group');
    expect(state().selectedIds).toEqual([group.id]);
    if (group.type !== 'group') throw new Error('not group');
    expect(group.children).toHaveLength(2);
    expect(group.children.find((c) => c.id === b)?.x).toBe(50); // 60 - 10

    state().ungroupSelected();
    expect(state().schema.elements).toHaveLength(2);
    for (const origin of before) {
      const el = byId(origin.id);
      expect(el.x).toBeCloseTo(origin.x, 5);
      expect(el.y).toBeCloseTo(origin.y, 5);
    }
  });

  it('少于 2 个元素时不成组', () => {
    state().addElement('text');
    state().groupSelected();
    expect(state().schema.elements).toHaveLength(1);
    expect(state().schema.elements[0].type).toBe('text');
  });
});

describe('canvasEditorStore 对齐 / 分布', () => {
  it('左对齐统一到最左边缘', () => {
    const [a, b] = addTwo();
    state().updateElement(a, { x: 10, w: 20 });
    state().updateElement(b, { x: 50, w: 30 });

    state().select([a, b]);
    state().alignSelected('left');

    expect(byId(a).x).toBe(10);
    expect(byId(b).x).toBe(10);
  });

  it('顶对齐统一到最上边缘', () => {
    const [a, b] = addTwo();
    state().updateElement(a, { y: 30 });
    state().updateElement(b, { y: 80 });

    state().select([a, b]);
    state().alignSelected('top');
    expect(byId(a).y).toBe(30);
    expect(byId(b).y).toBe(30);
  });

  it('水平等距分布保持首尾不动、中间均分', () => {
    state().addElement('text');
    const ids = [state().selectedIds[0]];
    state().addElement('text');
    ids.push(state().selectedIds[0]);
    state().addElement('text');
    ids.push(state().selectedIds[0]);

    state().updateElement(ids[0], { x: 0, w: 10 });
    state().updateElement(ids[1], { x: 12, w: 10 });
    state().updateElement(ids[2], { x: 100, w: 10 });

    state().select(ids);
    state().distributeSelected('x');

    expect(byId(ids[0]).x).toBe(0);
    expect(byId(ids[1]).x).toBe(50);
    expect(byId(ids[2]).x).toBe(100);
  });

  it('不足 3 个元素时不执行分布', () => {
    const [a, b] = addTwo();
    state().updateElement(a, { x: 0 });
    state().updateElement(b, { x: 10 });

    state().select([a, b]);
    state().distributeSelected('x');
    expect(byId(b).x).toBe(10);
  });
});

describe('flattenElements', () => {
  it('展开成组子元素并保留组名层级', () => {
    const [a, b] = addTwo();
    state().select([a, b]);
    state().groupSelected();

    const flat = flattenElements(state().schema.elements);
    expect(flat).toHaveLength(3); // 组 + 2 个子元素
    expect(flat.filter((f) => f.depth === 1)).toHaveLength(2);
  });

  it('列表条目模板元素被展开并标记 inListTemplate', () => {
    state().addElement('list');
    const flat = flattenElements(state().schema.elements);
    const templateItems = flat.filter((f) => f.inListTemplate);
    expect(templateItems.length).toBeGreaterThan(0);
    expect(templateItems.every((f) => f.depth === 1)).toBe(true);
  });

  it('列表条目模板元素坐标 = 列表位置 + 条目内相对坐标', () => {
    state().addElement('list');
    const list = state().schema.elements[0];
    if (list.type !== 'list') throw new Error('not list');
    const flat = flattenElements(state().schema.elements);
    const first = flat.find((f) => f.inListTemplate);
    expect(first).toBeDefined();
    expect(first!.absX).toBeCloseTo(list.x + first!.el.x, 5);
    expect(first!.absY).toBeCloseTo(list.y + first!.el.y, 5);
  });
});
