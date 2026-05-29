import { describe, expect, it } from 'vitest';
import { transformCitationMarkers } from './citationMarkers';

function cite(chunkId: number): string {
  return `\uE200cite\uE202${chunkId}\uE201`;
}

describe('transformCitationMarkers', () => {
  it('converts one marker to a numbered citation tag', () => {
    const result = transformCitationMarkers(`answer ${cite(140)}`, { disabled: false });

    expect(result.sourceIds).toEqual(['140']);
    expect(result.content).toContain('<citation source-id="140" index="1" disabled="false">1</citation>');
  });

  it('reuses the same number for repeated chunk ids', () => {
    const result = transformCitationMarkers(`${cite(140)} and ${cite(140)}`, { disabled: false });

    expect(result.sourceIds).toEqual(['140']);
    expect(result.content.match(/index="1"/g)).toHaveLength(2);
  });

  it('numbers chunk ids by first marker appearance', () => {
    const result = transformCitationMarkers(`${cite(143)} ${cite(140)} ${cite(143)}`, {
      disabled: true,
    });

    expect(result.sourceIds).toEqual(['143', '140']);
    expect(result.content).toContain('<citation source-id="143" index="1" disabled="true">1</citation>');
    expect(result.content).toContain('<citation source-id="140" index="2" disabled="true">2</citation>');
  });

  it('uses source order when source ids are provided', () => {
    const result = transformCitationMarkers(`${cite(143)} ${cite(140)}`, {
      disabled: false,
      sourceIds: [140, 143],
    });

    expect(result.sourceIds).toEqual(['143', '140']);
    expect(result.content).toContain('<citation source-id="143" index="2" disabled="false">2</citation>');
    expect(result.content).toContain('<citation source-id="140" index="1" disabled="false">1</citation>');
  });

  it('removes markers that are not in the provided source ids', () => {
    const result = transformCitationMarkers(`known ${cite(140)} unknown ${cite(999)}`, {
      disabled: false,
      sourceIds: [140],
    });

    expect(result.sourceIds).toEqual(['140']);
    expect(result.content).toContain('<citation source-id="140" index="1" disabled="false">1</citation>');
    expect(result.content).not.toContain('source-id="999"');
    expect(result.content).not.toContain('\uE200');
  });

  it('strips trailing partial markers while streaming', () => {
    const result = transformCitationMarkers('answer \uE200cite\uE20214', { disabled: true });

    expect(result.content).toBe('answer ');
    expect(result.content).not.toContain('\uE200');
  });

  it('does not allow literal citation tags from answer text to become custom nodes', () => {
    const result = transformCitationMarkers('<citation source-id="1" index="1">1</citation>', {
      disabled: false,
    });

    expect(result.content).toContain('&lt;citation source-id="1" index="1">1&lt;/citation>');
  });

  it('keeps standalone numeric bracket references as text', () => {
    const result = transformCitationMarkers('legacy [1] reference and [link](https://example.com)', {
      disabled: false,
    });

    expect(result.content).toContain('legacy \\[1\\] reference');
    expect(result.content).toContain('[link](https://example.com)');
  });
});
