import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getPublicShare } from '../api/shares';
import type { ResumeDetail } from '../api/types';
import { getPublicWatermarkConfig } from '../api/watermark';
import type { WatermarkConfig } from '../api/watermark';
import { ResumeTemplate } from '../components/templates/ResumeTemplate';

/** 设置分享页 SEO / OpenGraph 元信息（标题/描述/图片，默认脱敏）。 */
function applySeoMeta(resume: ResumeDetail) {
  const name = resume.personal?.name || resume.title || '简历分享';
  const title = resume.personal?.title ? `${name} · ${resume.personal.title}` : name;
  const description = resume.personal?.summary || '查看这份在线简历';
  const image = resume.personal?.avatar || '';

  document.title = title;
  const setMeta = (property: string, content: string) => {
    let el = document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`);
    if (!el) {
      el = document.createElement('meta');
      el.setAttribute('property', property);
      document.head.appendChild(el);
    }
    el.setAttribute('content', content);
  };
  setMeta('og:title', title);
  setMeta('og:description', description);
  setMeta('og:type', 'profile');
  setMeta('og:url', window.location.href);
  if (image) setMeta('og:image', image);
  setMeta('twitter:card', 'summary');
  setMeta('twitter:title', title);
  setMeta('twitter:description', description);
}

/** 生成可视水印的重复文本 SVG 背景（转义文案，防止破坏 SVG）。 */
function watermarkBackground(text: string, opacity: number): string {
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='260' height='180'><text x='10' y='150' font-size='15' fill='rgba(0,0,0,${opacity})' transform='rotate(-20 130 90)'>${escaped}</text></svg>`;
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}

export function ShareViewPage() {
  const { key } = useParams<{ key: string }>();
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [viewCount, setViewCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [needPassword, setNeedPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [wmConfig, setWmConfig] = useState<WatermarkConfig | null>(null);

  const load = (pwd?: string) => {
    if (!key) return;
    setLoading(true);
    setNeedPassword(false);
    setError('');
    getPublicShare(key, pwd)
      .then((d) => {
        setResume(d.resume);
        setViewCount(d.viewCount);
        applySeoMeta(d.resume);
        setLoading(false);
      })
      .catch((err) => {
        setLoading(false);
        const code = (err as { code?: number })?.code;
        if (code === 403) {
          setNeedPassword(true);
          setError((err as Error)?.message || '需要访问密码');
        } else {
          setError((err as Error)?.message || '分享不存在或已失效');
        }
      });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => {
    getPublicWatermarkConfig()
      .then(setWmConfig)
      .catch(() => {
        /* 获取水印配置失败时静默降级为不显示水印 */
      });
  }, []);

  const renderTemplate = () => {
    if (!resume) return null;
    return <ResumeTemplate data={resume} />;
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center text-gray-500">加载中...</div>;
  }

  if (needPassword) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="bg-white rounded-xl shadow-lg p-8 w-80">
          <h1 className="text-lg font-semibold mb-2 text-gray-800">分享受密码保护</h1>
          {error !== '需要访问密码' && <p className="text-sm text-red-500 mb-3">{error}</p>}
          <form onSubmit={(e) => { e.preventDefault(); load(password); }} className="flex flex-col gap-3">
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入访问密码"
              className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              autoFocus
            />
            <button type="submit" className="bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700">查看</button>
          </form>
        </div>
      </div>
    );
  }

  if (error || !resume) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-gray-600">
        <p>{error || '分享不存在或已失效'}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col items-center py-8 px-4 relative">
      {/* 可视水印：威慑截屏转发，不阻断阅读，文案/透明度来自后端配置 */}
      {wmConfig?.enabled && wmConfig.visibleText && (
        <div
          aria-hidden
          className="absolute inset-0 pointer-events-none z-10"
          style={{ backgroundImage: watermarkBackground(wmConfig.visibleText, wmConfig.visibleOpacity) }}
        />
      )}
      <p className="text-xs text-gray-400 mb-4 relative z-20">此分享为只读预览 · 已被浏览 {viewCount} 次</p>
      <div className="w-full max-w-[794px] relative z-20">
        <div className="bg-white shadow-2xl rounded-sm" style={{ minHeight: '1123px' }}>
          {renderTemplate()}
        </div>
      </div>
    </div>
  );
}