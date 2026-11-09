import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { ClusterFace, type GaugeKind, type GaugeLayout } from "@/components/cluster-face";
import { SiteFrame } from "@/components/site-frame";
import { Button } from "@/components/ui/button";
import {
  explainUsbError,
  probeCluster,
  restoreClusterDisplay,
  writeClusterTheme,
  type ClusterKey,
} from "@/lib/adb-session";
import { cn } from "@/lib/cn";

export const Route = createFileRoute("/cluster")({ component: ClusterPage });

const SNAP = "afaq-cluster-snap";

const LAYOUTS: { id: GaugeLayout; label: string; agency: boolean }[] = [
  { id: "classic", label: "دائرتان", agency: true },
  { id: "digital", label: "أرقام", agency: true },
  { id: "arcs", label: "أقواس", agency: false },
  { id: "bars", label: "أشرطة", agency: false },
  { id: "rings", label: "حلقات", agency: false },
  { id: "horizon", label: "خط", agency: false },
  { id: "sport", label: "رياضي", agency: false },
];

const PAIRS = [
  { id: "copper", label: "نحاسي", speed: "#E08A3C", rpm: "#E4C48A" },
  { id: "ice", label: "ثلجي", speed: "#9FCBD6", rpm: "#F4F1EA" },
  { id: "sand", label: "رملي", speed: "#E4C48A", rpm: "#C06D28" },
  { id: "race", label: "سباق", speed: "#D4533A", rpm: "#F2C14E" },
  { id: "glow", label: "فسفوري", speed: "#9DCC86", rpm: "#9FCBD6" },
];

const GROUNDS = [
  { id: "night", label: "ليل", value: "#14181A", ink: "#F4F1EA" },
  { id: "paper", label: "فاتح", value: "#E7E1D6", ink: "#1C211E" },
  { id: "dune", label: "كثبان", value: "#3A342C", ink: "#F3E6D0" },
  { id: "carbon", label: "كربون", value: "#101010", ink: "#F2F2F2" },
];

const KINDS: { id: GaugeKind; label: string }[] = [
  { id: "dial", label: "دائرة" },
  { id: "arc", label: "قوس" },
  { id: "bar", label: "شريط" },
  { id: "ring", label: "حلقة" },
  { id: "number", label: "رقم" },
];

function inkFor(hex: string) {
  const n = Number.parseInt(hex.slice(1), 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? "#1C211E" : "#F4F1EA";
}

function ClusterPage() {
  const [layout, setLayout] = useState<GaugeLayout>("classic");
  const [pairId, setPairId] = useState("copper");
  const [speed, setSpeed] = useState(PAIRS[0].speed);
  const [rpm, setRpm] = useState(PAIRS[0].rpm);
  const [groundId, setGroundId] = useState("night");
  const [groundHex, setGroundHex] = useState(GROUNDS[0].value);
  const [manualOn, setManualOn] = useState(false);
  const [speedKind, setSpeedKind] = useState<GaugeKind>("dial");
  const [rpmKind, setRpmKind] = useState<GaugeKind>("arc");
  const [thickness, setThickness] = useState(12);
  const [needle, setNeedle] = useState(true);
  const [photo, setPhoto] = useState<string | null>(null);
  const [dim, setDim] = useState(0.45);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState("وصّل الشاشة أول، وبعدها اقرأ العدادات قبل أي كتابة.");
  const [keys, setKeys] = useState<ClusterKey[]>([]);
  const [displays, setDisplays] = useState<number[]>([]);

  const clusterDisplay = displays.find((id) => id !== 0);
  const ink = photo ? "#F4F1EA" : inkFor(groundHex);

  useEffect(() => {
    return () => {
      if (photo) URL.revokeObjectURL(photo);
    };
  }, [photo]);

  function pickPair(id: string) {
    const pair = PAIRS.find((item) => item.id === id);
    if (!pair) return;
    setPairId(id);
    setSpeed(pair.speed);
    setRpm(pair.rpm);
  }

  function onPhoto(file: File | undefined) {
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setNote("الملف مو صورة. اختار صورة من الجهاز.");
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      setNote("الصورة أكبر من 8 ميغا. اختار صورة أخف عشان المعاينة تبقى خفيفة.");
      return;
    }
    const url = URL.createObjectURL(file);
    setPhoto((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return url;
    });
    setNote("الصورة صارت خلفية المعاينة فقط. ما تنرفع على شاشة العدادات ولا على كمبيوتر السيارة.");
  }

  function clearPhoto() {
    setPhoto((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return null;
    });
  }

  async function read() {
    setBusy("read");
    try {
      const probe = await probeCluster();
      setKeys(probe.keys);
      setDisplays(probe.displays);
      const numeric = probe.keys.find((item) => /^-?\d+$/.test(item.value));
      if (numeric && !localStorage.getItem(SNAP)) {
        localStorage.setItem(SNAP, JSON.stringify(numeric));
      }
      const screen = probe.displays.length
        ? `الشاشات: ${probe.displays.join("، ")}.`
        : "ما بان رقم شاشة منفصل.";
      const theme = numeric
        ? `ثيم العدادات الحالي ${numeric.key}=${numeric.value}.`
        : "ما لقينا مفتاح ثيم عدادات رقمي. ما نكتب إعداد عشوائي.";
      setNote(`${screen} ${theme}`);
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function apply() {
    if (manualOn || (layout !== "classic" && layout !== "digital")) {
      setNote(
        "هذا الشكل زيادة على ثيم الوكالة، فالمعاينة تتغير هنا والكتابة على السيارة ما تصير. الوكالة تقبل دائرتين أو أرقام. الصورة والألوان ما تنكتب على كمبيوتر العداد.",
      );
      return;
    }
    const numeric = keys.find((item) => /^-?\d+$/.test(item.value));
    if (!numeric) {
      setNote("اقرأ العدادات أولاً. إذا ما ظهر مفتاح ثيم، بدّل الشكل من السيارة: الإعدادات، ثم العدادات، ثم كلاسيك أو رقمي.");
      return;
    }
    if (!localStorage.getItem(SNAP)) {
      localStorage.setItem(SNAP, JSON.stringify(numeric));
    }
    setBusy("apply");
    try {
      const value = layout === "classic" ? "0" : "1";
      await writeClusterTheme(numeric.ns, numeric.key, value);
      setKeys([{ ...numeric, value }]);
      setNote(
        value === "0"
          ? "انكتب شكل الدائرتين. ألوان العدادات والصورة تبقى معاينة، ما تنكتب على الطبلون."
          : "انكتب شكل الأرقام. إذا العداد ما تغيّر، الشاشة تتجاهل المفتاح. ارجع للأصل.",
      );
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function restore() {
    setBusy("restore");
    const bits: string[] = [];
    try {
      const raw = localStorage.getItem(SNAP);
      if (raw) {
        const saved = JSON.parse(raw) as ClusterKey;
        if (saved && (saved.ns === "system" || saved.ns === "global") && /^-?\d{1,2}$/.test(saved.value)) {
          await writeClusterTheme(saved.ns, saved.key, saved.value);
          bits.push(`رجع المفتاح إلى ${saved.value}`);
        }
      } else {
        bits.push("ما فيه نسخة محفوظة للمفتاح");
      }
      if (clusterDisplay) {
        await restoreClusterDisplay(clusterDisplay);
        bits.push(`انرسل أمر الرئيسية لشاشة ${clusterDisplay}`);
      }
      clearPhoto();
      setLayout("classic");
      setManualOn(false);
      setSpeedKind("dial");
      setRpmKind("arc");
      setThickness(12);
      setNeedle(true);
      pickPair("copper");
      setGroundId("night");
      setGroundHex(GROUNDS[0].value);
      setNote(
        bits.length
          ? `${bits.join(". ")}. المعاينة رجعت لليل النحاسي. إذا بقي شكل غريب على الطبلون: الإعدادات ← العدادات.`
          : "المعاينة رجعت. ما انكتب شيء على السيارة قبلها، وما لقينا شاشة عدادات منفصلة.",
      );
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <SiteFrame>
      <div className="mx-auto max-w-6xl px-4 py-8 md:px-6">
        <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
          CLUSTER
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">شكل العدادات وألوانها</h1>
        <p className="mt-2 max-w-2xl text-sm text-muted">
          تختار شكلاً جاهزاً، أو تفصّل شكل السرعة وشكل الدورات بيديك، وتكتب لون كل عداد، وترفع صورة من جهازك كخلفية. الدائرتان والأرقام من ثيم الوكالة. الشكل اليدوي وباقي التصاميم معاينة وما تنكتب على كمبيوتر الطبلون.
        </p>

        <div className="mt-6">
          <ClusterFace
            layout={layout}
            speed={speed}
            rpm={rpm}
            ground={groundHex}
            ink={ink}
            photo={photo}
            dim={dim}
            manual={manualOn ? { speedKind, rpmKind, thickness, needle } : null}
          />
        </div>

        <fieldset className="mt-6">
          <legend className="text-sm font-medium">شكل العدادات</legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {LAYOUTS.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => {
                  setLayout(option.id);
                  setManualOn(false);
                }}
                className={cn(
                  "min-h-11 rounded-md border px-3 text-sm",
                  layout === option.id && !manualOn ? "border-copper bg-surface text-fg" : "border-line text-muted",
                )}
              >
                {option.label}
                {option.agency ? "" : " · زيادة"}
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset className="mt-4">
          <legend className="text-sm font-medium">شكل يدوي لكل عداد</legend>
          <div className="mt-2">
            <KindRow
              title="عداد السرعة"
              current={speedKind}
              active={manualOn}
              onPick={(id) => {
                setSpeedKind(id);
                setManualOn(true);
              }}
            />
            <KindRow
              title="عداد الدورات"
              current={rpmKind}
              active={manualOn}
              onPick={(id) => {
                setRpmKind(id);
                setManualOn(true);
              }}
            />
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-4">
            <label className="min-w-48 text-sm text-muted">
              سماكة الخط
              <input
                type="range"
                min={6}
                max={22}
                step={1}
                value={thickness}
                onChange={(event) => {
                  setThickness(Number(event.target.value));
                  setManualOn(true);
                }}
                className="mt-1 block w-full accent-copper"
              />
            </label>
            <button
              type="button"
              onClick={() => {
                setNeedle((value) => !value);
                setManualOn(true);
              }}
              className={cn(
                "min-h-11 rounded-md border px-3 text-sm",
                needle && manualOn ? "border-copper bg-surface text-fg" : "border-line text-muted",
              )}
            >
              {needle ? "الإبرة ظاهرة" : "بدون إبرة"}
            </button>
          </div>
        </fieldset>

        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <fieldset>
            <legend className="text-sm font-medium">ألوان العدادات</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {PAIRS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => pickPair(option.id)}
                  className={cn(
                    "min-h-11 rounded-md border px-3 text-sm",
                    pairId === option.id && speed === option.speed
                      ? "border-copper bg-surface text-fg"
                      : "border-line text-muted",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-4">
              <ColorField label="لون السرعة" value={speed} onChange={setSpeed} />
              <ColorField label="لون الدورات" value={rpm} onChange={setRpm} />
            </div>
          </fieldset>

          <fieldset>
            <legend className="text-sm font-medium">الخلفية</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {GROUNDS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => {
                  setGroundId(option.id);
                  setGroundHex(option.value);
                }}
                  className={cn(
                    "min-h-11 rounded-md border px-3 text-sm",
                    groundId === option.id && groundHex === option.value && !photo
                      ? "border-copper bg-surface text-fg"
                      : "border-line text-muted",
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <div className="mt-3">
              <ColorField label="لون الخلفية بخط اليد" value={groundHex} onChange={setGroundHex} />
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <label className="inline-flex min-h-11 cursor-pointer items-center rounded-md border border-line bg-surface px-4 text-sm">
                ارفع صورة من الجهاز
                <input
                  type="file"
                  accept="image/*"
                  className="sr-only"
                  onChange={(event) => {
                    onPhoto(event.target.files?.[0]);
                    event.target.value = "";
                  }}
                />
              </label>
              {photo ? (
                <Button variant="quiet" onClick={clearPhoto}>
                  احذف الصورة
                </Button>
              ) : null}
            </div>
            {photo ? (
              <label className="mt-3 block text-sm text-muted">
                تعتيم الصورة
                <input
                  type="range"
                  min={0.15}
                  max={0.8}
                  step={0.05}
                  value={dim}
                  onChange={(event) => setDim(Number(event.target.value))}
                  className="mt-1 block w-full accent-copper"
                />
              </label>
            ) : null}
          </fieldset>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <Button onClick={() => void read()} disabled={busy !== null}>
            {busy === "read" ? "جاري القراءة…" : "اقرأ العدادات"}
          </Button>
          <Button variant="line" onClick={() => void apply()} disabled={busy !== null}>
            {busy === "apply" ? "جاري التطبيق…" : "طبّق الشكل"}
          </Button>
          <Button variant="danger" onClick={() => void restore()} disabled={busy !== null}>
            {busy === "restore" ? "جاري الرجوع…" : "ارجع لشاشة العدادات الأصلية"}
          </Button>
        </div>
        <p className="mt-3 max-w-3xl text-sm text-muted">{note}</p>
      </div>
    </SiteFrame>
  );
}

function KindRow({
  title,
  current,
  active,
  onPick,
}: {
  title: string;
  current: GaugeKind;
  active: boolean;
  onPick: (id: GaugeKind) => void;
}) {
  return (
    <div className="mt-2">
      <p className="text-sm text-muted">{title}</p>
      <div className="mt-1 flex flex-wrap gap-2">
        {KINDS.map((option) => (
          <button
            key={option.id}
            type="button"
            onClick={() => onPick(option.id)}
            className={cn(
              "min-h-11 rounded-md border px-3 text-sm",
              active && current === option.id
                ? "border-copper bg-surface text-fg"
                : "border-line text-muted",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function ColorField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  return (
    <label className="flex flex-wrap items-center gap-2 text-sm text-muted">
      {label}
      <input
        type="color"
        value={value}
        aria-label={label}
        onChange={(event) => onChange(event.target.value.toUpperCase())}
        className="h-11 w-14 cursor-pointer rounded-md border border-line bg-transparent"
      />
      <input
        dir="ltr"
        value={draft}
        spellCheck={false}
        maxLength={7}
        aria-label={`${label} يدوياً`}
        onChange={(event) => {
          const next = event.target.value.toUpperCase();
          setDraft(next);
          if (/^#[0-9A-F]{6}$/.test(next)) onChange(next);
        }}
        className="h-11 w-28 rounded-md border border-line bg-surface px-2 font-mono text-sm text-fg"
      />
    </label>
  );
}
