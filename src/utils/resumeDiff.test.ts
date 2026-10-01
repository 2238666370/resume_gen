import { describe, it, expect } from 'vitest';
import { diffResume, isEnhancing, applyAcceptedDiff } from './resumeDiff';
import type { ResumeData, ExperienceItem, SkillItem } from '../types/resume';

/** 构造一份结构完整的最小简历，便于逐字段比对。 */
function base(): ResumeData {
  return {
    title: '未命名简历',
    templateId: 'classic',
    accentColor: '#2563eb',
    personal: {
      name: '张三', title: '', email: '', phone: '',
      location: '', website: '', avatar: '', summary: '',
    },
    education: [],
    experience: [],
    internship: [],
    skills: [],
    projects: [],
    certificates: [],
    languages: [],
    customSections: [],
    sections: [],
  };
}

const exp = (id: string): ExperienceItem => ({
  id, company: '某公司', position: '工程师',
  startDate: '2020.01', endDate: '2021.01', current: false, description: '',
});

const skill = (id: string, name: string, level: number): SkillItem => ({ id, name, level });

describe('diffResume', () => {
  it('检测标量字段变更并生成中文标签与路径', () => {
    const a = base();
    const b = base();
    b.personal.name = '李四';

    const items = diffResume(a, b);
    expect(items).toHaveLength(1);
    expect(items[0].path).toEqual(['personal', 'name']);
    expect(items[0].oldText).toBe('张三');
    expect(items[0].revisedText).toBe('李四');
    expect(items[0].label).toBe('个人信息 · 姓名');
  });

  it('相同数据产出空改动列表', () => {
    expect(diffResume(base(), base())).toHaveLength(0);
  });

  it('忽略 id 字段的变化', () => {
    const a = base();
    const b = base();
    a.experience = [exp('old-id')];
    b.experience = [exp('new-id')];

    expect(diffResume(a, b)).toHaveLength(0);
  });

  it('数组内元素字段变更可逐项定位并带序号标签', () => {
    const a = base();
    const b = base();
    a.experience = [exp('e1')];
    b.experience = [{ ...exp('e1'), company: '新公司' }];

    const items = diffResume(a, b);
    expect(items).toHaveLength(1);
    expect(items[0].path).toEqual(['experience', 0, 'company']);
    expect(items[0].label).toBe('工作经历 #1 · 公司');
  });

  it('数组长度变化时整段作为一个改动点', () => {
    const a = base();
    const b = base();
    a.skills = [skill('s1', 'Java', 3)];
    b.skills = [skill('s1', 'Java', 3), skill('s2', 'Go', 4)];

    const items = diffResume(a, b);
    expect(items).toHaveLength(1);
    expect(items[0].path).toEqual(['skills']);
    expect(items[0].label).toBe('技能 · skills');
  });
});

describe('isEnhancing', () => {
  it('改写后内容非空视为增强', () => {
    const a = base();
    const b = base();
    b.personal.name = '李四';
    expect(isEnhancing(diffResume(a, b)[0])).toBe(true);
  });

  it('清空字符串不视为增强', () => {
    const a = base();
    const b = base();
    a.personal.name = '张三';
    b.personal.name = '';
    expect(isEnhancing(diffResume(a, b)[0])).toBe(false);
  });

  it('纯空白字符串不视为增强', () => {
    const a = base();
    const b = base();
    a.personal.name = '张三';
    b.personal.name = '   ';
    expect(isEnhancing(diffResume(a, b)[0])).toBe(false);
  });
});

describe('applyAcceptedDiff', () => {
  it('仅合并被接受的改动，未接受字段保持原值', () => {
    const a = base();
    const b = base();
    b.personal.name = '李四';
    b.personal.email = 'lisi@example.com';

    const items = diffResume(a, b);
    const nameKey = items.find((i) => i.path[1] === 'name')!.key;
    const merged = applyAcceptedDiff(a, b, new Set([nameKey]));

    expect(merged.personal.name).toBe('李四');
    expect(merged.personal.email).toBe('');
  });

  it('不修改原始数据（深拷贝后合并）', () => {
    const a = base();
    const b = base();
    b.personal.name = '李四';

    const items = diffResume(a, b);
    applyAcceptedDiff(a, b, new Set([items[0].key]));

    expect(a.personal.name).toBe('张三');
  });

  it('支持接受数组元素内的字段改动', () => {
    const a = base();
    const b = base();
    a.skills = [skill('s1', 'Java', 3)];
    b.skills = [skill('s1', 'Java', 5)];

    const items = diffResume(a, b);
    const merged = applyAcceptedDiff(a, b, new Set([items[0].key]));

    expect(merged.skills[0].level).toBe(5);
  });

  it('空接受集合时返回与原始等价的副本', () => {
    const a = base();
    const b = base();
    b.personal.name = '李四';

    const merged = applyAcceptedDiff(a, b, new Set());
    expect(merged).toEqual(a);
    expect(merged).not.toBe(a);
  });
});
