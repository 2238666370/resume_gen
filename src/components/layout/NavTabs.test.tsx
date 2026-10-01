import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NavTabs } from './NavTabs';

const items = [
  { key: 'resumes' as const, label: '我的简历' },
  { key: 'shares' as const, label: '我的分享' },
];

describe('NavTabs', () => {
  it('渲染全部 Tab 与右侧附加内容', () => {
    render(
      <NavTabs items={items} value="resumes" onChange={vi.fn()} right={<button>新建</button>} />,
    );

    expect(screen.getByRole('button', { name: '我的简历' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '我的分享' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新建' })).toBeInTheDocument();
  });

  it('高亮当前选中项', () => {
    render(<NavTabs items={items} value="shares" onChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '我的分享' })).toHaveClass('font-medium');
    expect(screen.getByRole('button', { name: '我的简历' })).not.toHaveClass('font-medium');
  });

  it('点击 Tab 回调对应 key', async () => {
    const onChange = vi.fn();
    render(<NavTabs items={items} value="resumes" onChange={onChange} />);

    await userEvent.click(screen.getByRole('button', { name: '我的分享' }));
    expect(onChange).toHaveBeenCalledWith('shares');
  });

  it('不传 right 时不渲染右侧容器内容', () => {
    const { container } = render(<NavTabs items={items} value="resumes" onChange={vi.fn()} />);
    expect(container.querySelectorAll('button')).toHaveLength(2);
  });
});
