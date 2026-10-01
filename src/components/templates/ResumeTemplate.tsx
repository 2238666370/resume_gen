import { useEffect, useState } from 'react';
import type { ResumeData } from '../../types/resume';
import { fallbackLayout, parseSchema } from '../../types/schema';
import type { TemplateLayout } from '../../types/schema';
import { getTemplateSchema } from '../../api/templates';
import { ClassicTemplate } from './ClassicTemplate';
import { ModernTemplate } from './ModernTemplate';
import { MinimalTemplate } from './MinimalTemplate';
import { CustomSchemaTemplate } from './CustomSchemaTemplate';

interface Props {
  data: ResumeData;
  /** 可选：外部传入的 schema JSON 字符串（优先于自动获取）。 */
  schema?: string | null;
}

/**
 * 统一模板渲染入口（R8-A2）：按 schema.layout 分派到预设渲染器或自定义渲染器。
 * 官方三套（classic/modern/minimal）为内置预设；用户自定义模板走 CustomSchemaTemplate。
 */
export function ResumeTemplate({ data, schema }: Props) {
  const [resolvedSchema, setResolvedSchema] = useState<string | null>(schema ?? null);
  const code = data.templateId;

  useEffect(() => {
    // 预设模板无需拉取；自定义模板编码需按编码取 schema
    if (code === 'classic' || code === 'modern' || code === 'minimal' || schema) {
      setResolvedSchema(schema ?? null);
      return;
    }
    let cancelled = false;
    getTemplateSchema(code)
      .then((s) => { if (!cancelled) setResolvedSchema(s); })
      .catch(() => { if (!cancelled) setResolvedSchema(null); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, schema]);

  const parsed = parseSchema(resolvedSchema);
  const layout: TemplateLayout = parsed?.layout ?? fallbackLayout(code);

  if (layout === 'custom' && parsed) {
    return <CustomSchemaTemplate data={data} schema={parsed} />;
  }
  switch (layout) {
    case 'modern':
      return <ModernTemplate data={data} />;
    case 'minimal':
      return <MinimalTemplate data={data} />;
    case 'classic':
    default:
      return <ClassicTemplate data={data} />;
  }
}
