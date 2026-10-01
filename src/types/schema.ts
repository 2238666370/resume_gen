import type { SectionType } from './resume';

/**
 * 模板渲染 Schema（R8-A2 统一渲染引擎的输入）。
 *
 * 官方三套内置模板以 layout 预设（classic/modern/minimal）驱动渲染；
 * 用户自定义模板以 layout=custom + sections 描述驱动通用渲染。
 */
export type TemplateLayout = 'classic' | 'modern' | 'minimal' | 'custom';

/** 自定义模板支持的板块类型（在 ResumeDTO 模块类型上扩展 summary）。 */
export type SchemaSectionType = SectionType | 'summary';

export interface SchemaSection {
  id: string;
  type: SchemaSectionType;
  title?: string;
  show?: boolean;
  /** 双栏布局时所属栏位（缺省按类型回退）。 */
  column?: 'left' | 'right';
}

/** 纹饰/主题配置：控制头部对齐、标题装饰、板块卡片样式等。 */
export interface TemplateTheme {
  /** 头部（姓名/职位/联系方式）对齐方式。 */
  headerStyle?: 'left' | 'center';
  /** 板块标题装饰样式。 */
  titleStyle?: 'plain' | 'bar' | 'underline' | 'capsule';
  /** 板块内容容器样式。 */
  sectionStyle?: 'plain' | 'card';
  /** 是否显示头像。 */
  showAvatar?: boolean;
  /** 是否显示联系方式（邮箱/电话/地址/网站）。 */
  showContacts?: boolean;
}

export interface TemplateSchema {
  schemaVersion?: number;
  layout: TemplateLayout;
  columns?: 1 | 2;
  theme?: TemplateTheme;
  sections?: SchemaSection[];
}

const PRESETS: TemplateLayout[] = ['classic', 'modern', 'minimal'];

export function isPresetLayout(layout: string): layout is TemplateLayout {
  return PRESETS.includes(layout as TemplateLayout);
}

/** 解析 schema JSON 字符串，失败返回 null（回退预设）。 */
export function parseSchema(schema: string | null | undefined): TemplateSchema | null {
  if (!schema) return null;
  try {
    const obj = JSON.parse(schema) as TemplateSchema;
    if (!obj || typeof obj.layout !== 'string') return null;
    return obj;
  } catch {
    return null;
  }
}

/** 按模板编码回退预设布局（未拿到 schema 或解析失败时）。 */
export function fallbackLayout(code: string): TemplateLayout {
  if (code === 'modern') return 'modern';
  if (code === 'minimal') return 'minimal';
  return 'classic';
}
