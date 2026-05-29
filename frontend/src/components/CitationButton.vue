<script setup lang="ts">
import { computed, inject } from 'vue';
import { citationContextKey } from '@/components/citationContext';

type HtmlAttrs = Array<[string, string | null]>;

defineOptions({
  inheritAttrs: false,
});

const props = defineProps<{
  node?: {
    attrs?: HtmlAttrs | null;
    content?: string;
  };
  index?: string | number;
  disabled?: string | boolean;
  sourceId?: string | number;
  loading?: boolean;
  indexKey?: string | number;
  customId?: string;
  isDark?: boolean;
}>();

const citationContext = inject(citationContextKey, null);

const displayIndex = computed(() => {
  const value = props.index ?? readAttr('index') ?? props.node?.content;
  const index = Number(value);
  return Number.isInteger(index) && index > 0 ? index : null;
});

const disabled = computed(() => normalizeBoolean(props.disabled ?? readAttr('disabled')));

function readAttr(name: string): string | null {
  const attrs = props.node?.attrs;
  if (!attrs) return null;

  for (const [attrName, attrValue] of attrs) {
    if (attrName === name) return attrValue;
  }
  return null;
}

function normalizeBoolean(value: string | boolean | null | undefined): boolean {
  if (typeof value === 'boolean') return value;
  if (value == null) return false;
  return value !== 'false';
}

function handleClick() {
  if (disabled.value || displayIndex.value == null) return;
  citationContext?.openCitation(displayIndex.value);
}
</script>

<template>
  <button
    v-if="displayIndex != null"
    type="button"
    class="markdown-answer__citation"
    :data-citation-index="displayIndex"
    :disabled="disabled"
    :aria-disabled="disabled"
    :aria-label="`View source ${displayIndex}`"
    @click.stop="handleClick"
  >
    {{ displayIndex }}
  </button>
</template>
