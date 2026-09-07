import { describe, expect, it } from 'vitest';
import EmptyState from '@/components/EmptyState.vue';
import { mountWithVuetify } from '../helpers/mount';

describe('EmptyState', () => {
  it('renders the title and description', () => {
    const wrapper = mountWithVuetify(EmptyState, {
      props: { title: '没有数据', description: '换个条件试试' },
    });
    expect(wrapper.text()).toContain('没有数据');
    expect(wrapper.find('[aria-live="polite"]').text()).toBe('换个条件试试');
  });

  it('emits primary, secondary and action clicks', async () => {
    const wrapper = mountWithVuetify(EmptyState, {
      props: {
        primary: { label: '创建' },
        secondary: { label: '导入' },
        actions: [{ label: '帮助' }],
      },
    });

    const buttons = wrapper.findAll('button');
    await buttons[0].trigger('click');
    expect(wrapper.emitted('primary')).toHaveLength(1);

    await buttons[1].trigger('click');
    expect(wrapper.emitted('secondary')).toHaveLength(1);

    await buttons[2].trigger('click');
    expect(wrapper.emitted('action')).toEqual([[0]]);
  });

  it('renders no action buttons when the list is empty', () => {
    const wrapper = mountWithVuetify(EmptyState, {
      props: { primary: { label: '创建' }, actions: [] },
    });
    expect(wrapper.text()).not.toContain('帮助');
  });
});
