import type { ResumeData } from '../types/resume';

/** 模板预览用的示例简历数据。 */
export const SAMPLE_RESUME: ResumeData = {
  title: '示例简历',
  templateId: 'custom',
  accentColor: '#2563eb',
  personal: {
    name: '张三', title: '高级后端工程师', email: 'zhangsan@example.com', phone: '13800000000',
    location: '上海', website: 'https://example.com', avatar: '', summary: '多年后端开发经验，专注高并发系统与分布式架构。',
  },
  education: [{ id: 'e1', school: '示例大学', degree: '本科', major: '计算机科学与技术', startDate: '2014.09', endDate: '2018.06', gpa: '3.8', description: '' }],
  experience: [{ id: 'x1', company: '示例科技', position: '后端工程师', startDate: '2020.07', endDate: '', current: true, description: '负责核心服务开发与性能优化。' }],
  internship: [],
  skills: [{ id: 's1', name: 'Java', level: 5 }, { id: 's2', name: 'Spring Boot', level: 4 }],
  projects: [{ id: 'p1', name: '示例项目', role: '核心开发', startDate: '2021.01', endDate: '2022.06', description: '高并发交易系统。', link: '' }],
  certificates: [{ id: 'c1', name: 'CET-6', issuer: '', date: '2018', link: '' }],
  languages: [{ id: 'l1', name: '英语', level: 'CET-6' }],
  customSections: [],
  sections: [],
};
