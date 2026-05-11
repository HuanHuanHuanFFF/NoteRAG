import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const httpDir = join(scriptDir, "..");
const markdownPath = join(httpDir, "payloads", "import-text.md");
const outputPath = join(httpDir, "payloads", "import-text.generated.json");

const content = readFileSync(markdownPath, "utf8");
const firstHeading = content.match(/^#\s+(.+)$/m);
const title = firstHeading ? firstHeading[1].trim() : "Markdown Note";

writeFileSync(
        outputPath,
        JSON.stringify({ title, content }, null, 2) + "\n",
        "utf8");
