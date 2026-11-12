import { copyFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dist = fileURLToPath(new URL("../dist/", import.meta.url));
const index = join(dist, "index.html");
const routes = [
  "install",
  "apps",
  "cluster",
  "deck",
  "faq",
  "guides",
  "cars/jetour-t1",
  "cars/jetour-t2",
  "cars/jetour-dashing",
  "cars/other",
];

copyFileSync(index, join(dist, "404.html"));
for (const route of routes) {
  const dest = join(dist, route, "index.html");
  mkdirSync(dirname(dest), { recursive: true });
  copyFileSync(index, dest);
}
writeFileSync(join(dist, ".nojekyll"), "");
