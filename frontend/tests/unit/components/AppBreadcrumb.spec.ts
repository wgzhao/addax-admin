import { describe, expect, it } from 'vitest';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import { mountWithVuetify } from '../helpers/mount';

describe('AppBreadcrumb', () => {
  it('renders breadcrumb items from route meta', () => {
    const wrapper = mountWithVuetify(AppBreadcrumb, {
      global: {
        mocks: {
          $route: {
            matched: [{ meta: { breadcrumbs: [{ title: '首页', to: '/' }, { title: '采集表' }] } }],
          },
        },
      },
    });
    expect(wrapper.text()).toContain('首页');
    expect(wrapper.text()).toContain('采集表');
  });
});
