const MARKER_PREFIX = '\uE200cite\uE202';
const MARKER_END = '\uE201';
const COMPLETE_MARKER_PATTERN = new RegExp(`${MARKER_PREFIX}([1-9]\\d*)${MARKER_END}`, 'g');

export interface CitationMarkerTransformResult {
  content: string;
  sourceIds: string[];
}

export function transformCitationMarkers(
  answer: string,
  options: { disabled: boolean; sourceIds?: Array<number | string> }
): CitationMarkerTransformResult {
  const sourceIds: string[] = [];
  const sourceIndexById = createSourceIndexById(options.sourceIds);
  const displayIndexBySourceId = sourceIndexById ?? new Map<string, number>();
  let cursor = 0;
  let content = '';

  for (const match of answer.matchAll(COMPLETE_MARKER_PATTERN)) {
    const markerStart = match.index ?? 0;
    const marker = match[0];
    const sourceId = match[1];

    content += escapeLiteralCitationTags(stripTrailingPartialMarker(answer.slice(cursor, markerStart)));

    let displayIndex = displayIndexBySourceId.get(sourceId);
    if (displayIndex == null) {
      if (sourceIndexById != null) {
        cursor = markerStart + marker.length;
        continue;
      }
      displayIndex = sourceIds.length + 1;
      displayIndexBySourceId.set(sourceId, displayIndex);
    }
    if (!sourceIds.includes(sourceId)) sourceIds.push(sourceId);

    content += renderCitationTag(sourceId, displayIndex, options.disabled);
    cursor = markerStart + marker.length;
  }

  content += escapeLiteralCitationTags(stripTrailingPartialMarker(answer.slice(cursor)));
  return { content, sourceIds };
}

function createSourceIndexById(sourceIds?: Array<number | string>): Map<string, number> | null {
  if (sourceIds == null) return null;

  const indexById = new Map<string, number>();
  for (const sourceId of sourceIds) {
    const normalizedSourceId = String(sourceId);
    if (!indexById.has(normalizedSourceId)) {
      indexById.set(normalizedSourceId, indexById.size + 1);
    }
  }
  return indexById;
}

function renderCitationTag(sourceId: string, index: number, disabled: boolean): string {
  return `<citation source-id="${sourceId}" index="${index}" disabled="${disabled ? 'true' : 'false'}">${index}</citation>`;
}

function stripTrailingPartialMarker(text: string): string {
  const markerStart = text.lastIndexOf(MARKER_PREFIX[0]);
  if (markerStart < 0) return text;

  const maybeMarker = text.slice(markerStart);
  if (MARKER_PREFIX.startsWith(maybeMarker)) {
    return text.slice(0, markerStart);
  }
  if (maybeMarker.startsWith(MARKER_PREFIX) && !maybeMarker.includes(MARKER_END)) {
    return text.slice(0, markerStart);
  }
  return text;
}

function escapeLiteralCitationTags(text: string): string {
  return text
    .replace(/<(\s*\/?\s*citation)(?=[\s>/])/gi, '&lt;$1')
    .replace(/(?<!!)(?<!\\)\[([1-9]\d*)\](?!\()/g, '\\[$1\\]');
}
