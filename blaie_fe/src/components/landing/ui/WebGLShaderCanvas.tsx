"use client";

import { useEffect, useRef } from "react";

const VERTEX_SHADER = `
attribute vec2 a_position;
varying vec2 v_texCoord;
void main() {
  v_texCoord = a_position * 0.5 + 0.5;
  gl_Position = vec4(a_position, 0.0, 1.0);
}
`;

const FRAGMENT_SHADER = `
precision highp float;
varying vec2 v_texCoord;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec3 u_core_color;
uniform vec3 u_stream_color;

float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = noise(i);
    float b = noise(i + vec2(1.0, 0.0));
    float c = noise(i + vec2(0.0, 1.0));
    float d = noise(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * smoothNoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = v_texCoord;
    uv.x *= u_resolution.x / u_resolution.y;

    vec2 center = vec2(0.5 * u_resolution.x / u_resolution.y, 0.5);
    float dist = length(uv - center);

    float angle = atan(uv.y - center.y, uv.x - center.x);
    float swirl = angle + u_time * 0.5 + 5.0 / (dist + 0.1);
    vec2 rotatedUV = vec2(cos(swirl), sin(swirl)) * dist;

    float n = fbm(rotatedUV * 3.0 - u_time * 0.2);

    float core = 0.02 / (dist + 0.01);
    float stream = exp(-15.0 * abs(uv.y - 0.5)) * smoothstep(0.0, 0.8, uv.x) * n;

    vec3 color = u_core_color * core;
    color += u_stream_color * stream * 0.5;
    color *= (1.0 - dist * 1.5);

    gl_FragColor = vec4(color, color.r * 0.8);
}
`;

function createShader(gl: WebGLRenderingContext, type: number, src: string): WebGLShader | null {
  const shader = gl.createShader(type);
  if (!shader) return null;
  gl.shaderSource(shader, src);
  gl.compileShader(shader);
  return shader;
}

function cssColorToRgb(value: string, fallback: [number, number, number]) {
  const color = value.trim();
  if (color.startsWith("#")) {
    const hex = color.slice(1);
    const normalized =
      hex.length === 3
        ? hex
            .split("")
            .map((part) => part + part)
            .join("")
        : hex;
    const parsed = Number.parseInt(normalized, 16);
    if (!Number.isNaN(parsed)) {
      return [
        ((parsed >> 16) & 255) / 255,
        ((parsed >> 8) & 255) / 255,
        (parsed & 255) / 255,
      ] as const;
    }
  }

  const rgbMatch = color.match(/rgba?\(([^)]+)\)/);
  if (rgbMatch) {
    const [red, green, blue] = rgbMatch[1]
      .split(/[,\s/]+/)
      .map((part) => Number.parseFloat(part))
      .filter((part) => !Number.isNaN(part));
    if (red !== undefined && green !== undefined && blue !== undefined) {
      return [red / 255, green / 255, blue / 255] as const;
    }
  }

  return fallback;
}

function themeColor(name: string, fallback: [number, number, number]) {
  if (typeof window === "undefined") {
    return fallback;
  }
  return cssColorToRgb(
    getComputedStyle(document.documentElement).getPropertyValue(name),
    fallback,
  );
}

export function WebGLShaderCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const syncSize = () => {
      const w = canvas.clientWidth || 1280;
      const h = canvas.clientHeight || 720;
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
      }
    };

    const ro = typeof ResizeObserver !== "undefined"
      ? new ResizeObserver(syncSize)
      : null;
    ro?.observe(canvas);
    syncSize();

    const gl = canvas.getContext("webgl") || canvas.getContext("experimental-webgl") as WebGLRenderingContext | null;
    if (!gl) return;

    const prog = gl.createProgram();
    if (!prog) return;

    const vs = createShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
    const fs = createShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) return;

    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.linkProgram(prog);
    gl.useProgram(prog);

    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(
      gl.ARRAY_BUFFER,
      new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]),
      gl.STATIC_DRAW,
    );

    const pos = gl.getAttribLocation(prog, "a_position");
    gl.enableVertexAttribArray(pos);
    gl.vertexAttribPointer(pos, 2, gl.FLOAT, false, 0, 0);

    const uTime = gl.getUniformLocation(prog, "u_time");
    const uRes = gl.getUniformLocation(prog, "u_resolution");
    const uMouse = gl.getUniformLocation(prog, "u_mouse");
    const uCoreColor = gl.getUniformLocation(prog, "u_core_color");
    const uStreamColor = gl.getUniformLocation(prog, "u_stream_color");

    const mouse = { x: canvas.width / 2, y: canvas.height / 2 };
    const onMouseMove = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width && rect.height) {
        mouse.x = ((e.clientX - rect.left) / rect.width) * canvas.width;
        mouse.y = (1 - (e.clientY - rect.top) / rect.height) * canvas.height;
      }
    };
    window.addEventListener("mousemove", onMouseMove);

    let rafId: number;
    const render = (t: number) => {
      if (!ro) syncSize();
      gl.viewport(0, 0, canvas.width, canvas.height);
      if (uTime) gl.uniform1f(uTime, t * 0.001);
      if (uRes) gl.uniform2f(uRes, canvas.width, canvas.height);
      if (uMouse) gl.uniform2f(uMouse, mouse.x, mouse.y);
      if (uCoreColor) {
        gl.uniform3fv(
          uCoreColor,
          themeColor("--brand-accent", [0.86, 0.85, 0.99]),
        );
      }
      if (uStreamColor) {
        gl.uniform3fv(
          uStreamColor,
          themeColor("--ring", [0.72, 0.68, 1]),
        );
      }
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      rafId = requestAnimationFrame(render);
    };
    rafId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(rafId);
      window.removeEventListener("mousemove", onMouseMove);
      ro?.disconnect();
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      style={{ display: "block", width: "100%", height: "100%" }}
    />
  );
}
