import { describe, it, expect, beforeEach } from 'vitest';
import { useResumeStore } from './resumeStore';
import type { ResumeData } from '../types/resume';

const store = () => useResumeStore.getState();
const data = () => store().data;

beforeEach(() => {
  store().resetData();
});

describe('resumeStore 基础字段', () => {
  it('默认值与默认板块', () => {
    expect(data().templateId).toBe('classic');
    expect(data().accentColor).toBe('#2563eb');
    expect(data().sections).toHaveLength(8);
    expect(data().sections.find((s) => s.id === 'personal')?.visible).toBe(true);
    expect(data().sections.find((s) => s.id === 'internship')?.visible).toBe(false);
  });

  it('setTemplate / setAccentColor', () => {
    store().setTemplate('modern');
    store().setAccentColor('#ff0000');
    expect(data().templateId).toBe('modern');
    expect(data().accentColor).toBe('#ff0000');
  });

  it('updatePersonal 合并而非替换', () => {
    store().updatePersonal({ name: '张三' });
    store().updatePersonal({ email: 'z@example.com' });
    expect(data().personal.name).toBe('张三');
    expect(data().personal.email).toBe('z@example.com');
  });
});

describe('resumeStore 明细增删改', () => {
  it('education 增改删', () => {
    store().addEducation();
    expect(data().education).toHaveLength(1);
    const id = data().education[0].id;

    store().updateEducation(id, { school: '某大学' });
    expect(data().education[0].school).toBe('某大学');

    store().removeEducation(id);
    expect(data().education).toHaveLength(0);
  });

  it('experience 增改删', () => {
    store().addExperience();
    const id = data().experience[0].id;
    store().updateExperience(id, { company: '某公司' });
    expect(data().experience[0].company).toBe('某公司');
    store().removeExperience(id);
    expect(data().experience).toHaveLength(0);
  });

  it('internship 增删', () => {
    store().addInternship();
    expect(data().internship).toHaveLength(1);
    store().removeInternship(data().internship[0].id);
    expect(data().internship).toHaveLength(0);
  });

  it('skill 默认等级为 3', () => {
    store().addSkill();
    expect(data().skills[0].level).toBe(3);
    store().updateSkill(data().skills[0].id, { name: 'Java', level: 5 });
    expect(data().skills[0]).toMatchObject({ name: 'Java', level: 5 });
    store().removeSkill(data().skills[0].id);
    expect(data().skills).toHaveLength(0);
  });

  it('project / certificate / language 增删', () => {
    store().addProject();
    store().addCertificate();
    store().addLanguage();
    expect(data().projects).toHaveLength(1);
    expect(data().certificates).toHaveLength(1);
    expect(data().languages).toHaveLength(1);

    store().removeProject(data().projects[0].id);
    store().removeCertificate(data().certificates[0].id);
    store().removeLanguage(data().languages[0].id);
    expect(data().projects).toHaveLength(0);
    expect(data().certificates).toHaveLength(0);
    expect(data().languages).toHaveLength(0);
  });
});

describe('resumeStore 板块管理', () => {
  it('toggleSection 翻转可见性', () => {
    const before = data().sections.find((s) => s.id === 'education')!.visible;
    store().toggleSection('education');
    expect(data().sections.find((s) => s.id === 'education')!.visible).toBe(!before);
  });

  it('addCustomSection 同时创建内容与板块，removeCustomSection 一并移除', () => {
    const before = data().sections.length;
    store().addCustomSection();

    expect(data().customSections).toHaveLength(1);
    expect(data().sections).toHaveLength(before + 1);
    const custom = data().customSections[0];
    const section = data().sections.find((s) => s.id === custom.id);
    expect(section).toMatchObject({ type: 'custom', customId: custom.id, visible: true });

    store().removeCustomSection(custom.id);
    expect(data().customSections).toHaveLength(0);
    expect(data().sections).toHaveLength(before);
  });

  it('setSections 整体替换板块顺序', () => {
    const reversed = [...data().sections].reverse();
    store().setSections(reversed);
    expect(data().sections.map((s) => s.id)).toEqual(reversed.map((s) => s.id));
  });
});

describe('resumeStore 数据装载', () => {
  it('loadData 覆盖当前数据', () => {
    const incoming: ResumeData = { ...data(), title: '导入的简历', accentColor: '#123456' };
    store().loadData(incoming);
    expect(data().title).toBe('导入的简历');
    expect(data().accentColor).toBe('#123456');
  });

  it('resetData 恢复默认数据', () => {
    store().setTemplate('minimal');
    store().addSkill();
    store().resetData();

    expect(data().templateId).toBe('classic');
    expect(data().skills).toHaveLength(0);
    expect(data().sections).toHaveLength(8);
  });
});
