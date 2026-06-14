---
name: Cosmic AI Narrative
colors:
  surface: '#17111b'
  surface-dim: '#17111b'
  surface-bright: '#3e3642'
  surface-container-lowest: '#120c16'
  surface-container-low: '#201924'
  surface-container: '#241d28'
  surface-container-high: '#2f2732'
  surface-container-highest: '#3a323d'
  on-surface: '#ebdeee'
  on-surface-variant: '#cbc3d7'
  inverse-surface: '#ebdeee'
  inverse-on-surface: '#352e39'
  outline: '#958ea0'
  outline-variant: '#494454'
  surface-tint: '#d0bcff'
  primary: '#d0bcff'
  on-primary: '#3c0091'
  primary-container: '#a078ff'
  on-primary-container: '#340080'
  inverse-primary: '#6d3bd7'
  secondary: '#c3c0ff'
  on-secondary: '#1d00a5'
  secondary-container: '#3626ce'
  on-secondary-container: '#b3b1ff'
  tertiary: '#4cd7f6'
  on-tertiary: '#003640'
  tertiary-container: '#009eb9'
  on-tertiary-container: '#002f38'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e9ddff'
  primary-fixed-dim: '#d0bcff'
  on-primary-fixed: '#23005c'
  on-primary-fixed-variant: '#5516be'
  secondary-fixed: '#e2dfff'
  secondary-fixed-dim: '#c3c0ff'
  on-secondary-fixed: '#0f0069'
  on-secondary-fixed-variant: '#3323cc'
  tertiary-fixed: '#acedff'
  tertiary-fixed-dim: '#4cd7f6'
  on-tertiary-fixed: '#001f26'
  on-tertiary-fixed-variant: '#004e5c'
  background: '#17111b'
  on-background: '#ebdeee'
  surface-variant: '#3a323d'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 64px
    fontWeight: '400'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  display-sm:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '400'
    lineHeight: '1.2'
    letterSpacing: -0.03em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '400'
    lineHeight: '1.4'
    letterSpacing: -0.02em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: '0'
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.2'
    letterSpacing: 0.05em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: '1.2'
    letterSpacing: 0.1em
  display-lg-mobile:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '400'
    lineHeight: '1.1'
    letterSpacing: -0.04em
spacing:
  unit: 4px
  gutter: 24px
  margin-desktop: 64px
  margin-mobile: 24px
  container-max: 1440px
---

## Brand & Style
The design system is rooted in the concept of "The Void and the Singularity." It targets a high-end demographic of power users who value focus, intelligence, and a cinematic experience. The UI should evoke the feeling of a sophisticated interstellar terminal—quiet, vast, and technologically advanced.

The visual style is **Futuristic Glassmorphism** mixed with **Atmospheric Minimalism**. It avoids flat vector graphics in favor of volumetric light, ethereal smoke, and gravitational energy. Every interaction should feel like manipulating light within a vacuum. The emotional response is one of calm authority and infinite potential.

## Colors
The palette is dominated by the deepest void purple-black, creating an infinite backdrop for luminous accents. 
- **The Void (#050208):** Used for all primary surfaces to ensure depth and eliminate visual noise.
- **Neon Violet (#8b5cf6):** Reserved for primary interactive elements and the "Singularity" core glow.
- **Deep Indigo (#4f46e5):** Used for secondary accents and depth-building gradients.
- **Cyan Highlight (#06b6d4):** Applied sparingly for data visualizations or status indicators.
- **Hairline Border (#1f1a24):** A subtle structural boundary that keeps the UI from dissolving into the void.

## Typography
The typography system balances the "whisper-quiet" authority of **Inter** with the technical precision of **JetBrains Mono**. 
- **Inter (Weight 400):** Used for all narrative and primary interface text. Large display type must use aggressive negative tracking to feel modern and cinematic.
- **JetBrains Mono:** Used for technical metadata, labels, and system status. It should always appear in uppercase for small labels to enhance the "instrument panel" aesthetic.

## Layout & Spacing
The design system utilizes a **Fixed Grid** model for outputs to provide a sense of order, contrasted with a **Fluid, Contextual** model for inputs.
- **Desktop:** 12-column grid with 24px gutters. Content is centered with wide 64px margins to emphasize the surrounding "void."
- **Mobile:** 4-column grid with 16px gutters.
- **Rhythm:** Spacing follows a 4px base unit. Use generous padding (32px+) around the "Transformation Core" to allow the volumetric light effects to breathe without clipping.

## Elevation & Depth
Depth is created through **Atmospheric Layering** rather than traditional shadows.
- **The Core:** A central "Singularity" using multi-layered radial gradients (Violet to Indigo to Transparent) and a soft Gaussian blur (60px+) to create a volumetric bloom.
- **Glassmorphism:** Secondary panels use a 5% white opacity fill with a `backdrop-filter: blur(20px)`.
- **Particle Dust:** Subtle, low-opacity white dots are scattered behind the primary content layer to provide a sense of 3D space.
- **Borders:** Structural elements use 1px solid borders in `#1f1a24` with no shadows, keeping the aesthetic sharp and architectural.

## Shapes
The shape language is a deliberate study in contrast:
- **Structural (Cards/Panels):** 0px radius. Hard, precise edges that feel like monoliths.
- **Interactive (Buttons/Chips):** 9999px (Pill-shaped). These represent fluid energy and "touchable" elements.
- **Input Fields:** 24px radius. A transitional shape that sits between the rigid cards and the fluid buttons.

## Components
- **Top Navigation:** A hairline-bottom border terminal bar. Background is fully transparent; items are spaced widely using `label-md` typography.
- **Transformation Core:** A 300px circular region in the center of the screen. It features a pulsating violet glow and a smoky CSS-animated funnel effect that "draws in" nearby fragments.
- **Input Fragments:** Floating, glassy rectangles (`0px` radius) with `backdrop-filter`. They should appear slightly rotated or "orbiting" the core before being processed.
- **Output Cards:** Fixed-width, `0px` radius containers. They use the hairline border (`#1f1a24`) and are perfectly aligned to the grid.
- **Primary CTA:** A "Ghost Pill." Transparent background, white `body-md` text. On hover, it gains a `0 0 20px #8b5cf6` outer glow and the border transitions from neutral to neon violet.
- **Input Fields:** 24px rounded containers with a subtle `1px` border. On focus, the border glows cyan (`#06b6d4`).