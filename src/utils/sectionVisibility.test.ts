import { describe, it, expect } from 'vitest';
import { filterVisibleItems, sectionTypeOfBindPath } from './sectionVisibility';
import { countListItems, computeActualHeight } from './layoutEngine';
import type { CanvasListElement } from '../types/canvasSchema';

const list = (path: string, h = 30): CanvasListElement => ({
  id: 'l1', type: 'list', x: 0, y: 0, w: 170, h, zIndex: 1,
  bind: { path }, itemGap: 4,
  itemTemplate: [{ id: 't1', type: 'text', x: 0, y: 0, w: 170, h: 10, zIndex: 1, value: 'x' }],
});

describe('sectionTypeOfBindPath', () => {
  it('数组绑定路径映射到 resume_section.section_type', () => {
    expect(sectionTypeOfBindPath('experience')).toBe('experience');
    expect(sectionTypeOfBindPath('customSections')).toBe('custom');
    expect(sectionTypeOfBindPath('personal.name')).toBeUndefined();
  });
});

describe('filterVisibleItems', () => {
  it('无 sections 信息时全部放行（兼容示例数据）', () => {
    const items = [1, 2, 3];
    expect(filterVisibleItems({}, 'experience', items)).toHaveLength(3);
    expect(filterVisibleItems(null, 'experience', items)).toHaveLength(3);
  });

  it('板块可见时原样返回', () => {
    const data = { sections: [{ type: 'experience', visible: true }] };
    expect(filterVisibleItems(data, 'experience', [1, 2])).toHaveLength(2);
  });

  it('板块被隐藏时返回空数组', () => {
    const data = { sections: [{ type: 'internship', visible: false }] };
    expect(filterVisibleItems(data, 'internship', [1, 2])).toEqual([]);
  });

  it('自定义板块按其 customId 逐条判定显隐', () => {
    const items = [{ id: 'c1' }, { id: 'c2' }, { id: 'c3' }];
    const data = {
      sections: [
        { type: 'custom', customId: 'c1', visible: true },
        { type: 'custom', customId: 'c2', visible: false },
      ],
    };
    // c1 可见、c2 隐藏、c3 无记录默认可见
    expect(filterVisibleItems(data, 'customSections', items).map((i) => i.id)).toEqual(['c1', 'c3']);
  });

  it('无绑定关系的路径不受显隐影响', () => {
    const data = { sections: [{ type: 'experience', visible: false }] };
    expect(filterVisibleItems(data, 'text', [1])).toHaveLength(1);
  });
});

describe('布局与渲染共用显隐判定', () => {
  it('隐藏板块后条目数按 0 计，列表高度回落', () => {
    const l = list('experience', 30);
    const hidden = { experience: [{}, {}], sections: [{ type: 'experience', visible: false }] };
    const shown = { experience: [{}, {}], sections: [{ type: 'experience', visible: true }] };

    expect(countListItems(l, hidden)).toBe(0);
    expect(computeActualHeight(l, hidden)).toBe(0); // 空列表 → 高度 0，下方元素随之上移
    expect(countListItems(l, shown)).toBe(2);
    expect(computeActualHeight(l, shown)).toBe(30); // 2 条 = 20 < 设计高 30 → 取 30
  });
});
