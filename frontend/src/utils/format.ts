export interface ValueWithUnit {
  value: string;
  unit: string;
}

export interface CapacityScale {
  divisor: number;
  unit: string;
}

const BYTE_UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB'] as const;
const GIB_UNITS = ['GiB', 'TiB', 'PiB', 'EiB'] as const;

// Why: capping at three significant digits keeps every card the same width
function digitsFor(scaled: number): number {
  return scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2;
}

function pickUnitIndex(amount: number, unitCount: number): number {
  let index = 0;
  while (amount / 1024 ** index >= 1024 && index < unitCount - 1) {
    index += 1;
  }
  return index;
}

function formatScaled(amount: number, units: readonly string[]): ValueWithUnit {
  let index = pickUnitIndex(amount, units.length);
  let scaled = amount / 1024 ** index;
  // Why: rounding at the boundary would print "1024 GiB"; promote to the next unit
  if (Number(scaled.toFixed(digitsFor(scaled))) >= 1024 && index < units.length - 1) {
    index += 1;
    scaled = amount / 1024 ** index;
  }
  return { value: scaled.toFixed(digitsFor(scaled)), unit: units[index] };
}

// Why: the API reports raw bytes, so the unit scales with the value instead of
// being pinned to GiB (0.27 GiB reads better as 276 MiB, 231240 GiB as 226 TiB)
export function formatBytes(bytes: number | null | undefined): ValueWithUnit {
  const amount = Number(bytes);
  if (!Number.isFinite(amount) || amount <= 0) return { value: '0', unit: 'B' };
  return formatScaled(amount, BYTE_UNITS);
}

// Why: the 12-month chart API still aggregates in GiB; scale with the same ladder
export function capacityScale(peakGib: number | null | undefined): CapacityScale {
  const peak = Number(peakGib);
  const index = Number.isFinite(peak) && peak > 0 ? pickUnitIndex(peak, GIB_UNITS.length) : 0;
  return { divisor: 1024 ** index, unit: GIB_UNITS[index] };
}

// Why: value and unit are returned separately so the unit can render smaller
export function formatDuration(totalSecs: number | null | undefined): ValueWithUnit {
  const secs = Number(totalSecs);
  if (!Number.isFinite(secs) || secs <= 0) return { value: '0', unit: '分钟' };
  if (secs < 3600) return { value: String(Math.round(secs / 60)), unit: '分钟' };
  if (secs < 86400) return { value: (secs / 3600).toFixed(1), unit: '小时' };
  return { value: (secs / 86400).toFixed(1), unit: '天' };
}
