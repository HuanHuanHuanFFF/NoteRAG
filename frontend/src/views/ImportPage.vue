<script setup lang="ts">
import { computed, ref } from 'vue';
import { NButton, NInput, NCard, NDescriptions, NDescriptionsItem, useMessage } from 'naive-ui';
import { importText } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { ImportTextResponse } from '@/api/types';

const title = ref('');
const content = ref('');
const submitting = ref(false);
const lastResult = ref<ImportTextResponse | null>(null);
const message = useMessage();

const titleValid = computed(() => title.value.trim().length > 0 && title.value.length <= 255);
const contentValid = computed(() => content.value.trim().length > 0 && content.value.length <= 100_000);
const canSubmit = computed(() => titleValid.value && contentValid.value && !submitting.value);

async function handleSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    lastResult.value = await importText({ title: title.value.trim(), content: content.value });
    message.success('导入成功');
    title.value = '';
    content.value = '';
  } catch (error) {
    const msg = error instanceof ApiError ? error.message : '导入失败，请稍后重试';
    message.error(msg);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="mx-auto flex h-full max-w-3xl flex-col gap-4 overflow-y-auto">
    <header>
      <h1 class="text-xl font-semibold text-slate-900">导入笔记</h1>
      <p class="mt-1 text-sm text-slate-500">
        粘贴 Markdown 文本进行导入，后端会自动切块并向量化。
      </p>
    </header>

    <n-card title="文档" size="small" class="bg-white">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-sm text-slate-600">标题</div>
          <n-input
            v-model:value="title"
            placeholder="如：MySQL 索引详解"
            maxlength="255"
            show-count
            :disabled="submitting"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-slate-600">Markdown 内容</div>
          <n-input
            v-model:value="content"
            type="textarea"
            placeholder="# 标题&#10;&#10;正文..."
            :autosize="{ minRows: 12, maxRows: 24 }"
            :maxlength="100000"
            show-count
            :disabled="submitting"
          />
        </div>
        <div class="flex justify-end">
          <n-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
            导入
          </n-button>
        </div>
      </div>
    </n-card>

    <n-card v-if="lastResult" title="最近一次导入结果" size="small" class="bg-white">
      <n-descriptions :column="2" label-placement="left" bordered size="small">
        <n-descriptions-item label="文档 ID">{{ lastResult.documentId }}</n-descriptions-item>
        <n-descriptions-item label="Chunk 数">{{ lastResult.chunkCount }}</n-descriptions-item>
        <n-descriptions-item label="字符数">{{ lastResult.charCount }}</n-descriptions-item>
        <n-descriptions-item label="预估 token">{{ lastResult.tokenCount }}</n-descriptions-item>
      </n-descriptions>
    </n-card>
  </div>
</template>
