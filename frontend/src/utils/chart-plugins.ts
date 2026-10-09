import type { Chart, Plugin } from 'chart.js';

export interface ValueMarker {
  value: number;
  label: string;
  color?: string;
}

// 轻量垂直标记线插件（替代 chartjs-plugin-annotation 依赖）：
// 通过 options.plugins.valueMarker.markers 传入标记点
export const valueMarkerPlugin: Plugin = {
  id: 'valueMarker',

  afterDatasetsDraw(chart: Chart) {
    const options = chart.options.plugins as
      | { valueMarker?: { markers?: ValueMarker[] } }
      | undefined;
    const markers = options?.valueMarker?.markers;
    if (!markers || markers.length === 0) return;

    const { ctx, chartArea } = chart;
    const xScale = chart.scales.x;
    if (!xScale) return;

    ctx.save();
    ctx.font = '600 10px sans-serif';
    ctx.textBaseline = 'bottom';

    for (const marker of markers) {
      const x = xScale.getPixelForValue(marker.value);
      if (!Number.isFinite(x) || x < chartArea.left || x > chartArea.right) continue;

      const color = marker.color ?? '#F59E0B';
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.setLineDash([5, 3]);
      ctx.beginPath();
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      ctx.setLineDash([]);

      const text = marker.label;
      const width = ctx.measureText(text).width + 8;
      const textX = Math.min(Math.max(x - width / 2, chartArea.left), chartArea.right - width);
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.roundRect(textX, chartArea.top - 2, width, 14, 3);
      ctx.fill();
      ctx.fillStyle = '#FFFFFF';
      ctx.fillText(text, textX + 4, chartArea.top + 11);
    }

    ctx.restore();
  },
};
