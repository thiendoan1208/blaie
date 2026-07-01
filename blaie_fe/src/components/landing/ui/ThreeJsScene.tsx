"use client";

import { useEffect, useRef } from "react";
import * as THREE from "three";

interface Fragment {
  root: THREE.Group;
  rawMesh: THREE.Mesh;
  structured: THREE.Group;
  speed: number;
  rotSpeed: {
    x: number;
    y: number;
    z: number;
  };
  phase: "raw" | "morph" | "structured";
  morph: number;
  structuredKind: string;
}

interface Pulse {
  mesh: THREE.Mesh;
  life: number;
}

const STRUCTURED_KINDS = ["document", "calendar", "chart", "network", "alert", "timeline"];
const NO_EMISSIVE = 0;

function cssVariableColor(name: string, fallback: number) {
  if (typeof window === "undefined") {
    return fallback;
  }

  const color = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  if (!color.startsWith("#")) {
    return fallback;
  }

  const hex = color.slice(1);
  const normalized =
    hex.length === 3
      ? hex
          .split("")
          .map((part) => part + part)
          .join("")
      : hex;
  const parsed = Number.parseInt(normalized, 16);
  return Number.isNaN(parsed) ? fallback : parsed;
}

function sceneColors() {
  return {
    foreground: cssVariableColor("--foreground", 1315859),
    surface: cssVariableColor("--card", 16777215),
    subtle: cssVariableColor("--muted", 15789870),
    accent: cssVariableColor("--brand-accent", 14473469),
    muted: cssVariableColor("--muted-foreground", 7565932),
    border: cssVariableColor("--border", 14605521),
  };
}

export function ThreeJsScene() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let width = container.clientWidth || window.innerWidth;
    let height = container.clientHeight || window.innerHeight;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000);
    camera.position.z = 5;

    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    const initialColors = sceneColors();
    const ambientLight = new THREE.AmbientLight(initialColors.border);
    scene.add(ambientLight);
    const pointLight = new THREE.PointLight(initialColors.surface, 1.2, 100);
    pointLight.position.set(0, 0, 5);
    scene.add(pointLight);

    const sceneRig = new THREE.Group();
    scene.add(sceneRig);

    function updateViewport() {
      if (!container) return;
      width = container.clientWidth || window.innerWidth;
      height = container.clientHeight || window.innerHeight;
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height);
    }

    const rawGeometries = [
      new THREE.IcosahedronGeometry(0.2, 0),
      new THREE.BoxGeometry(0.2, 0.2, 0.2),
      new THREE.TorusGeometry(0.15, 0.05, 8, 16),
      new THREE.OctahedronGeometry(0.18, 0),
      new THREE.TetrahedronGeometry(0.2, 0)
    ];

    const fragments: Fragment[] = [];
    const pulses: Pulse[] = [];

    function makeMaterial(color: number, opacity: number, wireframe: boolean) {
      return new THREE.MeshPhongMaterial({
        color,
        transparent: true,
        opacity,
        wireframe: !!wireframe
      });
    }

    function makeStructuredMaterial(color: number, opacity: number, emissive: number, emissiveIntensity: number) {
      return new THREE.MeshStandardMaterial({
        color,
        roughness: 0.55,
        metalness: 0.08,
        transparent: true,
        opacity,
        emissive,
        emissiveIntensity
      });
    }

    function addBox(
      group: THREE.Group,
      w: number,
      h: number,
      d: number,
      color: number,
      x = 0,
      y = 0,
      z = 0,
      rotX = 0,
      rotY = 0,
      rotZ = 0,
      opacity = 1,
      emissive = NO_EMISSIVE,
      emissiveIntensity = 0
    ) {
      const mesh = new THREE.Mesh(
        new THREE.BoxGeometry(w, h, d),
        makeStructuredMaterial(color, opacity, emissive, emissiveIntensity)
      );
      mesh.position.set(x, y, z);
      mesh.rotation.set(rotX, rotY, rotZ);
      group.add(mesh);
      return mesh;
    }

    function addCylinder(
      group: THREE.Group,
      rTop: number,
      rBottom: number,
      h: number,
      radialSegments = 6,
      color: number,
      x = 0,
      y = 0,
      z = 0,
      rotX = 0,
      rotY = 0,
      rotZ = 0,
      opacity = 1,
      emissive = NO_EMISSIVE,
      emissiveIntensity = 0
    ) {
      const mesh = new THREE.Mesh(
        new THREE.CylinderGeometry(rTop, rBottom, h, radialSegments),
        makeStructuredMaterial(color, opacity, emissive, emissiveIntensity)
      );
      mesh.position.set(x, y, z);
      mesh.rotation.set(rotX, rotY, rotZ);
      group.add(mesh);
      return mesh;
    }

    function addSphere(
      group: THREE.Group,
      radius: number,
      widthSegments = 16,
      heightSegments = 16,
      color: number,
      x = 0,
      y = 0,
      z = 0,
      opacity = 1,
      emissive = NO_EMISSIVE,
      emissiveIntensity = 0
    ) {
      const mesh = new THREE.Mesh(
        new THREE.SphereGeometry(radius, widthSegments, heightSegments),
        makeStructuredMaterial(color, opacity, emissive, emissiveIntensity)
      );
      mesh.position.set(x, y, z);
      group.add(mesh);
      return mesh;
    }

    function makeStructuredArtifact(kind: string) {
      const group = new THREE.Group();
      const colors = sceneColors();
      const accent = colors.foreground;
      const cyan = colors.accent;
      const pale = colors.border;
      const gold = colors.subtle;
      const red = colors.muted;
      const surface = colors.surface;

      if (kind === "document") {
        addBox(group, 1.0, 1.28, 0.12, surface, 0, 0, 0, 0, 0, 0, 0.96, NO_EMISSIVE, 0);
        addBox(group, 1.0, 0.14, 0.08, accent, 0, 0.48, 0.03, 0, 0, 0, 0.95, accent, 0.08);
        addBox(group, 0.78, 0.06, 0.05, cyan, -0.02, 0.18, 0.07, 0, 0, 0, 0.85, cyan, 0.02);
        addBox(group, 0.62, 0.05, 0.05, pale, -0.08, 0.02, 0.07, 0, 0, 0, 0.7, pale, 0.01);
        addBox(group, 0.74, 0.05, 0.05, pale, -0.05, -0.16, 0.07, 0, 0, 0, 0.65, pale, 0.01);
        addBox(group, 0.5, 0.05, 0.05, pale, -0.12, -0.32, 0.07, 0, 0, 0, 0.6, pale, 0.01);
        addSphere(group, 0.06, 12, 12, cyan, 0.3, 0.18, 0.08, 0.95, cyan, 0.2);
        addBox(group, 0.2, 0.2, 0.05, gold, 0.31, -0.2, 0.08, 0, 0, Math.PI / 4, 0.8, gold, 0.08);
        group.scale.setScalar(0.125);
      } else if (kind === "calendar") {
        addBox(group, 1.0, 1.0, 0.14, surface, 0, 0, 0, 0, 0, 0, 0.96, NO_EMISSIVE, 0);
        addBox(group, 1.0, 0.18, 0.09, accent, 0, 0.42, 0.05, 0, 0, 0, 0.96, accent, 0.1);
        addCylinder(group, 0.05, 0.05, 0.18, 8, cyan, -0.28, 0.52, 0.09, Math.PI / 2, 0, 0, 0.95, cyan, 0.12);
        addCylinder(group, 0.05, 0.05, 0.18, 8, cyan, 0.28, 0.52, 0.09, Math.PI / 2, 0, 0, 0.95, cyan, 0.12);
        addBox(group, 0.62, 0.62, 0.06, gold, 0, -0.05, 0.09, 0, 0, 0, 0.9, NO_EMISSIVE, 0);
        for (let row = 0; row < 3; row++) {
          for (let col = 0; col < 3; col++) {
            addBox(
              group,
              0.12,
              0.12,
              0.03,
              col === 1 && row === 1 ? gold : pale,
              -0.18 + col * 0.18,
              0.11 - row * 0.18,
              0.12,
              0,
              0,
              0,
              col === 1 && row === 1 ? 0.95 : 0.72,
              col === 1 && row === 1 ? gold : pale,
              0.04
            );
          }
        }
        group.scale.setScalar(0.13);
      } else if (kind === "chart") {
        addBox(group, 1.08, 0.9, 0.12, surface, 0, 0, 0, 0, 0, 0, 0.96, NO_EMISSIVE, 0);
        addBox(group, 0.88, 0.04, 0.04, pale, 0, -0.33, 0.09, 0, 0, 0, 0.45, pale, 0.01);
        addBox(group, 0.04, 0.62, 0.04, pale, -0.34, 0, 0.09, 0, 0, 0, 0.45, pale, 0.01);
        addBox(group, 0.12, 0.25, 0.08, cyan, -0.2, -0.08, 0.1, 0, 0, 0, 0.95, cyan, 0.1);
        addBox(group, 0.12, 0.42, 0.08, accent, -0.02, -0.02, 0.1, 0, 0, 0, 0.95, accent, 0.08);
        addBox(group, 0.12, 0.3, 0.08, gold, 0.16, -0.06, 0.1, 0, 0, 0, 0.95, gold, 0.06);
        addBox(group, 0.12, 0.52, 0.08, red, 0.34, 0.06, 0.1, 0, 0, 0, 0.92, red, 0.05);
        addCylinder(group, 0.02, 0.02, 0.52, 6, cyan, -0.2, -0.08, 0.11, 0, 0, -0.12, 0.9, cyan, 0.08);
        addCylinder(group, 0.02, 0.02, 0.45, 6, accent, 0.0, -0.0, 0.11, 0, 0, 0.15, 0.9, accent, 0.08);
        addCylinder(group, 0.02, 0.02, 0.38, 6, gold, 0.18, -0.03, 0.11, 0, 0, -0.08, 0.9, gold, 0.08);
        addCylinder(group, 0.02, 0.02, 0.34, 6, red, 0.3, 0.01, 0.11, 0, 0, 0.2, 0.9, red, 0.08);
        group.scale.setScalar(0.13);
      } else if (kind === "network") {
        addSphere(group, 0.18, 18, 18, accent, 0, 0, 0, 1, accent, 0.12);
        addSphere(group, 0.1, 16, 16, cyan, -0.42, 0.2, 0.02, 0.96, cyan, 0.14);
        addSphere(group, 0.1, 16, 16, pale, 0.4, 0.22, -0.02, 0.92, pale, 0.12);
        addSphere(group, 0.08, 16, 16, gold, -0.2, -0.34, 0.03, 0.92, gold, 0.12);
        addSphere(group, 0.08, 16, 16, red, 0.34, -0.28, -0.02, 0.92, red, 0.1);
        addCylinder(group, 0.018, 0.018, 0.54, 6, pale, -0.21, 0.1, 0.01, 0.35, 0.82, 0, 0.7, pale, 0.06);
        addCylinder(group, 0.018, 0.018, 0.58, 6, pale, 0.2, 0.1, 0.01, -0.2, -0.78, 0, 0.7, pale, 0.06);
        addCylinder(group, 0.018, 0.018, 0.52, 6, pale, -0.1, -0.17, 0.01, -0.7, 0.42, 0.1, 0.7, pale, 0.06);
        addCylinder(group, 0.018, 0.018, 0.5, 6, pale, 0.15, -0.15, 0.01, 0.55, -0.3, 0.15, 0.7, pale, 0.06);
        group.scale.setScalar(0.13);
      } else if (kind === "alert") {
        addCylinder(group, 0.16, 0.42, 0.78, 4, red, 0, 0, 0, Math.PI / 2, 0, 0, 0.96, red, 0.12);
        addBox(group, 0.16, 0.5, 0.08, surface, 0, 0.02, 0.1, 0, 0, 0.06, 0.95, NO_EMISSIVE, 0);
        addSphere(group, 0.07, 14, 14, red, 0, -0.14, 0.12, 0.98, red, 0.14);
        addBox(group, 0.08, 0.34, 0.06, gold, 0, 0.12, 0.12, 0, 0, 0, 0.95, gold, 0.1);
        addBox(group, 0.24, 0.04, 0.04, cyan, 0, -0.02, 0.16, 0, 0, 0, 0.9, cyan, 0.08);
        group.scale.setScalar(0.14);
      } else {
        // timeline
        addBox(group, 1.06, 0.14, 0.09, accent, 0, 0.25, 0.03, 0, 0, 0, 0.8, accent, 0.06);
        addBox(group, 1.06, 0.14, 0.09, surface, 0, 0, 0.03, 0, 0, 0, 0.94, NO_EMISSIVE, 0);
        addBox(group, 1.06, 0.14, 0.09, surface, 0, -0.25, 0.03, 0, 0, 0, 0.94, NO_EMISSIVE, 0);
        addSphere(group, 0.08, 12, 12, cyan, -0.42, 0.25, 0.08, 0.95, cyan, 0.1);
        addSphere(group, 0.08, 12, 12, gold, -0.12, 0.0, 0.08, 0.95, gold, 0.08);
        addSphere(group, 0.08, 12, 12, pale, 0.18, -0.25, 0.08, 0.95, pale, 0.08);
        addSphere(group, 0.08, 12, 12, red, 0.46, 0.18, 0.08, 0.95, red, 0.08);
        addCylinder(group, 0.018, 0.018, 0.28, 6, pale, -0.27, 0.14, 0.08, 0, 0, 0.35, 0.85, pale, 0.04);
        addCylinder(group, 0.018, 0.018, 0.26, 6, pale, 0.03, -0.12, 0.08, 0, 0, -0.28, 0.85, pale, 0.04);
        addCylinder(group, 0.018, 0.018, 0.24, 6, pale, 0.32, -0.03, 0.08, 0, 0, 0.2, 0.85, pale, 0.04);
        group.scale.setScalar(0.13);
      }

      group.visible = false;
      return group;
    }

    function disposeGroup(group: THREE.Group) {
      group.traverse((node) => {
        if (node instanceof THREE.Mesh) {
          if (node.geometry) {
            node.geometry.dispose();
          }
          if (node.material) {
            if (Array.isArray(node.material)) {
              node.material.forEach((m) => m.dispose());
            } else {
              node.material.dispose();
            }
          }
        }
      });
    }

    function rebuildStructuredArtifact(fragment: Fragment, kind: string) {
      if (fragment.structured) {
        fragment.root.remove(fragment.structured);
        disposeGroup(fragment.structured);
      }

      const structured = makeStructuredArtifact(kind);
      fragment.structured = structured;
      fragment.structuredKind = kind;
      fragment.root.add(structured);
      return structured;
    }

    function spawnPulse() {
      const pulseMesh = new THREE.Mesh(
        new THREE.SphereGeometry(0.12, 16, 16),
        new THREE.MeshBasicMaterial({
          color: sceneColors().accent,
          transparent: true,
          opacity: 0.85
        })
      );
      sceneRig.add(pulseMesh);
      pulses.push({ mesh: pulseMesh, life: 0 });
    }

    function createFragment(): Fragment {
      const geom = rawGeometries[Math.floor(Math.random() * rawGeometries.length)];
      const mat = makeMaterial(sceneColors().muted, 0.4, true);
      const rawMesh = new THREE.Mesh(geom, mat);

      const root = new THREE.Group();
      const structuredKind = STRUCTURED_KINDS[Math.floor(Math.random() * STRUCTURED_KINDS.length)];
      const structured = makeStructuredArtifact(structuredKind);

      root.add(rawMesh);
      root.add(structured);

      root.position.set(
        -8 - Math.random() * 8,
        (Math.random() - 0.5) * 5,
        (Math.random() - 0.5) * 3
      );
      root.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, Math.random() * Math.PI);

      const speed = 0.004 + Math.random() * 0.01;
      const rotSpeed = {
        x: (Math.random() - 0.5) * 0.018,
        y: (Math.random() - 0.5) * 0.018,
        z: (Math.random() - 0.5) * 0.012
      };

      const fragment: Fragment = {
        root,
        rawMesh,
        structured,
        speed,
        rotSpeed,
        phase: "raw",
        morph: 0,
        structuredKind
      };

      sceneRig.add(root);
      return fragment;
    }

    function resetFragment(fragment: Fragment) {
      fragment.root.position.set(
        -8 - Math.random() * 8,
        (Math.random() - 0.5) * 5,
        (Math.random() - 0.5) * 3
      );
      fragment.root.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, Math.random() * Math.PI);
      fragment.rawMesh.geometry = rawGeometries[Math.floor(Math.random() * rawGeometries.length)];
      fragment.speed = 0.004 + Math.random() * 0.01;
      fragment.rotSpeed = {
        x: (Math.random() - 0.5) * 0.018,
        y: (Math.random() - 0.5) * 0.018,
        z: (Math.random() - 0.5) * 0.012
      };
      fragment.phase = "raw";
      fragment.morph = 0;
      fragment.rawMesh.visible = true;
      fragment.rawMesh.scale.set(1, 1, 1);
      
      const mat = fragment.rawMesh.material;
      if (mat instanceof THREE.Material) {
        mat.opacity = 0.5;
      }
      
      const newKind = STRUCTURED_KINDS[Math.floor(Math.random() * STRUCTURED_KINDS.length)];
      rebuildStructuredArtifact(fragment, newKind);
    }

    for (let i = 0; i < 12; i++) {
      fragments.push(createFragment());
    }

    function syncThemeMaterials() {
      const colors = sceneColors();
      ambientLight.color.setHex(colors.border);
      pointLight.color.setHex(colors.surface);
      fragments.forEach((fragment) => {
        if (fragment.rawMesh.material instanceof THREE.MeshPhongMaterial) {
          fragment.rawMesh.material.color.setHex(colors.muted);
        }
        const structured = rebuildStructuredArtifact(fragment, fragment.structuredKind);
        if (fragment.phase !== "raw") {
          structured.visible = true;
          structured.scale.setScalar(
            fragment.phase === "morph" ? 0.12 + 0.88 * fragment.morph : 1,
          );
          structured.traverse((node) => {
            if (node instanceof THREE.Mesh && node.material instanceof THREE.Material) {
              node.material.opacity =
                fragment.phase === "morph" ? 0.95 * fragment.morph : 0.95;
            }
          });
        }
      });
      pulses.forEach((pulse) => {
        if (pulse.mesh.material instanceof THREE.MeshBasicMaterial) {
          pulse.mesh.material.color.setHex(colors.accent);
        }
      });
    }

    function updatePulses() {
      for (let i = pulses.length - 1; i >= 0; i--) {
        const pulse = pulses[i];
        pulse.life += 0.035;
        pulse.mesh.scale.setScalar(0.5 + pulse.life * 1.8);
        const mat = pulse.mesh.material;
        if (mat instanceof THREE.Material) {
          mat.opacity = Math.max(0, 0.85 * (1 - pulse.life));
        }
        if (pulse.life >= 1) {
          sceneRig.remove(pulse.mesh);
          pulse.mesh.geometry.dispose();
          if (Array.isArray(pulse.mesh.material)) {
            pulse.mesh.material.forEach((m) => m.dispose());
          } else {
            pulse.mesh.material.dispose();
          }
          pulses.splice(i, 1);
        }
      }
    }

    let animationFrameId: number;

    function animate() {
      animationFrameId = requestAnimationFrame(animate);
      updatePulses();

      fragments.forEach((fragment) => {
        const { root, rawMesh, structured, rotSpeed } = fragment;

        if (fragment.phase === "raw") {
          root.position.x += fragment.speed * 1.15;
          root.rotation.x += rotSpeed.x;
          root.rotation.y += rotSpeed.y;
          root.rotation.z += rotSpeed.z;

          const distToCenter = Math.abs(root.position.x);
          if (distToCenter < 1.5) {
            root.position.x += fragment.speed * (1.5 - distToCenter) * 1.0;
            root.rotation.x += rotSpeed.x * 3;
            root.rotation.y += rotSpeed.y * 3;
          }

          if (root.position.x > -0.08) {
            fragment.phase = "morph";
            fragment.morph = 0;
            spawnPulse();
            structured.visible = true;
            structured.scale.setScalar(0.12);
            structured.traverse((node) => {
              if (node instanceof THREE.Mesh && node.material instanceof THREE.Material) {
                node.material.opacity = 0;
              }
            });
          }
        } else if (fragment.phase === "morph") {
          fragment.morph = Math.min(1, fragment.morph + 0.04);
          const t = fragment.morph;

          root.position.x += fragment.speed * 0.8;
          root.rotation.x += rotSpeed.x * 0.4;
          root.rotation.y += rotSpeed.y * 0.4;
          root.rotation.z += rotSpeed.z * 0.25;

          const rawMat = rawMesh.material;
          if (rawMat instanceof THREE.Material) {
            rawMat.opacity = Math.max(0, 0.5 * (1 - t));
          }
          rawMesh.scale.setScalar(Math.max(0.02, 1 - t * 0.95));

          structured.visible = true;
          structured.scale.setScalar(0.12 + 0.88 * t);
          structured.rotation.x += 0.03;
          structured.rotation.y += 0.02;
          structured.traverse((node) => {
            if (node instanceof THREE.Mesh && node.material instanceof THREE.Material) {
              node.material.opacity = 0.95 * t;
            }
          });

          if (t >= 1) {
            fragment.phase = "structured";
            rawMesh.visible = false;
          }
        } else {
          root.position.x += fragment.speed * 1.25;
          root.rotation.x += rotSpeed.x * 0.25;
          root.rotation.y += rotSpeed.y * 0.3;
          root.rotation.z += rotSpeed.z * 0.2;

          structured.rotation.x += 0.01;
          structured.rotation.y += 0.015;

          if (root.position.x > 8.5) {
            resetFragment(fragment);
          }
        }
      });

      renderer.render(scene, camera);
    }

    const resizeObserver = new ResizeObserver(() => {
      updateViewport();
    });
    resizeObserver.observe(container);
    const themeObserver = new MutationObserver(syncThemeMaterials);
    themeObserver.observe(document.documentElement, {
      attributeFilter: ["class"],
      attributes: true,
    });

    updateViewport();
    animate();

    return () => {
      cancelAnimationFrame(animationFrameId);
      resizeObserver.disconnect();
      themeObserver.disconnect();
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
      renderer.dispose();
      
      // Clean up geometries and materials
      rawGeometries.forEach((g) => g.dispose());
      fragments.forEach((f) => {
        disposeGroup(f.root);
      });
      pulses.forEach((p) => {
        p.mesh.geometry.dispose();
        if (Array.isArray(p.mesh.material)) {
          p.mesh.material.forEach((m) => m.dispose());
        } else {
          p.mesh.material.dispose();
        }
      });
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="absolute inset-0 w-full h-full z-0 pointer-events-none"
    />
  );
}
