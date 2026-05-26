import MarkdownIt from 'markdown-it';

export const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: false,
});
