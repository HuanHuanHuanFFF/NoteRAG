import type { InjectionKey } from 'vue';

export interface CitationContext {
  openCitation(index: number): void;
}

export const citationContextKey: InjectionKey<CitationContext> = Symbol('citationContext');
