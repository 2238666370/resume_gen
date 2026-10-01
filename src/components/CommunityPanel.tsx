import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listPosts, myCommunity } from '../api/community';
import type { CommunityPost, MyPost } from '../api/types';
import { PublishPostModal } from './PublishPostModal';
import { useAuthStore } from '../store/authStore';

const SIZE = 10;

type Tab = 'feed' | 'mine';
type MineTab = 'posts' | 'likes' | 'collects';

/**
 * 社区面板（F1）：从 CommunityPage 抽取，供「发现 · 社区」Tab 复用，不含页面壳。
 */
export function CommunityPanel() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);

  const [tab, setTab] = useState<Tab>('feed');
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<'latest' | 'hot'>('latest');
  const [tag, setTag] = useState<string | undefined>();
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [showPublish, setShowPublish] = useState(false);

  const [mineTab, setMineTab] = useState<MineTab>('posts');
  const [myPosts, setMyPosts] = useState<MyPost[]>([]);
  const [myLikes, setMyLikes] = useState<CommunityPost[]>([]);
  const [myCollects, setMyCollects] = useState<CommunityPost[]>([]);
  const [mineLoading, setMineLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listPosts(page, SIZE, tag, keyword || undefined, sort);
      setPosts(res.records);
      setTotal(res.total);
    } catch (err) {
      console.error('加载社区列表失败', err);
    } finally {
      setLoading(false);
    }
  }, [page, tag, keyword, sort]);

  useEffect(() => {
    load();
  }, [load]);

  const allTags = useMemo(() => {
    const set = new Set<string>();
    posts.forEach((p) => p.tags?.forEach((t) => set.add(t)));
    return Array.from(set);
  }, [posts]);

  const loadMine = useCallback(async () => {
    setMineLoading(true);
    try {
      const d = await myCommunity();
      setMyPosts(d.posts);
      setMyLikes(d.likes);
      setMyCollects(d.collects);
    } catch (err) {
      console.error('加载我的社区失败', err);
    } finally {
      setMineLoading(false);
    }
  }, []);

  const switchTab = (t: Tab) => {
    setTab(t);
    if (t === 'mine') {
      if (!token) {
        navigate('/login', { replace: true });
        return;
      }
      loadMine();
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / SIZE));

  const requireLogin = (fn: () => void) => {
    if (!token) {
      navigate('/login', { replace: true });
      return;
    }
    fn();
  };

  const statusText = (s: number) => {
    switch (s) {
      case 0: return '待审核';
      case 1: return '已上线';
      case 2: return '未通过';
      case 3: return '复审中';
      case 4: return '已下架';
      default: return '未知';
    }
  };

  return (
    <>
      <div className="flex items-center justify-between gap-4 mb-5 flex-wrap">
        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
          <button onClick={() => switchTab('feed')} className={`px-3 py-1 text-sm rounded-md ${tab === 'feed' ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500'}`}>
            信息流
          </button>
          <button onClick={() => switchTab('mine')} className={`px-3 py-1 text-sm rounded-md ${tab === 'mine' ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500'}`}>
            我的
          </button>
        </div>
        <button
          onClick={() => requireLogin(() => setShowPublish(true))}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
        >
          + 发布
        </button>
      </div>

      {tab === 'feed' ? (
        <>
          <div className="flex items-center gap-3 mb-5 flex-wrap">
            <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
              <button onClick={() => { setSort('latest'); setPage(1); }} className={`px-3 py-1 text-sm rounded-md ${sort === 'latest' ? 'bg-white shadow-sm' : 'text-gray-500'}`}>
                最新
              </button>
              <button onClick={() => { setSort('hot'); setPage(1); }} className={`px-3 py-1 text-sm rounded-md ${sort === 'hot' ? 'bg-white shadow-sm' : 'text-gray-500'}`}>
                最热
              </button>
            </div>
            <form
              onSubmit={(e) => { e.preventDefault(); setKeyword(keywordInput.trim()); setPage(1); }}
              className="flex items-center gap-2"
            >
              <input
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                placeholder="搜索标题 / 摘要 / 标签"
                className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-52 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button type="submit" className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">搜索</button>
              {keyword && (
                <button type="button" onClick={() => { setKeywordInput(''); setKeyword(''); setPage(1); }} className="text-sm text-gray-400 hover:text-gray-600">清除</button>
              )}
            </form>
            {allTags.map((t) => (
              <button
                key={t}
                onClick={() => { setTag(tag === t ? undefined : t); setPage(1); }}
                className={`px-3 py-1 text-sm rounded-full border ${tag === t ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-300 text-gray-600 hover:bg-gray-50'}`}
              >
                #{t}
              </button>
            ))}
          </div>

          {loading ? (
            <div className="text-center text-gray-400 py-20">加载中...</div>
          ) : posts.length === 0 ? (
            <div className="text-center py-20 text-gray-400">还没有帖子，点击「发布」分享你的第一份简历</div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {posts.map((p) => (
                <div
                  key={p.id}
                  className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow cursor-pointer"
                  onClick={() => navigate(`/community/${p.id}`)}
                >
                  <h2 className="font-medium text-gray-800 line-clamp-1">{p.title}</h2>
                  {p.summary && <p className="text-sm text-gray-400 mt-1.5 line-clamp-2">{p.summary}</p>}
                  <div className="flex items-center gap-1 mt-2 flex-wrap">
                    {p.tags?.map((t) => (
                      <span key={t} className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">#{t}</span>
                    ))}
                  </div>
                  <div className="flex items-center justify-between mt-4 text-xs text-gray-400">
                    <span>{p.authorNickname || '匿名'} · {p.createdAt ? p.createdAt.slice(5, 16) : ''}</span>
                    <div className="flex gap-3">
                      <span>👁 {p.viewCount}</span>
                      <span>👍 {p.likeCount}</span>
                      <span>⭐ {p.collectCount}</span>
                      <span>💬 {p.commentCount}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-4 mt-8">
              <button disabled={page <= 1} onClick={() => setPage(page - 1)} className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40">
                上一页
              </button>
              <span className="text-sm text-gray-500">{page} / {totalPages}</span>
              <button disabled={page >= totalPages} onClick={() => setPage(page + 1)} className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40">
                下一页
              </button>
            </div>
          )}
        </>
      ) : (
        <div>
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit mb-6">
            {(['posts', 'likes', 'collects'] as MineTab[]).map((t) => (
              <button key={t} onClick={() => setMineTab(t)} className={`px-4 py-1.5 text-sm rounded-md ${mineTab === t ? 'bg-white shadow-sm text-gray-800' : 'text-gray-500'}`}>
                {t === 'posts' ? '我发布的' : t === 'likes' ? '我点赞的' : '我收藏的'}
              </button>
            ))}
          </div>

          {mineLoading ? (
            <div className="text-center text-gray-400 py-20">加载中...</div>
          ) : (mineTab === 'posts' ? myPosts : mineTab === 'likes' ? myLikes : myCollects).length === 0 ? (
            <div className="text-center py-20 text-gray-400">暂无内容</div>
          ) : (
            <div className="flex flex-col gap-3">
              {(mineTab === 'posts' ? myPosts : mineTab === 'likes' ? myLikes : myCollects).map((p) => (
                <div key={p.id} className="bg-white rounded-xl border border-gray-200 p-4 flex items-center justify-between gap-3">
                  <button onClick={() => navigate(`/community/${p.id}`)} className="text-left flex-1 min-w-0">
                    <p className="font-medium text-gray-800 truncate">{p.title}</p>
                    <p className="text-xs text-gray-400 mt-1">
                      👍 {p.likeCount} · ⭐ {p.collectCount} · 💬 {p.commentCount} · 👁 {p.viewCount}
                    </p>
                  </button>
                  {mineTab === 'posts' && (
                    <span className="text-xs text-gray-400 flex-shrink-0">
                      {(p as MyPost).auditReason ? `${statusText((p as MyPost).auditStatus)}（${(p as MyPost).auditReason}）` : statusText((p as MyPost).auditStatus)}
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {showPublish && token && <PublishPostModal onClose={() => setShowPublish(false)} onPublished={() => { setShowPublish(false); setPage(1); load(); }} />}
    </>
  );
}
