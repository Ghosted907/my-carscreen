export type GaugeLayout =
  | "classic"
  | "digital"
  | "sport"
  | "arcs"
  | "bars"
  | "rings"
  | "horizon";

export type GaugeKind = "dial" | "arc" | "bar" | "ring" | "number";

export type ManualGauges = {
  speedKind: GaugeKind;
  rpmKind: GaugeKind;
  thickness: number;
  needle: boolean;
};

export function ClusterFace({
  layout,
  speed,
  rpm,
  ground,
  ink,
  photo,
  dim,
  manual,
}: {
  layout: GaugeLayout;
  speed: string;
  rpm: string;
  ground: string;
  ink: string;
  photo?: string | null;
  dim: number;
  manual?: ManualGauges | null;
}) {
  return (
    <div
      className="relative overflow-hidden rounded-2xl border border-line"
      style={{ background: ground, color: ink, aspectRatio: "21 / 9" }}
    >
      {photo ? (
        <img src={photo} alt="" className="absolute inset-0 h-full w-full object-cover" />
      ) : null}
      {photo ? (
        <div className="absolute inset-0" style={{ background: `rgba(8,10,9,${dim})` }} />
      ) : null}
      <svg viewBox="0 0 840 360" className="relative h-full w-full" role="img" aria-label="معاينة العدادات">
        {manual ? (
          <g>
            <OneGauge
              cx={210}
              kind={manual.speedKind}
              color={speed}
              ink={ink}
              label="كم/س"
              value="72"
              thickness={manual.thickness}
              needle={manual.needle}
              amount={0.62}
            />
            <OneGauge
              cx={630}
              kind={manual.rpmKind}
              color={rpm}
              ink={ink}
              label="×1000"
              value="2.4"
              thickness={manual.thickness}
              needle={manual.needle}
              amount={0.4}
            />
          </g>
        ) : layout === "classic" ? (
          <Classic speed={speed} rpm={rpm} ink={ink} />
        ) : layout === "digital" ? (
          <Digital speed={speed} rpm={rpm} ink={ink} />
        ) : layout === "sport" ? (
          <Sport speed={speed} rpm={rpm} ink={ink} />
        ) : layout === "arcs" ? (
          <Arcs speed={speed} rpm={rpm} ink={ink} />
        ) : layout === "bars" ? (
          <Bars speed={speed} rpm={rpm} ink={ink} />
        ) : layout === "rings" ? (
          <Rings speed={speed} rpm={rpm} ink={ink} />
        ) : (
          <Horizon speed={speed} rpm={rpm} ink={ink} />
        )}
      </svg>
    </div>
  );
}

function OneGauge({
  cx,
  kind,
  color,
  ink,
  label,
  value,
  thickness,
  needle,
  amount,
}: {
  cx: number;
  kind: GaugeKind;
  color: string;
  ink: string;
  label: string;
  value: string;
  thickness: number;
  needle: boolean;
  amount: number;
}) {
  const r = 108;
  const w = Math.min(22, Math.max(6, thickness));
  if (kind === "number") {
    return (
      <g>
        <text x={cx} y={186} textAnchor="middle" fill={color} fontSize="84" fontFamily="IBM Plex Sans Arabic, sans-serif">
          {value}
        </text>
        <text x={cx} y={226} textAnchor="middle" fill={ink} opacity="0.7" fontSize="18">
          {label}
        </text>
      </g>
    );
  }
  if (kind === "bar") {
    const width = 250;
    const x = cx - width / 2;
    const h = Math.max(w, 12);
    return (
      <g>
        <text x={cx} y={156} textAnchor="middle" fill={color} fontSize="48" fontFamily="IBM Plex Sans Arabic, sans-serif">
          {value}
        </text>
        <text x={cx} y={186} textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
          {label}
        </text>
        <rect x={x} y={210} width={width} height={h} rx={h / 2} fill={ink} opacity="0.18" />
        <rect x={x} y={210} width={width * amount} height={h} rx={h / 2} fill={color} />
      </g>
    );
  }
  if (kind === "ring") {
    return (
      <g>
        <circle cx={cx} cy={170} r={r} fill="none" stroke={ink} strokeOpacity="0.2" strokeWidth={w} />
        <path
          d={arc(cx, 170, r, 210, 210 + 220 * amount)}
          fill="none"
          stroke={color}
          strokeWidth={w}
          strokeLinecap="round"
        />
        <text x={cx} y={178} textAnchor="middle" fill={ink} fontSize="32" fontFamily="IBM Plex Sans Arabic, sans-serif">
          {value}
        </text>
        <text x={cx} y={300} textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
          {label}
        </text>
      </g>
    );
  }
  if (kind === "arc") {
    return (
      <g>
        <path d={arc(cx, 236, r, 190, 350)} fill="none" stroke={ink} strokeOpacity="0.2" strokeWidth={w} strokeLinecap="round" />
        <path
          d={arc(cx, 236, r, 190, 190 + 160 * amount)}
          fill="none"
          stroke={color}
          strokeWidth={w}
          strokeLinecap="round"
        />
        {needle ? <line x1={cx} y1={236} x2={cx} y2={150} stroke={color} strokeWidth="4" strokeLinecap="round" /> : null}
        <text x={cx} y={214} textAnchor="middle" fill={ink} fontSize="32" fontFamily="IBM Plex Sans Arabic, sans-serif">
          {value}
        </text>
        <text x={cx} y={318} textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
          {label}
        </text>
      </g>
    );
  }
  return (
    <g>
      <circle cx={cx} cy={175} r={r} fill="none" stroke={ink} strokeOpacity="0.22" strokeWidth={w} />
      <path
        d={arc(cx, 175, r, 140, 140 + 190 * amount)}
        fill="none"
        stroke={color}
        strokeWidth={w}
        strokeLinecap="round"
      />
      {needle ? (
        <>
          <line x1={cx} y1={175} x2={cx + 6} y2={84} stroke={color} strokeWidth="4" strokeLinecap="round" />
          <circle cx={cx} cy={175} r="6" fill={color} />
        </>
      ) : null}
      <text x={cx} y={204} textAnchor="middle" fill={ink} fontSize="28" fontFamily="IBM Plex Sans Arabic, sans-serif">
        {value}
      </text>
      <text x={cx} y={312} textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
        {label}
      </text>
    </g>
  );
}

function arc(cx: number, cy: number, r: number, start: number, end: number) {
  const p = (deg: number) => {
    const rad = (deg * Math.PI) / 180;
    return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
  };
  const [x1, y1] = p(start);
  const [x2, y2] = p(end);
  const large = Math.abs(end - start) > 180 ? 1 : 0;
  const sweep = end > start ? 1 : 0;
  return `M ${x1} ${y1} A ${r} ${r} 0 ${large} ${sweep} ${x2} ${y2}`;
}

function Dial({
  cx,
  label,
  value,
  color,
  ink,
}: {
  cx: number;
  label: string;
  value: string;
  color: string;
  ink: string;
}) {
  return (
    <g>
      <circle cx={cx} cy="180" r="108" fill="none" stroke={ink} strokeOpacity="0.22" strokeWidth="12" />
      <path
        d={arc(cx, 180, 108, 140, 330)}
        fill="none"
        stroke={color}
        strokeWidth="12"
        strokeLinecap="round"
      />
      <line x1={cx} y1="180" x2={cx + 6} y2="86" stroke={color} strokeWidth="4" strokeLinecap="round" />
      <circle cx={cx} cy="180" r="6" fill={color} />
      <text x={cx} y="204" textAnchor="middle" fill={ink} fontSize="28" fontFamily="IBM Plex Sans Arabic, sans-serif">
        {value}
      </text>
      <text x={cx} y="314" textAnchor="middle" fill={ink} opacity="0.72" fontSize="16" fontFamily="IBM Plex Sans Arabic, sans-serif">
        {label}
      </text>
    </g>
  );
}

function Classic({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <Dial cx={190} label="كم/س" value="72" color={speed} ink={ink} />
      <Dial cx={650} label="×1000" value="2.4" color={rpm} ink={ink} />
      <text x="420" y="176" textAnchor="middle" fill={ink} opacity="0.55" fontSize="22">
        P
      </text>
    </g>
  );
}

function Digital({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <text x="64" y="156" fill={speed} fontSize="84" fontFamily="IBM Plex Sans Arabic, sans-serif">
        72
      </text>
      <text x="64" y="196" fill={ink} opacity="0.7" fontSize="20">
        كم/س
      </text>
      <rect x="64" y="220" width="300" height="10" rx="5" fill={ink} opacity="0.18" />
      <rect x="64" y="220" width="128" height="10" rx="5" fill={speed} />
      <text x="500" y="156" fill={rpm} fontSize="72" fontFamily="IBM Plex Sans Arabic, sans-serif">
        2.4
      </text>
      <text x="500" y="196" fill={ink} opacity="0.7" fontSize="20">
        ألف لفة
      </text>
      <rect x="500" y="220" width="260" height="10" rx="5" fill={ink} opacity="0.18" />
      <rect x="500" y="220" width="96" height="10" rx="5" fill={rpm} />
    </g>
  );
}

function Sport({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <text x="48" y="214" fill={speed} fontSize="128" fontFamily="IBM Plex Sans Arabic, sans-serif">
        72
      </text>
      <text x="268" y="214" fill={ink} opacity="0.7" fontSize="22">
        كم/س
      </text>
      <circle cx="650" cy="180" r="104" fill="none" stroke={ink} strokeOpacity="0.2" strokeWidth="16" />
      <path d={arc(650, 180, 104, 150, 300)} fill="none" stroke={rpm} strokeWidth="16" strokeLinecap="round" />
      <text x="650" y="190" textAnchor="middle" fill={ink} fontSize="28">
        2.4
      </text>
    </g>
  );
}

function Arcs({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <path d={arc(210, 250, 130, 190, 350)} fill="none" stroke={ink} strokeOpacity="0.2" strokeWidth="16" strokeLinecap="round" />
      <path d={arc(210, 250, 130, 190, 305)} fill="none" stroke={speed} strokeWidth="16" strokeLinecap="round" />
      <text x="210" y="236" textAnchor="middle" fill={ink} fontSize="40">
        72
      </text>
      <text x="210" y="308" textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
        كم/س
      </text>
      <path d={arc(630, 250, 130, 190, 350)} fill="none" stroke={ink} strokeOpacity="0.2" strokeWidth="16" strokeLinecap="round" />
      <path d={arc(630, 250, 130, 190, 255)} fill="none" stroke={rpm} strokeWidth="16" strokeLinecap="round" />
      <text x="630" y="236" textAnchor="middle" fill={ink} fontSize="40">
        2.4
      </text>
      <text x="630" y="308" textAnchor="middle" fill={ink} opacity="0.7" fontSize="16">
        ألف لفة
      </text>
    </g>
  );
}

function Bars({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <text x="70" y="118" fill={ink} opacity="0.7" fontSize="18">
        السرعة
      </text>
      <text x="70" y="168" fill={speed} fontSize="48">
        72
      </text>
      <rect x="180" y="132" width="560" height="28" rx="14" fill={ink} opacity="0.15" />
      <rect x="180" y="132" width="240" height="28" rx="14" fill={speed} />
      <text x="70" y="230" fill={ink} opacity="0.7" fontSize="18">
        الدورات
      </text>
      <text x="70" y="280" fill={rpm} fontSize="48">
        2.4
      </text>
      <rect x="180" y="246" width="560" height="28" rx="14" fill={ink} opacity="0.15" />
      <rect x="180" y="246" width="168" height="28" rx="14" fill={rpm} />
    </g>
  );
}

function Rings({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <circle cx="280" cy="180" r="132" fill="none" stroke={ink} strokeOpacity="0.12" strokeWidth="8" />
      <path d={arc(280, 180, 132, 120, 310)} fill="none" stroke={speed} strokeWidth="8" strokeLinecap="round" />
      <circle cx="280" cy="180" r="96" fill="none" stroke={ink} strokeOpacity="0.18" strokeWidth="8" />
      <path d={arc(280, 180, 96, 200, 330)} fill="none" stroke={speed} strokeOpacity="0.55" strokeWidth="8" strokeLinecap="round" />
      <text x="280" y="176" textAnchor="middle" fill={ink} fontSize="52">
        72
      </text>
      <text x="280" y="208" textAnchor="middle" fill={ink} opacity="0.65" fontSize="16">
        كم/س
      </text>
      <circle cx="640" cy="180" r="78" fill="none" stroke={ink} strokeOpacity="0.18" strokeWidth="10" />
      <path d={arc(640, 180, 78, 160, 280)} fill="none" stroke={rpm} strokeWidth="10" strokeLinecap="round" />
      <text x="640" y="176" textAnchor="middle" fill={ink} fontSize="28">
        2.4
      </text>
      <text x="640" y="204" textAnchor="middle" fill={ink} opacity="0.65" fontSize="14">
        ×1000
      </text>
    </g>
  );
}

function Horizon({ speed, rpm, ink }: { speed: string; rpm: string; ink: string }) {
  return (
    <g>
      <text x="48" y="150" fill={speed} fontSize="92">
        72
      </text>
      <text x="210" y="150" fill={ink} opacity="0.7" fontSize="20">
        كم/س
      </text>
      <rect x="48" y="188" width="620" height="6" rx="3" fill={ink} opacity="0.2" />
      <rect x="48" y="188" width="260" height="6" rx="3" fill={speed} />
      <rect x="700" y="70" width="14" height="220" rx="7" fill={ink} opacity="0.15" />
      <rect x="700" y="176" width="14" height="114" rx="7" fill={rpm} />
      <text x="748" y="196" fill={ink} fontSize="22">
        2.4
      </text>
    </g>
  );
}
