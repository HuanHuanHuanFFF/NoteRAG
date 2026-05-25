import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
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
  it('renders markdown and escapes raw html', () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: '## Title\n\n**bold** and `code` <span>raw</span>',
        sources: [],
      },
    });

    expect(wrapper.find('h2').text()).toBe('Title');
    expect(wrapper.find('strong').text()).toBe('bold');
    expect(wrapper.find('code').text()).toBe('code');
    expect(wrapper.find('span').exists()).toBe(false);
    expect(wrapper.html()).toContain('&lt;span&gt;raw&lt;/span&gt;');
  });

  it('renders chunkId citation markers as source-order buttons', async () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: `first ${cite(218)} second ${cite(211)}`,
        sources: [source(211), source(218)],
      },
    });

    const buttons = wrapper.findAll('button[data-citation-index]');
    expect(buttons).toHaveLength(2);
    expect(buttons.map((button) => button.text())).toEqual(['2', '1']);

    await buttons[0].trigger('click');

    expect(wrapper.emitted('open-citation')).toEqual([[2]]);
  });

  it('keeps legacy bracket references as ordinary text', () => {
    const wrapper = mount(MarkdownAnswer, {
      props: {
        answer: 'legacy [1] reference',
        sources: [source(211)],
      },
    });

    expect(wrapper.find('button[data-citation-index]').exists()).toBe(false);
    expect(wrapper.text()).toContain('[1]');
  });
});
