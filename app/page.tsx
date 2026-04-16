'use client';

import React, { useState, useEffect, useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Area,
  AreaChart,
  ReferenceLine,
  ReferenceDot,
} from 'recharts';

/* ============ THEME TOKENS ============ */
const colors = {
  bg: '#EEE7D8',
  bgAlt: '#E5DDCB',
  ink: '#1A1613',
  inkSoft: '#4A4238',
  muted: '#8A8071',
  line: '#C9BFA8',
  accent: '#B8442B',
  accentSoft: '#D97A3C',
  success: '#4A7C59',
};

const fontSerif = "var(--font-fraunces), Georgia, serif";
const fontSans = "var(--font-ibm-plex-sans), system-ui, sans-serif";
const fontMono = "var(--font-jetbrains-mono), monospace";

/* ============ ROOT ============ */
export default function Sistema() {
  const [tab, setTab] = useState('home');

  return (
    <div
      style={{
        minHeight: '100vh',
        background: colors.bg,
        color: colors.ink,
        fontFamily: fontSans,
        fontSize: '16px',
        lineHeight: 1.5,
      }}
    >
      <Nav tab={tab} setTab={setTab} />
      <main style={{ maxWidth: '1100px', margin: '0 auto', padding: '2rem 2rem 6rem' }}>
        {tab === 'home' && <Home setTab={setTab} />}
        {tab === 'builder' && <Builder />}
        {tab === 'tracker' && <Tracker />}
        {tab === 'log' && <DailyLog />}
        {tab === 'research' && <Research />}
      </main>
      <Footer />
    </div>
  );
}

/* ============ NAV ============ */
function Nav({ tab, setTab }: { tab: string; setTab: (t: string) => void }) {
  const tabs = [
    { id: 'home', label: 'Home' },
    { id: 'builder', label: 'System Builder' },
    { id: 'tracker', label: '66-Day Tracker' },
    { id: 'log', label: 'Daily Log' },
    { id: 'research', label: 'The Research' },
  ];

  return (
    <header
      style={{
        borderBottom: `1px solid ${colors.line}`,
        position: 'sticky',
        top: 0,
        background: colors.bg,
        zIndex: 10,
      }}
    >
      <div
        style={{
          maxWidth: '1100px',
          margin: '0 auto',
          padding: '1.25rem 2rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div onClick={() => setTab('home')} style={{ cursor: 'pointer' }}>
          <div
            style={{
              fontFamily: fontSerif,
              fontSize: '28px',
              fontWeight: 500,
              letterSpacing: '0.02em',
              lineHeight: 1,
            }}
          >
            SISTEMA
          </div>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              color: colors.muted,
              letterSpacing: '0.15em',
              marginTop: '4px',
              textTransform: 'uppercase',
            }}
          >
            A system for when motivation isn&apos;t
          </div>
        </div>
        <nav style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              style={{
                background: 'none',
                border: 'none',
                padding: '4px 0',
                fontFamily: fontSans,
                fontSize: '14px',
                fontWeight: tab === t.id ? 500 : 400,
                color: tab === t.id ? colors.ink : colors.inkSoft,
                borderBottom:
                  tab === t.id
                    ? `2px solid ${colors.accent}`
                    : '2px solid transparent',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </div>
    </header>
  );
}

/* ============ HOME ============ */
function Home({ setTab }: { setTab: (t: string) => void }) {
  const [stats, setStats] = useState([0, 0, 0]);
  const targets = [100, 80, 90];

  useEffect(() => {
    const duration = 1400;
    const start = performance.now();
    const raf = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      setStats(targets.map((v) => Math.round(v * eased)));
      if (t < 1) requestAnimationFrame(raf);
    };
    requestAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div>
      {/* Hero */}
      <section style={{ padding: '4rem 0 3rem', borderBottom: `1px solid ${colors.line}` }}>
        <div
          style={{
            fontFamily: fontMono,
            fontSize: '11px',
            letterSpacing: '0.2em',
            color: colors.accent,
            textTransform: 'uppercase',
            marginBottom: '1.5rem',
          }}
        >
          WRS 106 &nbsp;/&nbsp; Assignment 4 &nbsp;/&nbsp; Synthesis Remix
        </div>
        <h1
          style={{
            fontFamily: fontSerif,
            fontSize: 'clamp(44px, 7vw, 88px)',
            fontWeight: 400,
            lineHeight: 0.95,
            letterSpacing: '-0.02em',
            margin: '0 0 2rem',
            maxWidth: '900px',
          }}
        >
          Build the system
          <br />
          <span style={{ fontStyle: 'italic', color: colors.accent }}>
            that works when motivation doesn&apos;t.
          </span>
        </h1>
        <p
          style={{
            fontSize: '20px',
            lineHeight: 1.5,
            maxWidth: '640px',
            color: colors.inkSoft,
            margin: '0 0 2.5rem',
            fontWeight: 300,
          }}
        >
          Most students blame laziness. The real problem is architecture. SISTEMA takes three peer
          reviewed findings and turns them into tools you can actually use in the next five minutes.
        </p>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          <PrimaryButton onClick={() => setTab('builder')}>Start building →</PrimaryButton>
          <SecondaryButton onClick={() => setTab('research')}>See the research</SecondaryButton>
        </div>
      </section>

      {/* Survey stats section */}
      <section style={{ padding: '4rem 0', borderBottom: `1px solid ${colors.line}` }}>
        <SectionHeader
          eyebrow="The Argument"
          title="The belief gap"
          subtitle="In a survey of 20 WRS 106 students, the data showed something most of them hadn't noticed: they already live by systems. They just don't know it."
        />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: '1px',
            background: colors.line,
            border: `1px solid ${colors.line}`,
            marginTop: '3rem',
          }}
        >
          {[
            {
              n: stats[0],
              unit: '%',
              label: 'said they are more productive when they feel motivated.',
              tag: 'The belief',
            },
            {
              n: stats[1],
              unit: '%',
              label: 'finish tasks even when they do not feel like doing them.',
              tag: 'The behavior',
            },
            {
              n: stats[2],
              unit: '%',
              label: 'rated having a schedule or plan a 4 or 5 out of 5.',
              tag: 'The opening',
            },
          ].map((s, i) => (
            <div key={i} style={{ background: colors.bg, padding: '2.5rem 2rem' }}>
              <div
                style={{
                  fontFamily: fontMono,
                  fontSize: '10px',
                  letterSpacing: '0.2em',
                  color: colors.accent,
                  textTransform: 'uppercase',
                  marginBottom: '1rem',
                }}
              >
                {s.tag}
              </div>
              <div
                style={{
                  fontFamily: fontSerif,
                  fontSize: '72px',
                  fontWeight: 400,
                  lineHeight: 1,
                  letterSpacing: '-0.03em',
                }}
              >
                {s.n}
                <span style={{ color: colors.accent }}>{s.unit}</span>
              </div>
              <div
                style={{
                  fontSize: '15px',
                  lineHeight: 1.5,
                  color: colors.inkSoft,
                  marginTop: '1rem',
                  maxWidth: '280px',
                }}
              >
                {s.label}
              </div>
            </div>
          ))}
        </div>
        <div
          style={{
            marginTop: '2.5rem',
            padding: '2rem',
            background: colors.ink,
            color: colors.bg,
          }}
        >
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              letterSpacing: '0.2em',
              color: colors.accentSoft,
              textTransform: 'uppercase',
              marginBottom: '1rem',
            }}
          >
            The contradiction
          </div>
          <p
            style={{
              fontFamily: fontSerif,
              fontSize: '26px',
              lineHeight: 1.3,
              fontWeight: 300,
              margin: 0,
              fontStyle: 'italic',
            }}
          >
            You already finish work on the days you do not feel like it. You just haven&apos;t
            connected that pattern to the system quietly doing the work for you.
          </p>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '11px',
              color: colors.muted,
              marginTop: '1.5rem',
            }}
          >
            Source: Martins, 2026. Class survey. N = 20.
          </div>
        </div>
      </section>

      {/* The three tools */}
      <section style={{ padding: '4rem 0', borderBottom: `1px solid ${colors.line}` }}>
        <SectionHeader
          eyebrow="The Toolkit"
          title="Three findings. Three tools. Five minutes."
          subtitle="Every tab in this site turns a single peer reviewed study into something you can use. The research is not in a footnote. It is the product."
        />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            gap: '1.5rem',
            marginTop: '3rem',
          }}
        >
          <ToolCard
            num="01"
            title="System Builder"
            source="Gollwitzer, 1999"
            body="Turn a vague goal into a specific if then plan. Watch the specificity meter climb as your system sharpens."
            onClick={() => setTab('builder')}
          />
          <ToolCard
            num="02"
            title="66-Day Tracker"
            source="Lally et al., 2010"
            body="The real habit formation curve. 66 days to automatic. Most people quit at week two. Now you can see exactly where you are."
            onClick={() => setTab('tracker')}
          />
          <ToolCard
            num="03"
            title="Daily Log"
            source="Martins, 2026"
            body="Log whether you finished your system and how you felt. The tool tells you whether your motivation or your structure is doing the work."
            onClick={() => setTab('log')}
          />
        </div>
      </section>

      {/* Personal */}
      <section style={{ padding: '4rem 0' }}>
        <div style={{ maxWidth: '720px' }}>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '11px',
              letterSpacing: '0.2em',
              color: colors.accent,
              textTransform: 'uppercase',
              marginBottom: '1rem',
            }}
          >
            Why this exists
          </div>
          <p
            style={{
              fontFamily: fontSerif,
              fontSize: '28px',
              lineHeight: 1.4,
              fontWeight: 300,
              margin: 0,
            }}
          >
            I lived on the wrong side of this for a long time. Locked in one day, unmotivated the
            next, and the second kind of day always won. Things changed once I stopped waiting to
            feel ready and built routines I could return to no matter how I felt. That shift is what
            this site is trying to pass on.
          </p>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '11px',
              color: colors.muted,
              marginTop: '2rem',
              letterSpacing: '0.15em',
              textTransform: 'uppercase',
            }}
          >
            — Nicolas Martins
          </div>
        </div>
      </section>
    </div>
  );
}

/* ============ BUILDER ============ */
function Builder() {
  const [goal, setGoal] = useState('');
  const [trigger, setTrigger] = useState('');
  const [action, setAction] = useState('');

  const score = useMemo(() => {
    let s = 0;
    if (goal.trim().length >= 3) s += 25;
    if (trigger.trim().length >= 3) s += 35;
    if (action.trim().length >= 3) s += 25;
    const specifics =
      /\b(\d+|minutes|min|am|pm|after|before|o'?clock|morning|evening|night|gym|desk|page|pages|book|call)\b/i;
    if (specifics.test(trigger)) s += 7;
    if (specifics.test(action)) s += 8;
    return Math.min(100, s);
  }, [goal, trigger, action]);

  const tier =
    score < 30
      ? 'Wish'
      : score < 60
        ? 'Forming'
        : score < 90
          ? 'Almost'
          : 'Complete system';
  const tierColor =
    score < 30
      ? colors.muted
      : score < 60
        ? colors.accentSoft
        : score < 90
          ? colors.accent
          : colors.success;

  const assembled =
    trigger && action
      ? `If it is ${trigger}, I will ${action}.`
      : 'Fill in the fields to assemble your system.';

  return (
    <div>
      <TabHeader
        eyebrow="Tab 02 / System Builder"
        title="If this, then that."
        subtitle="Gollwitzer (1999) showed specific if then plans increase follow through significantly more than vague general goals. This is the mechanism."
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
          gap: '3rem',
          marginTop: '3rem',
        }}
      >
        {/* Left: Inputs */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <Field
            step="01"
            label="The goal"
            hint="What do you actually want? Write it plainly."
            placeholder="Study for my finance midterm"
            value={goal}
            onChange={setGoal}
          />
          <Field
            step="02"
            label="The trigger"
            hint="A specific time or cue. Not 'later', not 'tonight'. A real moment."
            placeholder="it is 7pm and I have eaten dinner"
            value={trigger}
            onChange={setTrigger}
          />
          <Field
            step="03"
            label="The action"
            hint="Exactly what you will do. Include the amount."
            placeholder="open my notebook and review 5 pages for 45 minutes"
            value={action}
            onChange={setAction}
          />
        </div>

        {/* Right: Output */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <div
            style={{
              background: colors.ink,
              color: colors.bg,
              padding: '2.5rem 2rem',
              position: 'relative',
            }}
          >
            <div
              style={{
                fontFamily: fontMono,
                fontSize: '10px',
                letterSpacing: '0.2em',
                color: colors.accentSoft,
                textTransform: 'uppercase',
                marginBottom: '1.5rem',
              }}
            >
              Your implementation intention
            </div>
            <p
              style={{
                fontFamily: fontSerif,
                fontSize: '28px',
                lineHeight: 1.35,
                fontWeight: 300,
                margin: 0,
                fontStyle: trigger && action ? 'normal' : 'italic',
                color: trigger && action ? colors.bg : colors.muted,
                minHeight: '160px',
              }}
            >
              {assembled}
            </p>
          </div>

          <div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                marginBottom: '0.75rem',
              }}
            >
              <div
                style={{
                  fontFamily: fontMono,
                  fontSize: '10px',
                  letterSpacing: '0.2em',
                  textTransform: 'uppercase',
                  color: colors.muted,
                }}
              >
                Specificity meter
              </div>
              <div
                style={{
                  fontFamily: fontMono,
                  fontSize: '11px',
                  color: tierColor,
                  fontWeight: 500,
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                }}
              >
                {tier} &nbsp;&middot;&nbsp; {score}/100
              </div>
            </div>
            <div
              style={{
                height: '8px',
                background: colors.line,
                position: 'relative',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  height: '100%',
                  width: `${score}%`,
                  background: tierColor,
                  transition: 'width 0.4s ease-out, background 0.4s',
                }}
              />
            </div>
            <div
              style={{
                fontSize: '14px',
                color: colors.inkSoft,
                marginTop: '1.5rem',
                lineHeight: 1.6,
              }}
            >
              {score < 30 &&
                'A goal alone is a wish. Add a time trigger and a specific action.'}
              {score >= 30 &&
                score < 60 &&
                "You have the pieces. Make them specific. Replace 'later' with a real time. Replace 'study' with 'read 5 pages'."}
              {score >= 60 &&
                score < 90 &&
                'Getting close. A few more concrete details push this from intention into system.'}
              {score >= 90 &&
                "This is a complete implementation intention. Gollwitzer's research predicts your follow through rate just went up significantly."}
            </div>
          </div>
        </div>
      </div>

      <CitationBar text="Gollwitzer, P. M. (1999). Implementation intentions: Strong effects of simple plans. American Psychologist, 54(7), 493–503." />
    </div>
  );
}

function Field({
  step,
  label,
  hint,
  placeholder,
  value,
  onChange,
}: {
  step: string;
  label: string;
  hint: string;
  placeholder: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '1rem', marginBottom: '0.5rem' }}>
        <span
          style={{
            fontFamily: fontMono,
            fontSize: '11px',
            color: colors.accent,
            letterSpacing: '0.15em',
          }}
        >
          {step}
        </span>
        <label
          style={{
            fontFamily: fontSerif,
            fontSize: '22px',
            fontWeight: 400,
          }}
        >
          {label}
        </label>
      </div>
      <div
        style={{
          fontSize: '13px',
          color: colors.muted,
          marginBottom: '0.75rem',
          lineHeight: 1.5,
        }}
      >
        {hint}
      </div>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        style={{
          width: '100%',
          padding: '0.9rem 1rem',
          fontFamily: fontMono,
          fontSize: '14px',
          background: colors.bgAlt,
          border: `1px solid ${colors.line}`,
          color: colors.ink,
          outline: 'none',
          transition: 'border-color 0.2s',
          boxSizing: 'border-box',
        }}
        onFocus={(e) => (e.target.style.borderColor = colors.accent)}
        onBlur={(e) => (e.target.style.borderColor = colors.line)}
      />
    </div>
  );
}

/* ============ TRACKER ============ */
function Tracker() {
  const [completed, setCompleted] = useState<Set<number>>(new Set());
  const [currentDay, setCurrentDay] = useState(1);

  const toggleDay = (day: number) => {
    const next = new Set(completed);
    if (next.has(day)) next.delete(day);
    else next.add(day);
    setCompleted(next);
    const max = Math.max(0, ...Array.from(next));
    setCurrentDay(Math.min(66, max + 1));
  };

  const T = 22;
  const automaticityAt = (t: number) => Math.round(95 * (1 - Math.exp(-t / T)));

  const curveData = useMemo(() => {
    return Array.from({ length: 67 }, (_, i) => ({
      day: i,
      automaticity: automaticityAt(i),
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const daysLogged = completed.size;
  const currentAutomaticity = automaticityAt(daysLogged);
  const daysToAutomatic = Math.max(0, 66 - daysLogged);

  const streak = useMemo(() => {
    let s = 0;
    const sorted = Array.from(completed).sort((a, b) => b - a);
    if (sorted.length === 0) return 0;
    s = 1;
    let prev = sorted[0];
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i] === prev - 1) {
        s++;
        prev = sorted[i];
      } else break;
    }
    return s;
  }, [completed]);

  return (
    <div>
      <TabHeader
        eyebrow="Tab 03 / 66-Day Tracker"
        title="66 days. Not 21."
        subtitle="Lally et al. (2010) found habits take an average of 66 days to become automatic. The early weeks feel hard because that is where the curve is steepest, not because something is wrong."
      />

      {/* Stats */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1px',
          background: colors.line,
          border: `1px solid ${colors.line}`,
          margin: '3rem 0',
        }}
      >
        <StatCell label="Days logged" value={daysLogged} unit="/ 66" />
        <StatCell label="Current automaticity" value={currentAutomaticity} unit="%" accent />
        <StatCell label="Days to automatic" value={daysToAutomatic} />
        <StatCell label="Current streak" value={streak} />
      </div>

      {/* Curve */}
      <div style={{ marginBottom: '3rem' }}>
        <div
          style={{
            fontFamily: fontMono,
            fontSize: '10px',
            letterSpacing: '0.2em',
            color: colors.muted,
            textTransform: 'uppercase',
            marginBottom: '0.75rem',
          }}
        >
          The automaticity curve
        </div>
        <div
          style={{
            width: '100%',
            height: 260,
            background: colors.bgAlt,
            padding: '1.5rem',
            boxSizing: 'border-box',
          }}
        >
          <ResponsiveContainer>
            <AreaChart data={curveData} margin={{ top: 10, right: 20, left: 0, bottom: 20 }}>
              <defs>
                <linearGradient id="curveFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={colors.accent} stopOpacity={0.35} />
                  <stop offset="100%" stopColor={colors.accent} stopOpacity={0.05} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={colors.line} strokeDasharray="2 4" vertical={false} />
              <XAxis
                dataKey="day"
                stroke={colors.muted}
                tick={{ fontFamily: fontMono, fontSize: 11 }}
                label={{
                  value: 'Day',
                  position: 'insideBottom',
                  offset: -10,
                  style: { fontFamily: fontMono, fontSize: 11, fill: colors.muted },
                }}
              />
              <YAxis
                stroke={colors.muted}
                tick={{ fontFamily: fontMono, fontSize: 11 }}
                label={{
                  value: '% automatic',
                  angle: -90,
                  position: 'insideLeft',
                  style: { fontFamily: fontMono, fontSize: 11, fill: colors.muted },
                }}
                domain={[0, 100]}
              />
              <Tooltip
                contentStyle={{
                  background: colors.ink,
                  border: 'none',
                  color: colors.bg,
                  fontFamily: fontMono,
                  fontSize: 12,
                }}
                formatter={(v) => [`${v}%`, 'automaticity']}
                labelFormatter={(l) => `Day ${l}`}
              />
              <Area
                type="monotone"
                dataKey="automaticity"
                stroke={colors.accent}
                strokeWidth={2}
                fill="url(#curveFill)"
              />
              {daysLogged > 0 && (
                <ReferenceDot
                  x={daysLogged}
                  y={currentAutomaticity}
                  r={6}
                  fill={colors.ink}
                  stroke={colors.bg}
                  strokeWidth={2}
                />
              )}
              <ReferenceLine
                x={14}
                stroke={colors.muted}
                strokeDasharray="3 3"
                label={{
                  value: 'Most quit here',
                  position: 'top',
                  style: { fontFamily: fontMono, fontSize: 10, fill: colors.muted },
                }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Grid */}
      <div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            marginBottom: '1rem',
          }}
        >
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              letterSpacing: '0.2em',
              color: colors.muted,
              textTransform: 'uppercase',
            }}
          >
            Tap a day to mark it complete
          </div>
          <button
            onClick={() => setCompleted(new Set())}
            style={{
              background: 'none',
              border: 'none',
              fontFamily: fontMono,
              fontSize: '11px',
              color: colors.muted,
              cursor: 'pointer',
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
            }}
          >
            Reset
          </button>
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(11, 1fr)',
            gap: '6px',
          }}
        >
          {Array.from({ length: 66 }, (_, i) => i + 1).map((day) => {
            const isDone = completed.has(day);
            const isCurrent = day === currentDay && !isDone;
            return (
              <button
                key={day}
                onClick={() => toggleDay(day)}
                style={{
                  aspectRatio: '1',
                  background: isDone ? colors.ink : isCurrent ? colors.bgAlt : 'transparent',
                  border: `1px solid ${isDone ? colors.ink : isCurrent ? colors.accent : colors.line}`,
                  color: isDone ? colors.bg : colors.inkSoft,
                  fontFamily: fontMono,
                  fontSize: '11px',
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: 0,
                }}
                onMouseEnter={(e) =>
                  !isDone && (e.currentTarget.style.borderColor = colors.accent)
                }
                onMouseLeave={(e) =>
                  !isDone &&
                  !isCurrent &&
                  (e.currentTarget.style.borderColor = colors.line)
                }
              >
                {day}
              </button>
            );
          })}
        </div>
      </div>

      <CitationBar text="Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010). How are habits formed: Modelling habit formation in the real world. European Journal of Social Psychology, 40, 998–1009." />
    </div>
  );
}

function StatCell({
  label,
  value,
  unit,
  accent,
}: {
  label: string;
  value: number;
  unit?: string;
  accent?: boolean;
}) {
  return (
    <div style={{ background: colors.bg, padding: '1.5rem' }}>
      <div
        style={{
          fontFamily: fontMono,
          fontSize: '10px',
          letterSpacing: '0.2em',
          color: colors.muted,
          textTransform: 'uppercase',
          marginBottom: '0.75rem',
        }}
      >
        {label}
      </div>
      <div
        style={{
          fontFamily: fontSerif,
          fontSize: '48px',
          fontWeight: 400,
          lineHeight: 1,
          letterSpacing: '-0.02em',
        }}
      >
        {value}
        {unit && (
          <span
            style={{
              fontSize: '22px',
              color: accent ? colors.accent : colors.muted,
              marginLeft: '4px',
            }}
          >
            {unit}
          </span>
        )}
      </div>
    </div>
  );
}

/* ============ DAILY LOG ============ */
interface LogEntry {
  day: number;
  completed: boolean;
  motivation: number;
}

function DailyLog() {
  const [entries, setEntries] = useState<LogEntry[]>([]);
  const [completed, setCompleted] = useState<boolean | null>(null);
  const [motivation, setMotivation] = useState(3);

  const submit = () => {
    if (completed === null) return;
    const newEntry: LogEntry = {
      day: entries.length + 1,
      completed,
      motivation,
    };
    setEntries([...entries, newEntry]);
    setCompleted(null);
    setMotivation(3);
  };

  const chartData = entries.map((e) => ({
    day: `D${e.day}`,
    motivation: e.motivation,
    completion: e.completed ? 5 : 0,
  }));

  const insight = useMemo(() => {
    if (entries.length < 3) {
      return `Log at least 3 days to unlock your pattern. Right now you have ${entries.length}.`;
    }
    const lowMot = entries.filter((e) => e.motivation <= 2);
    const lowMotCompleted = lowMot.filter((e) => e.completed).length;

    if (lowMot.length === 0) {
      const total = entries.length;
      const totalCompleted = entries.filter((e) => e.completed).length;
      return `You have logged ${total} days and completed your system on ${totalCompleted} of them. You haven't had a low motivation day yet. Keep logging. The real test comes when motivation drops.`;
    }

    const pct = Math.round((lowMotCompleted / lowMot.length) * 100);
    if (pct >= 60) {
      return `You completed your system on ${lowMotCompleted} of ${lowMot.length} low motivation days (${pct}%). Motivation is not doing the work. Your system is.`;
    } else if (pct >= 30) {
      return `On low motivation days, you completed your system ${lowMotCompleted} of ${lowMot.length} times (${pct}%). The system holds sometimes. Tighten the trigger and the action and watch this number climb.`;
    } else {
      return `You completed your system on ${lowMotCompleted} of ${lowMot.length} low motivation days (${pct}%). Motivation is still running the show. That is exactly what a weak system looks like. Go back to the Builder and make the trigger and action specific enough to survive a bad day.`;
    }
  }, [entries]);

  return (
    <div>
      <TabHeader
        eyebrow="Tab 04 / Daily Log"
        title="Motivation or system. Check the data."
        subtitle="Your own class survey showed that 100% of students feel more productive when motivated, but 80% still finish tasks when they don't feel like it. This tab is where that contradiction becomes personal."
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
          gap: '2rem',
          marginTop: '3rem',
        }}
      >
        {/* Log form */}
        <div
          style={{
            background: colors.bgAlt,
            padding: '2rem',
          }}
        >
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              letterSpacing: '0.2em',
              color: colors.accent,
              textTransform: 'uppercase',
              marginBottom: '1.5rem',
            }}
          >
            Today&apos;s check in
          </div>

          <div style={{ marginBottom: '2rem' }}>
            <div
              style={{
                fontFamily: fontSerif,
                fontSize: '20px',
                marginBottom: '1rem',
              }}
            >
              Did you complete your system today?
            </div>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <ChoiceButton active={completed === true} onClick={() => setCompleted(true)}>
                Yes
              </ChoiceButton>
              <ChoiceButton active={completed === false} onClick={() => setCompleted(false)}>
                No
              </ChoiceButton>
            </div>
          </div>

          <div style={{ marginBottom: '2rem' }}>
            <div
              style={{
                fontFamily: fontSerif,
                fontSize: '20px',
                marginBottom: '1rem',
              }}
            >
              How motivated did you feel?
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  onClick={() => setMotivation(n)}
                  style={{
                    flex: 1,
                    padding: '0.75rem',
                    background: motivation === n ? colors.ink : 'transparent',
                    color: motivation === n ? colors.bg : colors.inkSoft,
                    border: `1px solid ${motivation === n ? colors.ink : colors.line}`,
                    fontFamily: fontMono,
                    fontSize: '16px',
                    cursor: 'pointer',
                    transition: 'all 0.15s',
                  }}
                >
                  {n}
                </button>
              ))}
            </div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                fontFamily: fontMono,
                fontSize: '10px',
                color: colors.muted,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
              }}
            >
              <span>Dead</span>
              <span>On fire</span>
            </div>
          </div>

          <PrimaryButton onClick={submit} disabled={completed === null}>
            Log day {entries.length + 1} →
          </PrimaryButton>
        </div>

        {/* Insight */}
        <div
          style={{
            background: colors.ink,
            color: colors.bg,
            padding: '2rem',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div
              style={{
                fontFamily: fontMono,
                fontSize: '10px',
                letterSpacing: '0.2em',
                color: colors.accentSoft,
                textTransform: 'uppercase',
                marginBottom: '1.5rem',
              }}
            >
              Your pattern
            </div>
            <p
              style={{
                fontFamily: fontSerif,
                fontSize: '22px',
                lineHeight: 1.4,
                fontWeight: 300,
                margin: 0,
                fontStyle: 'italic',
              }}
            >
              {insight}
            </p>
          </div>
          <div
            style={{
              display: 'flex',
              gap: '2rem',
              marginTop: '2rem',
              paddingTop: '1.5rem',
              borderTop: `1px solid ${colors.inkSoft}`,
            }}
          >
            <div>
              <div
                style={{
                  fontFamily: fontMono,
                  fontSize: '10px',
                  color: colors.muted,
                  letterSpacing: '0.15em',
                  textTransform: 'uppercase',
                }}
              >
                Days logged
              </div>
              <div style={{ fontFamily: fontSerif, fontSize: '32px', marginTop: '4px' }}>
                {entries.length}
              </div>
            </div>
            <div>
              <div
                style={{
                  fontFamily: fontMono,
                  fontSize: '10px',
                  color: colors.muted,
                  letterSpacing: '0.15em',
                  textTransform: 'uppercase',
                }}
              >
                Completion rate
              </div>
              <div style={{ fontFamily: fontSerif, fontSize: '32px', marginTop: '4px' }}>
                {entries.length > 0
                  ? Math.round(
                      (entries.filter((e) => e.completed).length / entries.length) * 100,
                    )
                  : 0}
                <span style={{ fontSize: '18px', color: colors.muted }}>%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Chart */}
      {entries.length > 0 && (
        <div style={{ marginTop: '3rem' }}>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              letterSpacing: '0.2em',
              color: colors.muted,
              textTransform: 'uppercase',
              marginBottom: '0.75rem',
            }}
          >
            Motivation vs. completion over time
          </div>
          <div
            style={{
              width: '100%',
              height: 260,
              background: colors.bgAlt,
              padding: '1.5rem',
              boxSizing: 'border-box',
            }}
          >
            <ResponsiveContainer>
              <LineChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 20 }}>
                <CartesianGrid stroke={colors.line} strokeDasharray="2 4" vertical={false} />
                <XAxis
                  dataKey="day"
                  stroke={colors.muted}
                  tick={{ fontFamily: fontMono, fontSize: 11 }}
                />
                <YAxis
                  stroke={colors.muted}
                  tick={{ fontFamily: fontMono, fontSize: 11 }}
                  domain={[0, 5]}
                />
                <Tooltip
                  contentStyle={{
                    background: colors.ink,
                    border: 'none',
                    color: colors.bg,
                    fontFamily: fontMono,
                    fontSize: 12,
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="motivation"
                  stroke={colors.accent}
                  strokeWidth={2}
                  dot={{ fill: colors.accent, r: 4 }}
                  name="Motivation (1-5)"
                />
                <Line
                  type="stepAfter"
                  dataKey="completion"
                  stroke={colors.ink}
                  strokeWidth={2}
                  dot={{ fill: colors.ink, r: 4 }}
                  name="Completed (0 or 5)"
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div
            style={{
              display: 'flex',
              gap: '2rem',
              marginTop: '1rem',
              fontFamily: fontMono,
              fontSize: '11px',
              color: colors.muted,
              letterSpacing: '0.05em',
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span
                style={{
                  width: 14,
                  height: 2,
                  background: colors.accent,
                  display: 'inline-block',
                }}
              />{' '}
              Motivation
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span
                style={{
                  width: 14,
                  height: 2,
                  background: colors.ink,
                  display: 'inline-block',
                }}
              />{' '}
              System completed
            </span>
          </div>
        </div>
      )}

      <CitationBar text="Martins, N. (2026). Motivation vs. discipline in daily behavior [Class survey]. University of Miami, WRS 106. N = 20." />
    </div>
  );
}

/* ============ RESEARCH ============ */
function Research() {
  const studies = [
    {
      num: '01',
      authors: 'Gollwitzer',
      year: '1999',
      title: 'Implementation intentions: Strong effects of simple plans.',
      journal: 'American Psychologist, 54(7), 493–503.',
      finding:
        'Specific if then plans increase follow through significantly more than vague general goals. When someone replaces "I will study tonight" with "If it is 7pm, I will open my notebook and study for 45 minutes," the chance of actually doing it goes up substantially.',
      why: 'This is the mechanism behind the System Builder. A goal without a trigger is a wish.',
    },
    {
      num: '02',
      authors: 'Lally, van Jaarsveld, Potts, & Wardle',
      year: '2010',
      title: 'How are habits formed: Modelling habit formation in the real world.',
      journal: 'European Journal of Social Psychology, 40, 998–1009.',
      finding:
        'Habits take an average of 66 days to become automatic. Some participants needed several months. Most people quit in week two, not because the habit is not working, but because they expect it to feel easier faster than it actually does.',
      why: 'The 66-Day Tracker turns the early struggle into a visible phase on a curve, not a verdict on whether you have discipline.',
    },
    {
      num: '03',
      authors: 'Duckworth, Peterson, Matthews, & Kelly',
      year: '2007',
      title: 'Grit: Perseverance and passion for long-term goals.',
      journal: 'Journal of Personality and Social Psychology, 92(6), 1087–1101.',
      finding:
        'Perseverance toward long term goals predicts achievement more strongly than talent or intelligence alone. Staying committed through failure and slow progress matters more than how gifted someone is.',
      why: 'This is the frame around everything. You do not need to be the smartest student in the room. You need to be the one who keeps showing up when it stops being fun.',
    },
  ];

  return (
    <div>
      <TabHeader
        eyebrow="Tab 05 / The Research"
        title="Three studies. One argument."
        subtitle="This site is not a self help app. Every feature is grounded in published, peer reviewed research. The research is not the footer. It is the product."
      />

      <div
        style={{
          marginTop: '3rem',
          display: 'flex',
          flexDirection: 'column',
          gap: '1px',
          background: colors.line,
          border: `1px solid ${colors.line}`,
        }}
      >
        {studies.map((s) => (
          <div key={s.num} style={{ background: colors.bg, padding: '2.5rem 2rem' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '80px 1fr',
                gap: '1.5rem',
                alignItems: 'start',
              }}
            >
              <div
                style={{
                  fontFamily: fontSerif,
                  fontSize: '48px',
                  fontWeight: 300,
                  color: colors.accent,
                  lineHeight: 1,
                }}
              >
                {s.num}
              </div>
              <div>
                <div
                  style={{
                    fontFamily: fontMono,
                    fontSize: '11px',
                    color: colors.muted,
                    letterSpacing: '0.1em',
                    textTransform: 'uppercase',
                    marginBottom: '0.5rem',
                  }}
                >
                  {s.authors} &nbsp;&middot;&nbsp; {s.year}
                </div>
                <div
                  style={{
                    fontFamily: fontSerif,
                    fontSize: '26px',
                    fontWeight: 400,
                    lineHeight: 1.3,
                    marginBottom: '0.75rem',
                    fontStyle: 'italic',
                  }}
                >
                  {s.title}
                </div>
                <div
                  style={{
                    fontFamily: fontMono,
                    fontSize: '12px',
                    color: colors.muted,
                    marginBottom: '1.5rem',
                  }}
                >
                  {s.journal}
                </div>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                    gap: '2rem',
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontFamily: fontMono,
                        fontSize: '10px',
                        letterSpacing: '0.2em',
                        color: colors.accent,
                        textTransform: 'uppercase',
                        marginBottom: '0.5rem',
                      }}
                    >
                      What they found
                    </div>
                    <p
                      style={{
                        fontSize: '15px',
                        lineHeight: 1.6,
                        color: colors.inkSoft,
                        margin: 0,
                      }}
                    >
                      {s.finding}
                    </p>
                  </div>
                  <div>
                    <div
                      style={{
                        fontFamily: fontMono,
                        fontSize: '10px',
                        letterSpacing: '0.2em',
                        color: colors.accent,
                        textTransform: 'uppercase',
                        marginBottom: '0.5rem',
                      }}
                    >
                      Why it is here
                    </div>
                    <p
                      style={{
                        fontSize: '15px',
                        lineHeight: 1.6,
                        color: colors.inkSoft,
                        margin: 0,
                      }}
                    >
                      {s.why}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div
        style={{
          marginTop: '3rem',
          padding: '2rem',
          background: colors.bgAlt,
        }}
      >
        <div
          style={{
            fontFamily: fontMono,
            fontSize: '10px',
            letterSpacing: '0.2em',
            color: colors.accent,
            textTransform: 'uppercase',
            marginBottom: '1rem',
          }}
        >
          Primary source data
        </div>
        <p
          style={{
            fontFamily: fontSerif,
            fontSize: '20px',
            lineHeight: 1.5,
            fontWeight: 300,
            margin: 0,
          }}
        >
          Martins, N. (2026).{' '}
          <span style={{ fontStyle: 'italic' }}>
            Motivation vs. discipline in daily behavior
          </span>{' '}
          [Class survey]. University of Miami, WRS 106. N = 20.
        </p>
      </div>
    </div>
  );
}

/* ============ SHARED COMPONENTS ============ */
function TabHeader({
  eyebrow,
  title,
  subtitle,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div style={{ padding: '3rem 0 2rem', borderBottom: `1px solid ${colors.line}` }}>
      <div
        style={{
          fontFamily: fontMono,
          fontSize: '11px',
          letterSpacing: '0.2em',
          color: colors.accent,
          textTransform: 'uppercase',
          marginBottom: '1.5rem',
        }}
      >
        {eyebrow}
      </div>
      <h1
        style={{
          fontFamily: fontSerif,
          fontSize: 'clamp(36px, 5vw, 64px)',
          fontWeight: 400,
          lineHeight: 1,
          letterSpacing: '-0.02em',
          margin: '0 0 1.5rem',
        }}
      >
        {title}
      </h1>
      <p
        style={{
          fontSize: '18px',
          lineHeight: 1.5,
          color: colors.inkSoft,
          maxWidth: '720px',
          margin: 0,
          fontWeight: 300,
        }}
      >
        {subtitle}
      </p>
    </div>
  );
}

function SectionHeader({
  eyebrow,
  title,
  subtitle,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div>
      <div
        style={{
          fontFamily: fontMono,
          fontSize: '11px',
          letterSpacing: '0.2em',
          color: colors.accent,
          textTransform: 'uppercase',
          marginBottom: '1.25rem',
        }}
      >
        {eyebrow}
      </div>
      <h2
        style={{
          fontFamily: fontSerif,
          fontSize: 'clamp(32px, 4vw, 52px)',
          fontWeight: 400,
          lineHeight: 1.05,
          letterSpacing: '-0.02em',
          margin: '0 0 1rem',
        }}
      >
        {title}
      </h2>
      <p
        style={{
          fontSize: '17px',
          lineHeight: 1.5,
          color: colors.inkSoft,
          maxWidth: '640px',
          margin: 0,
          fontWeight: 300,
        }}
      >
        {subtitle}
      </p>
    </div>
  );
}

function ToolCard({
  num,
  title,
  source,
  body,
  onClick,
}: {
  num: string;
  title: string;
  source: string;
  body: string;
  onClick: () => void;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        padding: '2rem',
        background: colors.bgAlt,
        cursor: 'pointer',
        transition: 'all 0.2s',
        border: '1px solid transparent',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = colors.ink;
        e.currentTarget.style.color = colors.bg;
        const children = e.currentTarget.querySelectorAll('[data-swap]') as NodeListOf<HTMLElement>;
        children.forEach((c) => (c.style.color = colors.bg));
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = colors.bgAlt;
        e.currentTarget.style.color = colors.ink;
        const children = e.currentTarget.querySelectorAll('[data-swap]') as NodeListOf<HTMLElement>;
        children.forEach((c) => (c.style.color = c.dataset.swap || ''));
      }}
    >
      <div
        data-swap={colors.accent}
        style={{
          fontFamily: fontMono,
          fontSize: '11px',
          letterSpacing: '0.2em',
          color: colors.accent,
          textTransform: 'uppercase',
          marginBottom: '1rem',
          transition: 'color 0.2s',
        }}
      >
        {num} &nbsp;/&nbsp; {source}
      </div>
      <div
        style={{
          fontFamily: fontSerif,
          fontSize: '28px',
          fontWeight: 400,
          marginBottom: '1rem',
          lineHeight: 1.1,
        }}
      >
        {title}
      </div>
      <p
        data-swap={colors.inkSoft}
        style={{
          fontSize: '15px',
          lineHeight: 1.5,
          color: colors.inkSoft,
          margin: '0 0 1.5rem',
          transition: 'color 0.2s',
        }}
      >
        {body}
      </p>
      <div
        data-swap={colors.accent}
        style={{
          fontFamily: fontMono,
          fontSize: '12px',
          color: colors.accent,
          letterSpacing: '0.1em',
          textTransform: 'uppercase',
          transition: 'color 0.2s',
        }}
      >
        Open tool →
      </div>
    </div>
  );
}

function PrimaryButton({
  children,
  onClick,
  disabled,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: '0.9rem 1.5rem',
        background: disabled ? colors.muted : colors.ink,
        color: colors.bg,
        border: 'none',
        fontFamily: fontMono,
        fontSize: '13px',
        letterSpacing: '0.1em',
        textTransform: 'uppercase',
        cursor: disabled ? 'not-allowed' : 'pointer',
        transition: 'all 0.2s',
        opacity: disabled ? 0.5 : 1,
      }}
      onMouseEnter={(e) => !disabled && (e.currentTarget.style.background = colors.accent)}
      onMouseLeave={(e) => !disabled && (e.currentTarget.style.background = colors.ink)}
    >
      {children}
    </button>
  );
}

function SecondaryButton({
  children,
  onClick,
}: {
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '0.9rem 1.5rem',
        background: 'transparent',
        color: colors.ink,
        border: `1px solid ${colors.ink}`,
        fontFamily: fontMono,
        fontSize: '13px',
        letterSpacing: '0.1em',
        textTransform: 'uppercase',
        cursor: 'pointer',
        transition: 'all 0.2s',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = colors.ink;
        e.currentTarget.style.color = colors.bg;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = 'transparent';
        e.currentTarget.style.color = colors.ink;
      }}
    >
      {children}
    </button>
  );
}

function ChoiceButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        flex: 1,
        padding: '1rem',
        background: active ? colors.ink : 'transparent',
        color: active ? colors.bg : colors.ink,
        border: `1px solid ${active ? colors.ink : colors.line}`,
        fontFamily: fontMono,
        fontSize: '13px',
        letterSpacing: '0.1em',
        textTransform: 'uppercase',
        cursor: 'pointer',
        transition: 'all 0.15s',
      }}
    >
      {children}
    </button>
  );
}

function CitationBar({ text }: { text: string }) {
  return (
    <div
      style={{
        marginTop: '3rem',
        paddingTop: '1.5rem',
        borderTop: `1px solid ${colors.line}`,
        fontFamily: fontMono,
        fontSize: '11px',
        color: colors.muted,
        lineHeight: 1.6,
      }}
    >
      {text}
    </div>
  );
}

function Footer() {
  return (
    <footer
      style={{
        borderTop: `1px solid ${colors.line}`,
        padding: '2.5rem 2rem',
        marginTop: '2rem',
      }}
    >
      <div
        style={{
          maxWidth: '1100px',
          margin: '0 auto',
          display: 'flex',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1rem',
          alignItems: 'baseline',
        }}
      >
        <div>
          <div
            style={{
              fontFamily: fontSerif,
              fontSize: '18px',
              marginBottom: '4px',
            }}
          >
            SISTEMA
          </div>
          <div
            style={{
              fontFamily: fontMono,
              fontSize: '10px',
              color: colors.muted,
              letterSpacing: '0.15em',
              textTransform: 'uppercase',
            }}
          >
            Nicolas Martins &nbsp;&middot;&nbsp; WRS 106 &nbsp;&middot;&nbsp; Spring 2026
          </div>
        </div>
        <div
          style={{
            fontFamily: fontMono,
            fontSize: '11px',
            color: colors.muted,
            letterSpacing: '0.1em',
            maxWidth: '420px',
            textAlign: 'right',
            lineHeight: 1.6,
          }}
        >
          Built as Assignment 4: Synthesis Remix. University of Miami. Prof. Samantha Phillips.
        </div>
      </div>
    </footer>
  );
}
