import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import MarkdownAnswer from './MarkdownAnswer.vue';
import type { SourceChunk } from '@/api/types';

function source(chunkId: number): SourceChunk {
  return {
    noteId: 1,
    chunkId,
    title: `source ${chunkId}`,
    headingPath: null,
    content: 'content',
    score: 0.9,
  };
}

function cite(chunkId: number): string {
  return `\uE200cite\uE202${chunkId}\uE201`;
}

describe('MarkdownAnswer', () => {
  it('renders markdown and escapes raw html', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: '## Title\n\n**bold** and `code` <span>raw</span>',
        sources: [],
      },
    });
    await flushPromises();

    expect(wrapper.find('h2').text()).toBe('Title');
    expect(wrapper.find('strong').text()).toBe('bold');
    expect(wrapper.find('code').text()).toBe('code');
    expect(wrapper.html()).not.toContain('<span>raw</span>');
    expect(wrapper.html()).toContain('&lt;span&gt;raw&lt;/span&gt;');
  });

  it('renders chunkId citation markers as first-appearance buttons', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: `first ${cite(218)} second ${cite(211)}`,
        sources: [source(218), source(211)],
      },
    });
    await flushPromises();

    const buttons = wrapper.findAll('button[data-citation-index]');
    expect(buttons).toHaveLength(2);
    expect(buttons.map((button) => button.text())).toEqual(['1', '2']);

    await buttons[0].trigger('click');

    expect(wrapper.emitted('open-citation')).toEqual([[1]]);
  });

  it('does not render enabled citations for chunks missing from sources', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: `known ${cite(218)} missing ${cite(999)}`,
        sources: [source(218)],
      },
    });
    await flushPromises();

    const buttons = wrapper.findAll('button[data-citation-index]');
    expect(buttons).toHaveLength(1);
    expect(buttons[0].text()).toBe('1');
    expect(wrapper.html()).not.toContain('source-id="999"');
    expect(wrapper.text()).not.toContain('\uE200');
  });

  it('disables citation buttons while streaming', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: `streaming ${cite(218)}`,
        sources: [source(218)],
        loading: true,
      },
    });
    await flushPromises();

    const button = wrapper.find('button[data-citation-index]');
    expect(button.attributes('disabled')).toBeDefined();

    await button.trigger('click');

    expect(wrapper.emitted('open-citation')).toBeUndefined();
  });

  it('does not render literal citation html from answer text as a button', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: '<citation source-id="1" index="1">1</citation>',
        sources: [],
      },
    });
    await flushPromises();

    expect(wrapper.find('button[data-citation-index]').exists()).toBe(false);
    expect(wrapper.text()).toContain('<citation');
    expect(wrapper.text()).toContain('</citation>');
  });

  it('keeps legacy bracket references as ordinary text', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: 'legacy [1] reference',
        sources: [source(211)],
      },
    });
    await flushPromises();

    expect(wrapper.find('button[data-citation-index]').exists()).toBe(false);
    expect(wrapper.text()).toContain('[1]');
  });
});
